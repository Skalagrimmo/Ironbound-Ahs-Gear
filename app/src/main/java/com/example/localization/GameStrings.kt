package com.example.localization

import com.example.model.*

object GameStrings {

    fun appTitle(lang: GameLanguage) = when (lang) {
        GameLanguage.UA -> "Ironbound: Попіл і Шестерні"
        GameLanguage.EN -> "Ironbound: Ash & Gear"
    }

    fun subtitle(lang: GameLanguage) = when (lang) {
        GameLanguage.UA -> "Ізометрична тактична екшн-РПГ • LittleKt + OpenGL ES SVDAG"
        GameLanguage.EN -> "Isometric Tactical Action RPG • LittleKt + OpenGL ES SVDAG"
    }

    fun heroName(hc: HeroClass, lang: GameLanguage) = when (hc) {
        HeroClass.KNIGHT -> if (lang == GameLanguage.UA) "Броньований Лицар" else "Armored Knight"
        HeroClass.RIFLEMAN -> if (lang == GameLanguage.UA) "Стрілець-Арбалетник" else "Rifleman-Archer"
        HeroClass.TECHNOMANCER -> if (lang == GameLanguage.UA) "Техномант-Маг" else "Technomancer Mage"
    }

    fun heroRole(hc: HeroClass, lang: GameLanguage) = when (hc) {
        HeroClass.KNIGHT -> if (lang == GameLanguage.UA) "Авангард ближнього бою • Щит і Важкий Меч" else "Melee Vanguard • Bulwark & Greatsword"
        HeroClass.RIFLEMAN -> if (lang == GameLanguage.UA) "Дальній критичний урон • Повторний Арбалет" else "Ranged Marksman • Repeating Crossbow"
        HeroClass.TECHNOMANCER -> if (lang == GameLanguage.UA) "Ефірна алхімія та блискавки • Посох Реактора" else "Aether & Pyro-Alchemy • Reactor Staff"
    }

    fun enemyName(e: EnemyArchetype, lang: GameLanguage) = when (e) {
        EnemyArchetype.RAT_SWARM -> if (lang == GameLanguage.UA) "Механічний Щур" else "Mechanical Rat"
        EnemyArchetype.SCRAPPER -> if (lang == GameLanguage.UA) "Фабричний Мародер" else "Factory Scrapper"
        EnemyArchetype.CULTIST -> if (lang == GameLanguage.UA) "Культист Іржі" else "Rust Cultist"
        EnemyArchetype.CONSTRUCT -> if (lang == GameLanguage.UA) "Готичний Конструкт" else "Gothic Construct"
    }

    fun zoneName(z: ZoneId, lang: GameLanguage) = when (z) {
        ZoneId.FACTORY_ZONE -> if (lang == GameLanguage.UA) "Фабрична Зона" else "Factory Zone"
        ZoneId.INTERIOR_LAB -> if (lang == GameLanguage.UA) "Внутрішній Цех / Лаб" else "Interior Factory / Lab"
        ZoneId.RESIDENTIAL_DISTRICT -> if (lang == GameLanguage.UA) "Житловий Квартал" else "Residential District"
        ZoneId.THE_RUINS -> if (lang == GameLanguage.UA) "Руїни Каналу" else "The Ruins (Canal)"
        ZoneId.DARK_CONFLUENCE -> if (lang == GameLanguage.UA) "Іржавий Ліс і Завод" else "Dark Nature Confluence"
    }

    fun zoneLore(z: ZoneId, lang: GameLanguage) = when (z) {
        ZoneId.FACTORY_ZONE -> if (lang == GameLanguage.UA) {
            "Готично-індустріальна площа під годинниковою вежею. Патрулі мародерів і зграї заводських щурів."
        } else {
            "Gothic-industrial sprawl beneath the clocktower. Patrolled by scrappers and mechanical rat packs."
        }
        ZoneId.INTERIOR_LAB -> if (lang == GameLanguage.UA) {
            "Безсонячний заводський комплекс із алхімічними чанами та важкими паровими конструктами."
        } else {
            "The Sunless Factory Complex filled with chemical vats and heavy steam-driven constructs."
        }
        ZoneId.RESIDENTIAL_DISTRICT -> if (lang == GameLanguage.UA) {
            "Бруковані ринки й цегляні провулки, захоплені культистами та бандами розбирачів."
        } else {
            "Cobblestone market stalls and brick alleys overrun by Rust Cultists and ambush squads."
        }
        ZoneId.THE_RUINS -> if (lang == GameLanguage.UA) {
            "Затоплений басейн каналу з обваленими арками. Снайперські позиції та охоронці реліктів."
        } else {
            "Flooded canal basin with collapsed arches. Guarded by ranged cultists and ancient sentinels."
        }
        ZoneId.DARK_CONFLUENCE -> if (lang == GameLanguage.UA) {
            "Де отруєні труби вростають у коріння Іржавого Лісу. Високорівневі конструкти й елітний лут."
        } else {
            "Where toxic pipes entwine Rustwood roots. High-tier constructs and Mythic Sunless drops."
        }
    }

    fun pressenName(p: PressenType, lang: GameLanguage) = when (p) {
        PressenType.POWER -> if (lang == GameLanguage.UA) "Пресен Сили" else "Power Pressen"
        PressenType.REGEN -> if (lang == GameLanguage.UA) "Пресен Відновлення" else "Regen Pressen"
        PressenType.COOLDOWN -> if (lang == GameLanguage.UA) "Пресен Заряду" else "Cooldown Pressen"
        PressenType.CHAIN -> if (lang == GameLanguage.UA) "Пресен Ланцюга" else "Chain Pressen"
    }

    fun pressenDesc(p: PressenType, lang: GameLanguage) = when (p) {
        PressenType.POWER -> if (lang == GameLanguage.UA) "+35% шкоди та пробиття броні" else "+35% strike damage & armor shred"
        PressenType.REGEN -> if (lang == GameLanguage.UA) "Лікує +14 ОЗ та +6 Енергії за удар" else "Restores +14 HP & +6 Energy per hit"
        PressenType.COOLDOWN -> if (lang == GameLanguage.UA) "Скорочує перезарядку вмінь на 1.2с" else "Reduces active skill cooldowns by 1.2s"
        PressenType.CHAIN -> if (lang == GameLanguage.UA) "Подвоює (×1.75) ефект попереднього слота" else "Amplifies (×1.75) the previous Pressen"
    }

    fun rarityName(r: ItemRarity, lang: GameLanguage) = when (r) {
        ItemRarity.IRON -> if (lang == GameLanguage.UA) "Залізний" else "Iron"
        ItemRarity.BRASS -> if (lang == GameLanguage.UA) "Латунний" else "Brass"
        ItemRarity.AETHER -> if (lang == GameLanguage.UA) "Ефірний" else "Aether"
        ItemRarity.RELIC -> if (lang == GameLanguage.UA) "Реліквія" else "Relic"
        ItemRarity.SUNLESS -> if (lang == GameLanguage.UA) "Безсонячний" else "Sunless"
    }

    fun slotName(s: EquipSlot, lang: GameLanguage) = when (s) {
        EquipSlot.WEAPON -> if (lang == GameLanguage.UA) "Зброя" else "Weapon"
        EquipSlot.OFFHAND -> if (lang == GameLanguage.UA) "Ліва рука" else "Off-Hand"
        EquipSlot.HELM -> if (lang == GameLanguage.UA) "Шолом" else "Helm"
        EquipSlot.ARMOR -> if (lang == GameLanguage.UA) "Броня" else "Armor"
        EquipSlot.BOOTS -> if (lang == GameLanguage.UA) "Чоботи" else "Boots"
        EquipSlot.RELIC -> if (lang == GameLanguage.UA) "Шестерня-Релікт" else "Cog-Relic"
    }

    fun itemName(titleKey: String, lang: GameLanguage): String = when (titleKey) {
        "starter_sword" -> if (lang == GameLanguage.UA) "Кований Бастард" else "Forged Bastard Sword"
        "starter_shield" -> if (lang == GameLanguage.UA) "Щит Охоронця" else "Vanguard Kite Shield"
        "starter_crossbow" -> if (lang == GameLanguage.UA) "Паровий Арбалет" else "Steam Repeating Bow"
        "starter_quiver" -> if (lang == GameLanguage.UA) "Стальний Сагайдак" else "Steel Bolt Case"
        "starter_staff" -> if (lang == GameLanguage.UA) "Ефірний Жезл" else "Aether Conduit Staff"
        "starter_core" -> if (lang == GameLanguage.UA) "Конденсатор Іскри" else "Spark Capacitor"
        "starter_armor" -> if (lang == GameLanguage.UA) "Клепаний Панцир" else "Riveted Plate"
        "loot_halberd" -> if (lang == GameLanguage.UA) "Цехова Алебарда" else "Foundry Greatsword"
        "loot_repeater" -> if (lang == GameLanguage.UA) "Точний Самостріл" else "Precision Repeater"
        "loot_tesla" -> if (lang == GameLanguage.UA) "Посох Котушки" else "Tesla Coil Staff"
        "loot_aegis" -> if (lang == GameLanguage.UA) "Бастіон Іржі" else "Rustproof Aegis"
        "loot_visage" -> if (lang == GameLanguage.UA) "Маска Ливарні" else "Foundry Visor"
        "loot_cuirass" -> if (lang == GameLanguage.UA) "Кіраса Котла" else "Boiler Cuirass"
        "loot_greaves" -> if (lang == GameLanguage.UA) "Чоботи Шестерень" else "Clockwork Greaves"
        "loot_chronogear" -> if (lang == GameLanguage.UA) "Ядро Хронометра" else "Chronometer Core"
        else -> titleKey
    }

    fun skillName(id: String, lang: GameLanguage): String = when (id) {
        "knight_cleave" -> if (lang == GameLanguage.UA) "Сталевий Розсік" else "Iron Cleave"
        "knight_aegis" -> if (lang == GameLanguage.UA) "Бастіон Волі" else "Bastion Slam"
        "knight_charge" -> if (lang == GameLanguage.UA) "Таранний Ривок" else "Siege Dash"
        "rifle_volley" -> if (lang == GameLanguage.UA) "Залп Болтів" else "Bolt Volley"
        "rifle_trap" -> if (lang == GameLanguage.UA) "Шрапнельна Міна" else "Shrapnel Blast"
        "rifle_roll" -> if (lang == GameLanguage.UA) "Тактичний Відскок" else "Evasive Roll"
        "mage_arc" -> if (lang == GameLanguage.UA) "Ланцюг Блискавки" else "Arc Discharge"
        "mage_nova" -> if (lang == GameLanguage.UA) "Піро-Нова" else "Pyro-Alchemy Nova"
        "mage_blink" -> if (lang == GameLanguage.UA) "Ефірний Стрибок" else "Aether Blink"
        else -> id
    }

    fun talentBranchName(hc: HeroClass, branchIdx: Int, lang: GameLanguage): String = when (hc) {
        HeroClass.KNIGHT -> if (branchIdx == 0) {
            if (lang == GameLanguage.UA) "Незламний Бастіон" else "Bulwark Vanguard"
        } else {
            if (lang == GameLanguage.UA) "Осадний Нищівник" else "Siegebreaker"
        }
        HeroClass.RIFLEMAN -> if (branchIdx == 0) {
            if (lang == GameLanguage.UA) "Годинниковий Снайпер" else "Clockwork Marksman"
        } else {
            if (lang == GameLanguage.UA) "Вибухова Боєукладка" else "Volatile Munitions"
        }
        HeroClass.TECHNOMANCER -> if (branchIdx == 0) {
            if (lang == GameLanguage.UA) "Провідник Ефіру" else "Aether Conduit"
        } else {
            if (lang == GameLanguage.UA) "Піро-Алхімія" else "Pyro-Alchemy"
        }
    }

    fun talentName(id: String, lang: GameLanguage): String = when (id) {
        "k_b1" -> if (lang == GameLanguage.UA) "Загартовані Пластини" else "Tempered Plating"
        "k_b2" -> if (lang == GameLanguage.UA) "Удар Бастіону" else "Bastion Slam"
        "k_b3" -> if (lang == GameLanguage.UA) "Жива Сталь" else "Living Steel"
        "k_s1" -> if (lang == GameLanguage.UA) "Гостре Лезо" else "Honed Greatsword"
        "k_s2" -> if (lang == GameLanguage.UA) "Таранний Ривок" else "Siege Dash"
        "k_s3" -> if (lang == GameLanguage.UA) "Лють Ката" else "Executioner's Edge"

        "r_m1" -> if (lang == GameLanguage.UA) "Оптичний Приціл" else "Clockwork Optics"
        "r_m2" -> if (lang == GameLanguage.UA) "Залп Болтів" else "Bolt Volley"
        "r_m3" -> if (lang == GameLanguage.UA) "Смертельна Точність" else "Lethal Precision"
        "r_v1" -> if (lang == GameLanguage.UA) "Швидкий Звод" else "Hair-Trigger Spring"
        "r_v2" -> if (lang == GameLanguage.UA) "Тактичний Відскок" else "Evasive Roll"
        "r_v3" -> if (lang == GameLanguage.UA) "Шрапнельна Детонація" else "Shrapnel Detonation"

        "m_a1" -> if (lang == GameLanguage.UA) "Висока Напруга" else "High Voltage"
        "m_a2" -> if (lang == GameLanguage.UA) "Ланцюг Блискавки" else "Arc Discharge"
        "m_a3" -> if (lang == GameLanguage.UA) "Реакторне Перевантаження" else "Reactor Overclock"
        "m_p1" -> if (lang == GameLanguage.UA) "Алхімічний Запас" else "Alchemical Reserve"
        "m_p2" -> if (lang == GameLanguage.UA) "Ефірний Стрибок" else "Aether Blink"
        "m_p3" -> if (lang == GameLanguage.UA) "Піро-Алхімічна Нова" else "Pyro-Alchemy Nova"
        else -> id
    }

    fun talentDesc(id: String, lang: GameLanguage): String = when (id) {
        "k_b1" -> if (lang == GameLanguage.UA) "+10 Броні та +35 ОЗ за ранг" else "+10 Armor and +35 HP per rank"
        "k_b2" -> if (lang == GameLanguage.UA) "Відкриває вміння Бастіон Волі (удар по площі + лікування)" else "Unlocks Bastion Slam (AoE stun & sustain)"
        "k_b3" -> if (lang == GameLanguage.UA) "+4% вампіризму за кожен ранг" else "+4% Lifesteal per rank"
        "k_s1" -> if (lang == GameLanguage.UA) "+8 Урону та +4% Криту за ранг" else "+8 Attack Damage & +4% Crit per rank"
        "k_s2" -> if (lang == GameLanguage.UA) "Відкриває ривок Таранний Ривок" else "Unlocks Siege Dash mobility strike"
        "k_s3" -> if (lang == GameLanguage.UA) "+12% Шкоди комбо та +5% Прискорення КД" else "+12% Damage & +5% Cooldown Haste"

        "r_m1" -> if (lang == GameLanguage.UA) "+6% Критичного шансу за ранг" else "+6% Crit Chance per rank"
        "r_m2" -> if (lang == GameLanguage.UA) "Відкриває вміння Залп Болтів по сектору" else "Unlocks multi-target Bolt Volley"
        "r_m3" -> if (lang == GameLanguage.UA) "+10 Урону та +0.4м дальності стрільби" else "+10 Damage & +0.4m attack range"
        "r_v1" -> if (lang == GameLanguage.UA) "+8% Прискорення перезарядки за ранг" else "+8% Cooldown Haste per rank"
        "r_v2" -> if (lang == GameLanguage.UA) "Відкриває маневр Тактичний Відскок" else "Unlocks Evasive Roll dash"
        "r_v3" -> if (lang == GameLanguage.UA) "+25 ОЗ та +4% Вампіризму за ранг" else "+25 HP & +4% Lifesteal per rank"

        "m_a1" -> if (lang == GameLanguage.UA) "+9 Магічного урону за ранг" else "+9 Spell Damage per rank"
        "m_a2" -> if (lang == GameLanguage.UA) "Відкриває дуговий удар Ланцюг Блискавки" else "Unlocks Arc Discharge chain lightning"
        "m_a3" -> if (lang == GameLanguage.UA) "+10% Прискорення КД та +5% Криту" else "+10% Cooldown Haste & +5% Crit"
        "m_p1" -> if (lang == GameLanguage.UA) "+35 ОЗ та +6 Броні за ранг" else "+35 HP & +6 Armor per rank"
        "m_p2" -> if (lang == GameLanguage.UA) "Відкриває телепорт Ефірний Стрибок" else "Unlocks Aether Blink teleport"
        "m_p3" -> if (lang == GameLanguage.UA) "+12 Урону та +4% Вампіризму" else "+12 Damage & +4% Lifesteal"
        else -> ""
    }

    // UI labels
    fun label(key: String, lang: GameLanguage): String = when (key) {
        "choose_hero" -> if (lang == GameLanguage.UA) "Оберіть Героя" else "Choose Your Hero"
        "enter_world" -> if (lang == GameLanguage.UA) "На Мапу Світу" else "World Map"
        "deploy_zone" -> if (lang == GameLanguage.UA) "Висадитись у Зону" else "Deploy to Zone"
        "tactical_pause" -> if (lang == GameLanguage.UA) "ТАКТИЧНА ПАУЗА" else "TACTICAL PAUSE"
        "live_combat" -> if (lang == GameLanguage.UA) "РЕАЛЬНИЙ ЧАС" else "REAL-TIME"
        "pause_hint" -> if (lang == GameLanguage.UA) "Торкніться поля чи ворога щоб спланувати чергу команд, або натисніть ВІДНОВИТИ" else "Tap ground or enemy to queue orders, then press RESUME"
        "resume" -> if (lang == GameLanguage.UA) "ВІДНОВИТИ" else "RESUME"
        "pause" -> if (lang == GameLanguage.UA) "ПАУЗА" else "PAUSE"
        "clear_queue" -> if (lang == GameLanguage.UA) "Очистити чергу" else "Clear Queue"
        "queued_orders" -> if (lang == GameLanguage.UA) "У черзі" else "Queued"
        "combo_lab" -> if (lang == GameLanguage.UA) "Лабораторія Комбо" else "Combo Lab"
        "inventory" -> if (lang == GameLanguage.UA) "Інвентар" else "Inventory"
        "talents" -> if (lang == GameLanguage.UA) "Дерева Талантів" else "Talent Trees"
        "map" -> if (lang == GameLanguage.UA) "Мапа" else "Map"
        "settings" -> if (lang == GameLanguage.UA) "Налаштування" else "Settings"
        "equip" -> if (lang == GameLanguage.UA) "Одягти" else "Equip"
        "equipped" -> if (lang == GameLanguage.UA) "Одягнено" else "Equipped"
        "salvage" -> if (lang == GameLanguage.UA) "Розібрати" else "Salvage"
        "forge_item" -> if (lang == GameLanguage.UA) "Викувати Лут (40 Брухту)" else "Forge Gear (40 Scrap)"
        "respec" -> if (lang == GameLanguage.UA) "Скинути Таланти" else "Respec Talents"
        "upgrade" -> if (lang == GameLanguage.UA) "Вивчити" else "Learn"
        "points_avail" -> if (lang == GameLanguage.UA) "Доступно очок" else "Available Points"
        "scrap" -> if (lang == GameLanguage.UA) "Брухт" else "Scrap"
        "level" -> if (lang == GameLanguage.UA) "Рів." else "Lv."
        "hp" -> if (lang == GameLanguage.UA) "Здоров'я" else "Health"
        "dmg" -> if (lang == GameLanguage.UA) "Шкода" else "Damage"
        "armor" -> if (lang == GameLanguage.UA) "Броня" else "Armor"
        "crit" -> if (lang == GameLanguage.UA) "Крит" else "Crit"
        "lifesteal" -> if (lang == GameLanguage.UA) "Вампіризм" else "Lifesteal"
        "cdr" -> if (lang == GameLanguage.UA) "Приск. КД" else "CDR"
        "language" -> if (lang == GameLanguage.UA) "Мова інтерфейсу" else "Interface Language"
        "svdag_preset" -> if (lang == GameLanguage.UA) "SVDAG Якість (Exynos 7870 / Mali-T830)" else "SVDAG Quality (Exynos 7870 / Mali-T830)"
        "telemetry_toggle" -> if (lang == GameLanguage.UA) "Показувати телеметрію FPS / SVDAG" else "Show FPS / SVDAG Telemetry"
        "haptics_toggle" -> if (lang == GameLanguage.UA) "Тактильна віддача" else "Haptic Feedback"
        "zone_cleared" -> if (lang == GameLanguage.UA) "ЗОНУ ЗАЧИЩЕНО!" else "ZONE CLEARED!"
        "hero_fallen" -> if (lang == GameLanguage.UA) "ГЕРОЙ ПОЛІГ" else "HERO FALLEN"
        "retry_zone" -> if (lang == GameLanguage.UA) "Повторити Бій" else "Retry Zone"
        "return_map" -> if (lang == GameLanguage.UA) "На Мапу Світу" else "Return to Map"
        "next_wave" -> if (lang == GameLanguage.UA) "Закрити" else "Close"
        "combo_hint" -> if (lang == GameLanguage.UA) "Торкніться слота 1–6, щоб обрати Пресен. Ланцюг підсилює попередній слот!" else "Tap a slot 1–6 to assign a Pressen. Chain amplifies the preceding slot!"
        "combo_mult" -> if (lang == GameLanguage.UA) "Множник Комбо" else "Combo Mult"
        "combo_heal" -> if (lang == GameLanguage.UA) "Лікування/ланцюг" else "Heal/Chain"
        "combo_cdr" -> if (lang == GameLanguage.UA) "КД/ланцюг" else "CDR/Chain"
        "potion" -> if (lang == GameLanguage.UA) "Зілля" else "Potion"
        "attack" -> if (lang == GameLanguage.UA) "Комбо" else "Combo"
        "switch_hero" -> if (lang == GameLanguage.UA) "Змінити Клас" else "Switch Class"
        "empty_bag" -> if (lang == GameLanguage.UA) "Рюкзак порожній. Перемагайте ворогів або куйте спорядження!" else "Backpack empty. Defeat enemies or forge gear!"
        "unlock_req" -> if (lang == GameLanguage.UA) "Пройдіть попередню зону" else "Clear previous zone"
        "rec_lvl" -> if (lang == GameLanguage.UA) "Рек. рів." else "Rec. Lv."
        "clears" -> if (lang == GameLanguage.UA) "Зачисток" else "Clears"
        else -> key
    }
}
