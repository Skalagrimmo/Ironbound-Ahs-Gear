package com.example.engine.render

import com.example.engine.assets.AtlasUv
import com.example.engine.assets.CombatAtlasAssets
import com.example.engine.svdag.SvdagGlRenderer
import com.example.model.QueuedOrder
import com.example.viewmodel.AppScreen
import com.example.viewmodel.GameViewModel
import com.lehaine.littlekt.Context
import com.lehaine.littlekt.ContextListener
import com.lehaine.littlekt.file.createByteBuffer
import com.lehaine.littlekt.graphics.Color
import com.lehaine.littlekt.graphics.OrthographicCamera
import com.lehaine.littlekt.graphics.Pixmap
import com.lehaine.littlekt.graphics.Texture
import com.lehaine.littlekt.graphics.g2d.SpriteBatch
import com.lehaine.littlekt.graphics.g2d.TextureSlice
import com.lehaine.littlekt.graphics.gl.ClearBufferMask
import com.lehaine.littlekt.graphics.gl.PixmapTextureData
import com.lehaine.littlekt.graphics.gl.TexMagFilter
import com.lehaine.littlekt.graphics.gl.TexMinFilter
import com.lehaine.littlekt.graphics.toFloatBits
import com.lehaine.littlekt.math.geom.Angle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.DurationUnit

/**
 * LittleKt ContextListener hosting the OpenGL ES 3.0 / 2.0 SVDAG environment raymarcher
 * and LittleKt SpriteBatch depth-sorted isometric character/VFX renderer.
 *
 * Keeps Context.start() and non-arena frames near-zero cost so Android's initial
 * SurfaceSyncGroup / GLSurfaceView frame swap completes immediately without timeout.
 */
class IronboundLittleKtScene(
    context: Context,
    private val viewModel: GameViewModel
) : ContextListener(context) {

    private val gameContext: Context = context
    private var lastViewportDiagnostic: String? = null
    private var batch: SpriteBatch? = null
    private var camera: OrthographicCamera? = null
    private var atlasTexture: Texture? = null
    private var whiteTex: Texture? = null
    private var whitePixel: TextureSlice? = null
    private val svdagRenderer = SvdagGlRenderer()

    private var activeZoneLoaded = false
    private var lastLoadedZoneIndex = -1
    private var elapsedTime = 0f
    private var fpsAccum = 0f
    private var fpsFrames = 0
    private var currentFps = 60
    private var hudSyncTimer = 0f


    override suspend fun Context.start() {
        com.example.viewmodel.LoadedGameAssetsHolder.scene = this@IronboundLittleKtScene

        viewModel.onZoneChangedForGl = { _ ->
            lastLoadedZoneIndex = -1
            activeZoneLoaded = false
        }

        onResize { w, h ->
            val cam = camera ?: OrthographicCamera(w.toFloat().coerceAtLeast(1f), h.toFloat().coerceAtLeast(1f)).also {
                camera = it
            }
            cam.ortho(w.toFloat().coerceAtLeast(1f), h.toFloat().coerceAtLeast(1f))
            cam.position.set(w * 0.5f, h * 0.5f, 0f)
            cam.update()
        }

        // REPAIR_HOOK: Головний цикл LittleKt onRender (рендер SVDAG фону + SpriteBatch спрайтів і VFX)
        onRender { dt ->
            val state = viewModel.uiState.value
            val combatAtlas = viewModel.combatAtlas

            if (!state.isReady || state.screen != AppScreen.ARENA || combatAtlas == null) {
                gl.clearColor(0.05f, 0.06f, 0.08f, 1f)
                gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
                return@onRender
            }

            ensureArenaGlResources(this, combatAtlas)
            val activeBatch = batch ?: return@onRender
            val activeCam = camera ?: return@onRender
            val activeAtlas = atlasTexture ?: return@onRender
            val activeWhite = whitePixel ?: return@onRender

            val dtSec = dt.toDouble(DurationUnit.SECONDS).toFloat().coerceIn(0.001f, 0.05f)
            elapsedTime += dtSec
            fpsAccum += dtSec
            fpsFrames++
            if (fpsAccum >= 0.5f) {
                currentFps = (fpsFrames / fpsAccum).toInt().coerceIn(1, 120)
                fpsAccum = 0f
                fpsFrames = 0
            }

            val sim = viewModel.simulation
            if (!activeZoneLoaded || lastLoadedZoneIndex != sim.activeZone.index) {
                viewModel.svdagChunks[sim.activeZone]?.let { chunk ->
                    svdagRenderer.uploadChunk(chunk)
                    lastLoadedZoneIndex = sim.activeZone.index
                    activeZoneLoaded = true
                }
            }

            sim.step(dtSec)
            hudSyncTimer += dtSec
            if (hudSyncTimer >= 0.08f) {
                hudSyncTimer = 0f
                viewModel.pushGlHudSnapshot(currentFps)
            }

            val w = graphics.width.coerceAtLeast(1)
            val h = graphics.height.coerceAtLeast(1)

            // Pass 1: Exynos 7870 / Mali-T830 FBO-scaled SVDAG isometric raymarched world
            svdagRenderer.renderBackdrop(
                viewportW = w,
                viewportH = h,
                camX = sim.heroX,
                camY = sim.heroY,
                timeSec = elapsedTime,
                isPaused = sim.isPaused,
                preset = state.svdagPreset
            )

            // Pass 2: LittleKt SpriteBatch depth-sorted entities, RTWP queues, telegraphs & VFX
            activeCam.update()
            activeBatch.begin(activeCam.viewProjection)

            val scalePx = IsometricProjection.pixelsPerWorldUnit(h.toFloat())
            val camX = sim.heroX
            val camY = sim.heroY

            // Scalar helpers avoid per-entity projection object allocations in the render loop.
            fun worldToScreenX(wx: Float, wy: Float, wz: Float): Float =
                IsometricProjection.worldToLittleKtX(
                    wx, wz, camX, camY, w.toFloat(), h.toFloat()
                )

            fun worldToScreenY(wx: Float, wy: Float, wz: Float): Float =
                IsometricProjection.worldToLittleKtY(
                    wx, wy, wz, camX, camY, h.toFloat()
                )

            sim.moveTargetX?.let { mtx ->
                sim.moveTargetY?.let { mty ->
                    val sx = worldToScreenX(mtx, 2.02f, mty)
                    val sy = worldToScreenY(mtx, 2.02f, mty)
                    drawRing(activeBatch, activeWhite, sx, sy, 22f, Color(0.83f, 0.61f, 0.26f, 0.75f))
                }
            }

            if (sim.isPaused || sim.queuedOrders.isNotEmpty()) {
                var prevX = worldToScreenX(sim.heroX, 2.05f, sim.heroY)
                var prevY = worldToScreenY(sim.heroX, 2.05f, sim.heroY)
                sim.queuedOrders.forEach { order ->
                    when (order) {
                        is QueuedOrder.MoveTo -> {
                            val nx = worldToScreenX(order.worldX, 2.05f, order.worldY)
                            val ny = worldToScreenY(order.worldX, 2.05f, order.worldY)
                            drawLine(activeBatch, activeWhite, prevX, prevY, nx, ny, 4f, Color(0.31f, 0.66f, 0.72f, 0.85f))
                            drawRing(activeBatch, activeWhite, nx, ny, 20f, Color(0.48f, 0.84f, 0.90f, 0.9f))
                            prevX = nx; prevY = ny
                        }
                        is QueuedOrder.AttackTarget -> {
                            sim.enemies.firstOrNull { it.id == order.enemyId && !it.isDead }?.let { t ->
                                val nx = worldToScreenX(t.x, 2.05f, t.y)
                                val ny = worldToScreenY(t.x, 2.05f, t.y)
                                drawLine(activeBatch, activeWhite, prevX, prevY, nx, ny, 4f, Color(0.88f, 0.33f, 0.27f, 0.9f))
                                drawRing(activeBatch, activeWhite, nx, ny, 34f, Color(0.88f, 0.33f, 0.27f, 0.95f))
                                prevX = nx; prevY = ny
                            }
                        }
                        is QueuedOrder.CastSkill -> {
                            val nx = worldToScreenX(order.targetX, 2.05f, order.targetY)
                            val ny = worldToScreenY(order.targetX, 2.05f, order.targetY)
                            drawLine(activeBatch, activeWhite, prevX, prevY, nx, ny, 4f, Color(0.94f, 0.75f, 0.42f, 0.9f))
                            drawRing(activeBatch, activeWhite, nx, ny, 44f, Color(0.94f, 0.75f, 0.42f, 0.85f))
                            prevX = nx; prevY = ny
                        }
                        is QueuedOrder.UsePotion -> Unit
                    }
                }
            }

            data class DrawActor(val depth: Float, val isHero: Boolean, val enemyIdx: Int)
            val drawList = ArrayList<DrawActor>(sim.enemies.size + 1)
            drawList.add(DrawActor(sim.heroX + sim.heroY, true, -1))
            sim.enemies.forEachIndexed { idx, e ->
                if (!e.isDead || e.deathTimer > 0f) {
                    drawList.add(DrawActor(e.x + e.y, false, idx))
                }
            }
            drawList.sortBy { it.depth }

            val spriteH = scalePx * 2.15f

            drawList.forEach { actor ->
                if (actor.isHero) {
                    val sx = worldToScreenX(sim.heroX, 2.0f, sim.heroY)
                    val sy = worldToScreenY(sim.heroX, 2.0f, sim.heroY)
                    drawRing(activeBatch, activeWhite, sx, sy, 28f, Color(0.83f, 0.61f, 0.26f, 0.70f))
                    val uv = combatAtlas.getHeroUv(sim.heroClass, sim.heroAnim, sim.heroAnimTime)
                    val sw = spriteH * uv.aspect
                    val tint = if (sim.heroHurtFlash > 0f) Color(1f, 0.45f, 0.45f, 1f) else Color.WHITE
                    drawAtlasRegion(activeBatch, activeAtlas, uv, sx - sw * 0.5f, sy - spriteH * 0.14f, sw, spriteH, sim.heroFacingLeft, tint)
                } else {
                    val e = sim.enemies[actor.enemyIdx]
                    val sx = worldToScreenX(e.x, 2.0f, e.y)
                    val sy = worldToScreenY(e.x, 2.0f, e.y)
                    if (sim.selectedTargetId == e.id) {
                        drawRing(activeBatch, activeWhite, sx, sy, 32f, Color(0.90f, 0.33f, 0.26f, 0.9f))
                    }
                    if (e.isWindingUp && !e.isDead) {
                        val warnR = scalePx * e.archetype.attackRange * 0.65f
                        drawRing(activeBatch, activeWhite, sx, sy, warnR, Color(0.95f, 0.30f, 0.22f, 0.80f))
                    }
                    val uv = combatAtlas.getEnemyUv(e.archetype, e.anim, e.animTime)
                    val eScale = if (e.archetype.isElite) 1.28f else if (e.archetype.id == "RAT_SWARM") 0.68f else 0.96f
                    val eh = spriteH * eScale
                    val ew = eh * uv.aspect
                    val alpha = if (e.isDead) (e.deathTimer / 1.6f).coerceIn(0f, 1f) else 1f
                    val tint = when {
                        e.hurtFlash > 0f -> Color(1f, 0.42f, 0.38f, alpha)
                        e.isWindingUp -> Color(1f, 0.82f, 0.68f, alpha)
                        else -> Color(1f, 1f, 1f, alpha)
                    }
                    drawAtlasRegion(activeBatch, activeAtlas, uv, sx - ew * 0.5f, sy - eh * 0.14f, ew, eh, e.facingLeft, tint)

                    if (!e.isDead) {
                        val barW = 54f * eScale
                        val barH = 6f
                        val bx = sx - barW * 0.5f
                        val by = sy + eh * 0.88f
                        drawRect(activeBatch, activeWhite, bx - 1f, by - 1f, barW + 2f, barH + 2f, Color(0.06f, 0.07f, 0.09f, 0.85f))
                        val frac = (e.hp.toFloat() / e.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val barCol = if (e.archetype.isElite) Color(0.92f, 0.65f, 0.22f, 1f) else Color(0.86f, 0.28f, 0.24f, 1f)
                        drawRect(activeBatch, activeWhite, bx, by, barW * frac, barH, barCol)
                    }
                }
            }

            sim.pulses.forEach { p ->
                val sx = worldToScreenX(p.x, 2.0f, p.y)
                val sy = worldToScreenY(p.x, 2.0f, p.y)
                val t = (p.age / p.duration).coerceIn(0f, 1f)
                val rad = scalePx * p.maxRadius * (0.35f + 0.65f * t)
                val col = argbToLittleKtColor(p.colorHex, 1f - t)
                drawRing(activeBatch, activeWhite, sx, sy, rad, col)
            }

            sim.projectiles.forEach { p ->
                val sx = worldToScreenX(p.x, 2.7f, p.y)
                val sy = worldToScreenY(p.x, 2.7f, p.y)
                val col = argbToLittleKtColor(p.colorHex, 1f)
                drawRect(activeBatch, activeWhite, sx - 7f, sy - 7f, 14f, 14f, col)
                drawRect(activeBatch, activeWhite, sx - 3f, sy - 3f, 6f, 6f, Color.WHITE)
            }

            activeBatch.end()
        }

        // REPAIR_HOOK: Звільнення OpenGL ресурсів LittleKt і SVDAG при закритті сцени
        onDispose {
            if (com.example.viewmodel.LoadedGameAssetsHolder.scene === this@IronboundLittleKtScene) {
                com.example.viewmodel.LoadedGameAssetsHolder.scene = null
            }
            svdagRenderer.dispose()
            batch?.dispose()
            batch = null
            atlasTexture?.dispose()
            atlasTexture = null
            whiteTex?.dispose()
            whiteTex = null
            whitePixel = null
            activeZoneLoaded = false
            lastLoadedZoneIndex = -1
        }
    }

    // REPAIR_HOOK: Завантаження SpriteBatch і текстури бойового атласу на потоці OpenGL
    private fun ensureArenaGlResources(ctx: Context, assets: CombatAtlasAssets) {
        if (camera == null) {
            val w = ctx.graphics.width.toFloat().coerceAtLeast(1f)
            val h = ctx.graphics.height.toFloat().coerceAtLeast(1f)
            camera = OrthographicCamera(w, h).apply {
                position.set(w * 0.5f, h * 0.5f, 0f)
                update()
            }
        }
        if (batch == null) {
            batch = SpriteBatch(ctx)
        }
        if (whiteTex == null) {
            val whiteBuf = createByteBuffer(byteArrayOf(-1, -1, -1, -1))
            val tex = Texture(PixmapTextureData(Pixmap(1, 1, whiteBuf), false)).apply {
                prepare(ctx)
            }
            whiteTex = tex
            whitePixel = TextureSlice(tex, 0, 0, 1, 1)
        }
        if (atlasTexture == null) {
            val bmp = assets.atlasBitmap
            val w = bmp.width
            val h = bmp.height
            val argb = IntArray(w * h)
            bmp.getPixels(argb, 0, w, 0, 0, w, h)
            val rgba = ByteArray(w * h * 4)
            for (i in argb.indices) {
                val c = argb[i]
                val o = i * 4
                rgba[o] = ((c shr 16) and 0xFF).toByte()
                rgba[o + 1] = ((c shr 8) and 0xFF).toByte()
                rgba[o + 2] = (c and 0xFF).toByte()
                rgba[o + 3] = ((c ushr 24) and 0xFF).toByte()
            }
            val buf = createByteBuffer(rgba)
            val pixmap = Pixmap(w, h, buf)
            atlasTexture = Texture(PixmapTextureData(pixmap, false)).apply {
                minFilter = TexMinFilter.LINEAR
                magFilter = TexMagFilter.LINEAR
                prepare(ctx)
            }
        }
    }

    // REPAIR_HOOK: Ізометричні перетворення екран <-> світ (синхронізовано з базисом у SvdagGlRenderer)
    fun screenToWorld(screenX: Float, screenY: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        logProjectionViewport(viewW, viewH)
        val sim = viewModel.simulation
        val world = IsometricProjection.composeToWorld(
            screenX, screenY, viewW, viewH, sim.heroX, sim.heroY
        )
        return world.x to world.z
    }

    fun worldToComposeScreen(wx: Float, wy: Float, wz: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        logProjectionViewport(viewW, viewH)
        val sim = viewModel.simulation
        val screen = IsometricProjection.worldToCompose(
            wx, wy, wz, sim.heroX, sim.heroY, viewW, viewH
        )
        return screen.x to screen.y
    }

    /**
     * Compose and LittleKt must describe the same full-surface pixel viewport.
     * Report a mismatch once per distinct size so device insets/scaling issues
     * can be diagnosed without flooding Logcat from the render/HUD loop.
     */
    private fun logProjectionViewport(viewW: Float, viewH: Float) {
        val composeW = viewW.toInt().coerceAtLeast(1)
        val composeH = viewH.toInt().coerceAtLeast(1)
        val littleKtW = gameContext.graphics.width.coerceAtLeast(1)
        val littleKtH = gameContext.graphics.height.coerceAtLeast(1)
        val mismatch = kotlin.math.abs(composeW - littleKtW) > 1 ||
            kotlin.math.abs(composeH - littleKtH) > 1

        if (mismatch) {
            val signature = "Compose=${composeW}x${composeH}, LittleKt=${littleKtW}x${littleKtH}"
            if (signature != lastViewportDiagnostic) {
                android.util.Log.w("IronboundProjection", "Viewport size mismatch (pixels): $signature")
                lastViewportDiagnostic = signature
            }
        } else if (lastViewportDiagnostic != null) {
            android.util.Log.i(
                "IronboundProjection",
                "Compose and LittleKt viewport sizes match: ${composeW}x${composeH}px"
            )
            lastViewportDiagnostic = null
        }
    }

    private fun drawAtlasRegion(
        batch: SpriteBatch,
        atlas: Texture,
        uv: AtlasUv,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        flipX: Boolean,
        color: Color
    ) {
        val slice = TextureSlice(
            atlas,
            (uv.u0 * atlas.width).toInt(),
            (uv.v0 * atlas.height).toInt(),
            ((uv.u1 - uv.u0) * atlas.width).toInt().coerceAtLeast(1),
            ((uv.v1 - uv.v0) * atlas.height).toInt().coerceAtLeast(1)
        )
        batch.draw(
            slice = slice,
            x = x,
            y = y,
            originX = 0f,
            originY = 0f,
            width = w,
            height = h,
            scaleX = 1f,
            scaleY = 1f,
            rotation = Angle.ZERO,
            colorBits = color.toFloatBits(),
            flipX = flipX,
            flipY = false
        )
    }

    private fun drawRect(
        batch: SpriteBatch,
        whitePixel: TextureSlice,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Color
    ) {
        batch.draw(
            slice = whitePixel,
            x = x,
            y = y,
            originX = 0f,
            originY = 0f,
            width = w,
            height = h,
            scaleX = 1f,
            scaleY = 1f,
            rotation = Angle.ZERO,
            colorBits = color.toFloatBits(),
            flipX = false,
            flipY = false
        )
    }

    private fun drawRing(
        batch: SpriteBatch,
        whitePixel: TextureSlice,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Color
    ) {
        val segs = 16
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (Math.PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segs) * (Math.PI.toFloat() * 2f)
            val x0 = cx + cos(a0) * radius
            val y0 = cy + sin(a0) * radius * 0.56f
            val x1 = cx + cos(a1) * radius
            val y1 = cy + sin(a1) * radius * 0.56f
            drawLine(batch, whitePixel, x0, y0, x1, y1, 2.8f, color)
        }
    }

    private fun drawLine(
        batch: SpriteBatch,
        whitePixel: TextureSlice,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        thickness: Float,
        color: Color
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
        batch.draw(
            slice = whitePixel,
            x = x0,
            y = y0 - thickness * 0.5f,
            originX = 0f,
            originY = thickness * 0.5f,
            width = len,
            height = thickness,
            scaleX = 1f,
            scaleY = 1f,
            rotation = Angle.fromRadians(angle),
            colorBits = color.toFloatBits(),
            flipX = false,
            flipY = false
        )
    }

    private fun argbToLittleKtColor(argb: Int, alphaOverride: Float): Color {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return Color(r, g, b, alphaOverride)
    }
}
