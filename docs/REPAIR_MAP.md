# Мапа відновлення та технічного аудиту — Ironbound: Ash & Gear

Документ описує критичні межі модулів, точки діагностики (`// REPAIR_HOOK:`), поточну багатопоточну модель та перелік підтверджених проблем і ризиків у кодовій базі **Ironbound: Ash & Gear** (`com.example` / `com.aistudio.ironboundrpg.vxqkzm`).

---

## 1. Огляд архітектури та межі підсистем

Проєкт поєднує п’ять чітко розмежованих шарів:

1. **Android Lifecycle & Host (`MainActivity.kt`)**:
   - Ініціалізує `LittleKtApp` на `GLSurfaceView` і розміщує поверх нього `ComposeView` із єдиним `IronboundTheme`.
   - Керує `renderMode` (`RENDERMODE_WHEN_DIRTY` у меню для економії батареї/GPU на Exynos 7870 та `RENDERMODE_CONTINUOUSLY` на арені).
2. **UI та HUD на Jetpack Compose (`ui/screens/*`, `ui/modals/*`, `localization/GameStrings.kt`)**:
   - Екрани `HeroSelectScreen`, `WorldMapScreen`, бойовий оверлей `ArenaHudOverlay` та модальні діалоги `RpgModalHost` (Лабораторія Комбо, Інвентар, Дерева Талантів, Налаштування).
   - Повна підтримка перемикання мови `UA / EN` на льоту.
3. **Графічний конвеєр LittleKt + OpenGL ES SVDAG (`engine/render/*`, `engine/svdag/*`, `engine/assets/*`)**:
   - `SpriteSheetLoader` декодує спрайт-листи з `app/src/main/assets/` у прев’ю-бітмапи для Compose та у текстурний атлас `1024x1024 ARGB_8888`.
   - `SvdagBuilder` будує 5-рівневий Sparse Voxel DAG (SVDAG) із дедуплікацією вузлів для 5 біомів і пакує його в RGBA8 текстуру.
   - `SvdagGlRenderer` виконує реймаршинг у масштабований офскрін-FBO (`0.50x / 0.75x / 1.00x` під Mali-T830 MP1) і бліт на екран.
   - `IronboundLittleKtScene` поверх SVDAG-фону малює відсортовані за глибиною спрайти героїв/ворогів, чергу наказів RTWP та VFX через LittleKt `SpriteBatch`.
4. **Бойова симуляція (`engine/sim/CombatSimulation.kt`)**:
   - Чистий Kotlin-рушій реального часу з тактичною паузою (RTWP), комбо-ланцюгами у стилі *Remember Me* (Pressen: Power / Regen / Cooldown / Chain), вміннями, ШІ ворогів і телеграфами атак.
5. **Стан та збереження даних (`viewmodel/GameViewModel.kt`, `data/db/*`, `data/repo/GameRepository.kt`)**:
   - Room SQLite (версія 1) зберігає профілі 3 класів героїв, інвентар, комбо-слоти, ранги талантів, прогрес 5 зон і налаштування.

---

## 2. Життєвий цикл застосунку

| Компонент | Точка / Метод | За що відповідає | Маркер |
| :--- | :--- | :--- | :--- |
| `MainActivity` | `onCreate` | Edge-to-edge, запуск `createLittleKtApp`, пошук `GLSurfaceView`, прикріплення `ComposeView`, перемикання `renderMode` | `MainActivity.kt` |
| `MainActivity` | `onResume` / `onPause` / `onDestroy` | Прокидання `(littleKtApp?.context as? AndroidContext)` у паузу/резюм та коректне закриття GL-контексту | `MainActivity.kt` |
| `MainActivity` | `IronboundRootContent` | Гейт `state.initError` (картка діагностики українською + повтор), спінер `!state.isReady`, `BackHandler` для модалок/екранів | `MainActivity.kt` |

---

## 3. Ігрова симуляція та міжпоточна синхронізація

### Межа потоків (Compose Main Thread / IO <-> LittleKt `GLThread`)
- **Проблема до виправлення**: `ArenaHudOverlay` (Main thread) і `GameViewModel` (Main / IO) напряму викликали `sim.startZone`, `sim.handleWorldTap`, `sim.togglePause`, `sim.triggerBasicComboAttack`, `sim.triggerSkill`, `sim.triggerPotion`, `sim.clearQueue`, `sim.updateHeroBuild` і мутували `ArrayList`-и (`enemies`, `queuedOrders`, `pulses`), тоді як `IronboundLittleKtScene.onRender` на `GLThread` ітерував ті самі колекції.
- **Рішення**:
  - Усі зовнішні мутуючі методи `CombatSimulation` переведені в потокобезпечну неблокуючу чергу `ConcurrentLinkedQueue<SimCommand>`.
  - `step(rawDt)` викликає `drainPendingCommands()` **до** перевірки `if (isPaused || heroHp <= 0)`, тому накази під час Тактичної Паузи (`MoveTo`, `AttackTarget`, `CastSkill`, `UsePotion`, `TogglePause`, `ClearQueue`) гарантовано приймаються без розморожування часу.
  - Поля `heroX`, `heroY`, `joystickX`, `joystickY`, `isPaused` (`private set`) позначені `@Volatile` для безпечного читання з Compose (`screenToWorld` / `worldToComposeScreen` / `openModal`).

### Карта методів `CombatSimulation`
- `startZone()` -> ставить у чергу `SimCommand.StartZone`; `applyStartZone()` скидає HP/Енергію/КД/таймери, очищає списки та спавнить ворогів обраної зони через `spawnZoneEnemies()`.
- `step(rawDt)` -> обмежує `dt` до `[0f, 0.05f]`, дренує `commandQueue`, при паузі/смерті повільно рухає лише `heroAnimTime`; інакше оновлює регенерацію енергії, перезарядки, послідовне виконання `queuedOrders`, рух джойстика/кліку, ШІ ворогів (з фазою `isWindingUp` для читабельності в паузі), проєктайли, VFX та виклик `onZoneCleared()`.
- `applyWorldTap()` -> під час паузи додає `QueuedOrder.MoveTo` або `QueuedOrder.AttackTarget`, у реальному часі одразу рухає або атакує ціль.
- `executeComboStrikeOn()` -> обчислює активний слот Пресену та підсилення `CHAIN` від попереднього слота, списує КД/додає HP або шкоду, інкрементує `comboStep`.
- `snapshot()` -> формує незмінний `CombatHudSnapshot` на `GLThread` після `step()` і публікує його в `_hud` через `GameViewModel.pushGlHudSnapshot()`.

---

## 4. Рендеринг (LittleKt + OpenGL ES SVDAG + Атлас спрайтів)

| Компонент | Функція | Зона відповідальності |
| :--- | :--- | :--- |
| `SpriteSheetLoader` | `loadUiPreviews` | Декодує `assets/sprites_characters.jpg` і `assets/world_biomes.jpg` (1408×768) в RGB_565 для портретів, мапи світу та мініатюр зон |
| `SpriteSheetLoader` | `loadCombatAtlas` | Вирізає 46 кадрів героїв і ворогів з альфа-прозорістю (BFS-заливка сланцевого фону + згладжування країв) у текстурний атлас `1024x1024 ARGB_8888` |
| `CombatAtlasAssets` | `getHeroUv`, `getEnemyUv` | Повертає UV-координати кадру з захистом від від’ємного `animTime` (`.coerceAtLeast(0)`) |
| `SvdagBuilder` | `buildForZone` | Генерує 32×16×32 воксельне середовище для кожної з 5 зон, дедуплікує піддерева у 5-рівневому DAG та кодує таблицю вузлів у RGBA8 байтовий буфер |
| `SvdagGlRenderer` | `initIfNeeded`, `uploadChunk`, `ensureFbo`, `renderBackdrop`, `dispose` | Компілює GLSL-шейдери з перевіркою `GL_COMPILE_STATUS`/`GL_LINK_STATUS`, завантажує DAG-текстуру, створює масштабований FBO (`0.50x` Exynos 7870 / `0.75x` Balanced / `1.00x` Native) з перевіркою `GL_FRAMEBUFFER_COMPLETE`, рендерить SVDAG і робить фінальний бліт |
| `IronboundLittleKtScene` | `onRender`, `ensureArenaGlResources`, `screenToWorld`, `worldToComposeScreen`, `onDispose` | У меню чистить екран одним кольором; на арені синхронізує SVDAG-чанк, запускає `sim.step()`, рендерить фон, сортує спрайти за ізометричною глибиною (`x + y`), лінії черги RTWP, кола телеграфів та очищає ресурси при виході |

**Критичне правило узгодженості проекції**: ортонормальний базис (`rx`, `rz`, `ux`, `uy`, `uz`, `zoom = 11.5f`) у `IronboundLittleKtScene` повинен точно збігатися з базисом камери в `SvdagGlRenderer.SVDAG_FRAGMENT_SHADER` (`right`, `rayDir`, `up`, `zoom = 11.5`).

---

## 5. Шар даних і стан (`GameViewModel`, `GameRepository`, `Room`)

- **`AppDatabase` / `GameDaos.kt`**: 6 сутностей (`HeroProfileEntity`, `InventoryItemEntity`, `ComboSlotEntity`, `TalentRankEntity`, `ZoneProgressEntity`, `AppSettingsEntity`). Схема версії 1 незмінна.
- **`GameRepository.kt`**:
  - `ensureSeeded()` виконується всередині `db.withTransaction { ... }` і перевіряє кожну частину незалежно (налаштування, кожен із 3 класів героїв, наявність стартового інвентарю `countItemsForHero`, комбо-слотів `countSlotsForHero` і `insertZonesIgnore` для 5 зон). Це захищає від часткового засівання при перериванні першого запуску та ніколи не затирає існуючий прогрес.
  - `equipItem()` та `salvageItem()` виконуються у `db.withTransaction`, гарантуючи єдиний екіпірований предмет на слот та атомарне нарахування брухту.
- **`GameViewModel.kt`**:
  - `retryInitialization()` запускає засівання БД та `loadUiPreviews()` у `Dispatchers.IO` всередині `try/catch`, переводячи будь-який виняток у `GameUiState.initError` з детальним повідомленням українською замість вічного індикатора завантаження.
  - `deployToZone()` додатково ловить винятки `loadCombatAtlas()` / `SvdagBuilder.buildForZone()`, запобігаючи мовчазному падінню корутини.
  - `uiState` поєднує 11 потоків (`_initStatus`, екрани, модалки, налаштування, героїв, інвентар, комбо, таланти, зони, обрану зону, HUD) і передає перераховані характеристики героя у `CombatSimulation`.

---

## 6. Каталог виявлених проблем і ризиків

| № | Файл і функція | Серйозність | Статус | Тригер | Очікувана поведінка | Мінімальне виправлення | Регресійний тест |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `app/src/main/assets/` (`SpriteSheetLoader.loadUiPreviews`, `loadCombatAtlas`) | **Критична** | **Підтверджено і виправлено** | Запуск гри або вхід на арену при відсутніх файлах `sprites_characters.jpg` та `world_biomes.jpg` | Обидва JPG-листи `1408x768` доступні через `AssetManager`, координати нарізки валідні | Перенесено та верифіковано вихідні файли `11_2f215d46.jpg` -> `sprites_characters.jpg` і `22_7f2bd3e5.jpg` -> `world_biomes.jpg` | `assetsContainValidSpriteAndBiomeSheetsAndLoadPreviewsAndAtlas` |
| 2 | `GameViewModel.init` / `MainActivity.IronboundRootContent` | **Висока** | **Підтверджено і виправлено** | Будь-який виняток при завантаженні асетів або ініціалізації Room | Відображення зрозумілої картки помилки українською та можливість повторити запуск | Додано `_initStatus(ready, error)`, `try/catch`, `retryInitialization()` і `init_error_card` + `retry_init_button` | `uiPreviewsAndInitRecoveryStatePropagatesCleanly` |
| 3 | `CombatSimulation` (`step`, `startZone`, `handleWorldTap`, `togglePause`, `trigger*`) та `IronboundLittleKtScene.onRender` | **Висока** | **Підтверджено і виправлено** | Одночасні натискання Compose HUD / ViewModel на Main-потоці та рендер/симуляція на `GLThread` | Відсутність `ConcurrentModificationException` і втрачених команд | Впроваджено `ConcurrentLinkedQueue<SimCommand>` + `drainPendingCommands()` на початку `step()`, `@Volatile` для координат і джойстика, `requestPause()` | `combatSimulationCommandQueueIsThreadSafeUnderConcurrentWritesAndSteps` |
| 4 | `GameRepository.ensureSeeded`, `equipItem`, `salvageItem` | **Середня** | **Підтверджено і виправлено** | Переривання першого запуску між вставкою героя та предметів/зон або швидке екіпірування/розбір | Атомарне відновлення відсутніх стартових записів без затирання `clearedCount` | Обгорнуто у `db.withTransaction`, додано перевірки по кожному герою та DAO-лічильники | `roomEnsureSeededRepairsPartialDatabaseWithoutOverwritingProgress`, `roomEquipAndSalvageAreAtomicAndCreditScrap` |
| 5 | `SvdagGlRenderer.compileShader`, `createProgram`, `ensureFbo`, `renderBackdrop` | **Середня** | **Ризик GPU/драйвера (захищено)** | Відмова компіляції GLSL ES або неповний FBO на мобільному GPU | Діагностика в `lastGlError` + Logcat та безпечне очищення буфера без чорних артефактів чи крашу | Перевірка `GL_COMPILE_STATUS`, `GL_LINK_STATUS`, `GL_FRAMEBUFFER_COMPLETE` і фолбек-очищення | Перевірено компіляцією та інспекцією GL-коду (GLSurfaceView недоступний у JVM Robolectric) |
| 6 | `CombatAtlasAssets.getHeroUv`, `getEnemyUv` | **Низька** | **Потенційний ризик і виправлено** | Від’ємний `animTime` дає від’ємний остаток `% list.size` та `IndexOutOfBoundsException` | Завжди валідний індекс кадру `[0, list.size - 1]` | Додано `.coerceAtLeast(0)` перед `% list.size` | `assetsContainValidSpriteAndBiomeSheetsAndLoadPreviewsAndAtlas` |
| 7 | `IronboundLittleKtScene.onDispose` | **Низька** | **Потенційний ризик і виправлено** | Перестворення Activity залишає застаріле посилання в `LoadedGameAssetsHolder.scene` | Обнулення посилання тільки якщо воно вказує на поточний екземпляр сцени | Додано `if (LoadedGameAssetsHolder.scene === this) LoadedGameAssetsHolder.scene = null` | Перевірено інспекцією життєвого циклу |
| 8 | `GameViewModel.onKillReward` (`awardCombatProgress` / `forgeRandomGear`) | **Середня** | **Потенційний ризик (спостерігається)** | Два паралельні `Dispatchers.IO` виклики при швидких одночасних убивствах можуть читати застарілий `scrapCurrency` | Потенційна втрата частини XP/брухту при гонці кількох IO-корутин; мінімальний масштаб — залишається під наглядом, рекомендовано у майбутньому серіалізувати оновлення валют у Room транзакціях | Зафіксовано як низькоймовірний ризик на етапі прототипу | — |
| 9 | `res/drawable/ic_launcher_foreground.xml`, `res/mipmap-*`, `res/font/*.ttf` (`Type.kt`) | **Критична (збірка)** | **Підтверджено і виправлено** | Відсутній ресурс `@drawable/app_icon_foreground_1794838620307`, відсутні `mipmap-*` PNG та локальні шрифти `Cormorant Garamond`, `Inter`, `JetBrains Mono` у `res/font/` | Успішна лінковки ресурсів AAPT2 та кастомна адаптивна іконка й типографіка | Згенеровано foreground іконку `ironbound_icon_1795083913082.jpg`, згенеровано `mipmap-*` PNG (включно з круглими), встановлено шрифти через `font-util` у `app/src/main/res/font/` | Повна збірка `compile_applet` та `appNameMatchesIronboundRpg` |

---

## 7. Індекс маркерів `// REPAIR_HOOK:` у коді

Усі ключові точки входу позначені короткими коментарями `// REPAIR_HOOK:`:
- `app/src/main/java/com/example/MainActivity.kt` — хост LittleKt+Compose, pause/resume/destroy, стартова панель помилки `initError`.
- `app/src/main/java/com/example/viewmodel/GameViewModel.kt` — стартова ініціалізація з `try/catch` та `retryInitialization`, `combine` потік `uiState`, запуск зони `deployToZone`.
- `app/src/main/java/com/example/data/repo/GameRepository.kt` — ідемпотентне транзакційне засівання `ensureSeeded`, атомарне `equipItem` / `salvageItem`.
- `app/src/main/java/com/example/engine/assets/SpriteSheetLoader.kt` — завантаження і нарізка `sprites_characters.jpg` та `world_biomes.jpg`, безпечна індексація кадрів UV.
- `app/src/main/java/com/example/engine/svdag/SvdagGlRenderer.kt` — FBO рендеринг SVDAG, перевірки GLSL-шейдерів та повноти FBO.
- `app/src/main/java/com/example/engine/render/IronboundLittleKtScene.kt` — головний цикл `onRender`, `ensureArenaGlResources`, проєкція `screenToWorld`, `onDispose`.
- `app/src/main/java/com/example/engine/sim/CombatSimulation.kt` — `ConcurrentLinkedQueue<SimCommand>`, `startZone`, обробка вводу та RTWP, `step`, виконання черги паузи.
- `app/src/main/java/com/example/ui/screens/ArenaHudOverlay.kt` — передача дотиків та віртуального джойстика в `CombatSimulation`.
