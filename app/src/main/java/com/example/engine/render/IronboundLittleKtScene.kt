package com.example.engine.render

import android.graphics.Bitmap
import com.example.engine.assets.AnimKind
import com.example.engine.assets.AtlasUv
import com.example.engine.assets.LoadedGameAssets
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
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.DurationUnit

/**
 * LittleKt ContextListener hosting the OpenGL ES 3.0 / 2.0 SVDAG environment raymarcher
 * and LittleKt SpriteBatch depth-sorted isometric character/VFX renderer.
 */
class IronboundLittleKtScene(
    context: Context,
    private val viewModel: GameViewModel
) : ContextListener(context) {

    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var atlasTexture: Texture
    private lateinit var whitePixel: TextureSlice
    private val svdagRenderer = SvdagGlRenderer()

    private var activeZoneLoaded = false
    private var lastLoadedZoneIndex = -1
    private var elapsedTime = 0f
    private var fpsAccum = 0f
    private var fpsFrames = 0
    private var currentFps = 60
    private var hudSyncTimer = 0f

    // Orthonormal basis matching SvdagGlRenderer.SVDAG_FRAGMENT_SHADER
    private val rx = 0.70710677f
    private val rz = -0.70710677f
    // rayDir = normalize(-1, -1.25, -1) = (-0.5298129, -0.6622662, -0.5298129)
    // up = cross(right, -rayDir) = (-0.4682929, 0.7492686, -0.4682929)
    private val ux = -0.4682929f
    private val uy = 0.7492686f
    private val uz = -0.4682929f
    private val zoom = 11.5f

    override suspend fun Context.start() {
        com.example.viewmodel.LoadedGameAssetsHolder.scene = this@IronboundLittleKtScene
        batch = SpriteBatch(this)
        camera = OrthographicCamera(graphics.width.toFloat(), graphics.height.toFloat())
        camera.position.set(graphics.width * 0.5f, graphics.height * 0.5f, 0f)
        camera.update()

        // Create 1x1 white pixel texture for crisp LittleKt Batch bars, selection rings, and paths
        val whiteBuf = createByteBuffer(byteArrayOf(-1, -1, -1, -1))
        val whiteTex = Texture(PixmapTextureData(Pixmap(1, 1, whiteBuf), false)).apply {
            prepare(this@start)
        }
        whitePixel = TextureSlice(whiteTex, 0, 0, 1, 1)

        viewModel.onZoneChangedForGl = { zoneId ->
            lastLoadedZoneIndex = -1
            activeZoneLoaded = false
        }

        onResize { w, h ->
            camera.ortho(w.toFloat(), h.toFloat())
            camera.position.set(w * 0.5f, h * 0.5f, 0f)
            camera.update()
        }

        onRender { dt ->
            val dtSec = dt.toDouble(DurationUnit.SECONDS).toFloat().coerceIn(0.001f, 0.05f)
            elapsedTime += dtSec
            fpsAccum += dtSec
            fpsFrames++
            if (fpsAccum >= 0.5f) {
                currentFps = (fpsFrames / fpsAccum).toInt().coerceIn(1, 120)
                fpsAccum = 0f
                fpsFrames = 0
            }

            val state = viewModel.uiState.value
            if (!state.isReady) {
                gl.clearColor(0.05f, 0.06f, 0.08f, 1f)
                gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
                return@onRender
            }

            ensureAtlasUploaded(this, viewModel.loadedAssets)

            if (state.screen != AppScreen.ARENA) {
                gl.clearColor(0.05f, 0.06f, 0.08f, 1f)
                gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
                return@onRender
            }

            val sim = viewModel.simulation
            if (!activeZoneLoaded || lastLoadedZoneIndex != sim.activeZone.index) {
                viewModel.svdagChunks[sim.activeZone]?.let { chunk ->
                    svdagRenderer.uploadChunk(chunk)
                    lastLoadedZoneIndex = sim.activeZone.index
                    activeZoneLoaded = true
                }
            }

            // Step combat simulation
            sim.step(dtSec)
            hudSyncTimer += dtSec
            if (hudSyncTimer >= 0.08f) {
                hudSyncTimer = 0f
                viewModel.pushGlHudSnapshot(currentFps)
            }

            val w = graphics.width
            val h = graphics.height

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
            camera.update()
            batch.begin(camera.viewProjection)

            val scalePx = (h * 0.5f) / zoom
            val camX = sim.heroX
            val camY = sim.heroY

            fun worldToScreenX(wx: Float, wy: Float, wz: Float): Float {
                val dx = wx - camX
                val dy = wy - 1.8f
                val dz = wz - camY
                return w * 0.5f + (dx * rx + dz * rz) * scalePx
            }

            fun worldToScreenY(wx: Float, wy: Float, wz: Float): Float {
                val dx = wx - camX
                val dy = wy - 1.8f
                val dz = wz - camY
                return h * 0.5f + (dx * ux + dy * uy + dz * uz) * scalePx
            }

            // Draw tap-to-move waypoint & RTWP queued orders
            sim.moveTargetX?.let { mtx ->
                sim.moveTargetY?.let { mty ->
                    val sx = worldToScreenX(mtx, 2.02f, mty)
                    val sy = worldToScreenY(mtx, 2.02f, mty)
                    drawRing(sx, sy, 22f, Color(0.83f, 0.61f, 0.26f, 0.75f))
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
                            drawLine(prevX, prevY, nx, ny, 4f, Color(0.31f, 0.66f, 0.72f, 0.85f))
                            drawRing(nx, ny, 20f, Color(0.48f, 0.84f, 0.90f, 0.9f))
                            prevX = nx; prevY = ny
                        }
                        is QueuedOrder.AttackTarget -> {
                            sim.enemies.firstOrNull { it.id == order.enemyId && !it.isDead }?.let { t ->
                                val nx = worldToScreenX(t.x, 2.05f, t.y)
                                val ny = worldToScreenY(t.x, 2.05f, t.y)
                                drawLine(prevX, prevY, nx, ny, 4f, Color(0.88f, 0.33f, 0.27f, 0.9f))
                                drawRing(nx, ny, 34f, Color(0.88f, 0.33f, 0.27f, 0.95f))
                                prevX = nx; prevY = ny
                            }
                        }
                        is QueuedOrder.CastSkill -> {
                            val nx = worldToScreenX(order.targetX, 2.05f, order.targetY)
                            val ny = worldToScreenY(order.targetX, 2.05f, order.targetY)
                            drawLine(prevX, prevY, nx, ny, 4f, Color(0.94f, 0.75f, 0.42f, 0.9f))
                            drawRing(nx, ny, 44f, Color(0.94f, 0.75f, 0.42f, 0.85f))
                            prevX = nx; prevY = ny
                        }
                        is QueuedOrder.UsePotion -> Unit
                    }
                }
            }

            // Collect hero & enemies for isometric depth sorting (back-to-front by x + z)
            data class DrawActor(val depth: Float, val isHero: Boolean, val enemyIdx: Int)
            val drawList = ArrayList<DrawActor>(sim.enemies.size + 1)
            drawList.add(DrawActor(sim.heroX + sim.heroY, true, -1))
            sim.enemies.forEachIndexed { idx, e ->
                if (!e.isDead || e.deathTimer > 0f) {
                    drawList.add(DrawActor(e.x + e.y, false, idx))
                }
            }
            drawList.sortBy { it.depth }

            val assets = viewModel.loadedAssets
            val spriteH = scalePx * 2.15f

            drawList.forEach { actor ->
                if (actor.isHero) {
                    val sx = worldToScreenX(sim.heroX, 2.0f, sim.heroY)
                    val sy = worldToScreenY(sim.heroX, 2.0f, sim.heroY)
                    // Hero ground ring
                    drawRing(sx, sy, 28f, Color(0.83f, 0.61f, 0.26f, 0.70f))
                    val uv = assets.getHeroUv(sim.heroClass, sim.heroAnim, sim.heroAnimTime)
                    val sw = spriteH * uv.aspect
                    val tint = if (sim.heroHurtFlash > 0f) Color(1f, 0.45f, 0.45f, 1f) else Color.WHITE
                    drawAtlasRegion(uv, sx - sw * 0.5f, sy - spriteH * 0.14f, sw, spriteH, sim.heroFacingLeft, tint)
                } else {
                    val e = sim.enemies[actor.enemyIdx]
                    val sx = worldToScreenX(e.x, 2.0f, e.y)
                    val sy = worldToScreenY(e.x, 2.0f, e.y)
                    val isSel = sim.selectedTargetId == e.id
                    if (isSel) {
                        drawRing(sx, sy, 32f, Color(0.90f, 0.33f, 0.26f, 0.9f))
                    }
                    if (e.isWindingUp && !e.isDead) {
                        // Telegraph danger ring visible clearly during Tactical Pause!
                        val warnR = scalePx * e.archetype.attackRange * 0.65f
                        drawRing(sx, sy, warnR, Color(0.95f, 0.30f, 0.22f, 0.80f))
                    }
                    val uv = assets.getEnemyUv(e.archetype, e.anim, e.animTime)
                    val eScale = if (e.archetype.isElite) 1.28f else if (e.archetype.id == "RAT_SWARM") 0.68f else 0.96f
                    val eh = spriteH * eScale
                    val ew = eh * uv.aspect
                    val alpha = if (e.isDead) (e.deathTimer / 1.6f).coerceIn(0f, 1f) else 1f
                    val tint = when {
                        e.hurtFlash > 0f -> Color(1f, 0.42f, 0.38f, alpha)
                        e.isWindingUp -> Color(1f, 0.82f, 0.68f, alpha)
                        else -> Color(1f, 1f, 1f, alpha)
                    }
                    drawAtlasRegion(uv, sx - ew * 0.5f, sy - eh * 0.14f, ew, eh, e.facingLeft, tint)

                    // Enemy HP bar
                    if (!e.isDead) {
                        val barW = 54f * eScale
                        val barH = 6f
                        val bx = sx - barW * 0.5f
                        val by = sy + eh * 0.88f
                        drawRect(bx - 1f, by - 1f, barW + 2f, barH + 2f, Color(0.06f, 0.07f, 0.09f, 0.85f))
                        val frac = (e.hp.toFloat() / e.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val barCol = if (e.archetype.isElite) Color(0.92f, 0.65f, 0.22f, 1f) else Color(0.86f, 0.28f, 0.24f, 1f)
                        drawRect(bx, by, barW * frac, barH, barCol)
                    }
                }
            }

            // Slash & skill pulses
            sim.pulses.forEach { p ->
                val sx = worldToScreenX(p.x, 2.0f, p.y)
                val sy = worldToScreenY(p.x, 2.0f, p.y)
                val t = (p.age / p.duration).coerceIn(0f, 1f)
                val rad = scalePx * p.maxRadius * (0.35f + 0.65f * t)
                val col = argbToLittleKtColor(p.colorHex, 1f - t)
                drawRing(sx, sy, rad, col)
            }

            // Projectiles
            sim.projectiles.forEach { p ->
                val sx = worldToScreenX(p.x, 2.7f, p.y)
                val sy = worldToScreenY(p.x, 2.7f, p.y)
                val col = argbToLittleKtColor(p.colorHex, 1f)
                drawRect(sx - 7f, sy - 7f, 14f, 14f, col)
                drawRect(sx - 3f, sy - 3f, 6f, 6f, Color.WHITE)
            }

            batch.end()
        }

        onDispose {
            svdagRenderer.dispose()
            batch.dispose()
        }
    }

    /**
     * Converts normalized screen touch coordinates (Compose Box pointer down)
     * into exact SVDAG arena world (x, z) coordinates by inverting the isometric ground projection.
     */
    fun screenToWorld(screenX: Float, screenY: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        val sim = viewModel.simulation
        val scalePx = (viewH * 0.5f) / zoom
        val px = (screenX - viewW * 0.5f) / scalePx
        // Screen Y in Android Compose is top-down, whereas LittleKt camera Y is bottom-up
        val glY = viewH - screenY
        val py = (glY - viewH * 0.5f) / scalePx
        // Ground plane dy = 2.0 - 1.8 = 0.2
        val pyGround = py - 0.2f * uy
        // Solve:
        // px = dx * rx + dz * rz  (rx = 0.7071, rz = -0.7071)
        // pyGround = dx * ux + dz * uz (ux = uz = -0.4683)
        val dxMinusDz = px / rx
        val dxPlusDz = pyGround / ux
        val dx = (dxPlusDz + dxMinusDz) * 0.5f
        val dz = (dxPlusDz - dxMinusDz) * 0.5f
        return (sim.heroX + dx) to (sim.heroY + dz)
    }

    fun worldToComposeScreen(wx: Float, wy: Float, wz: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        val sim = viewModel.simulation
        val scalePx = (viewH * 0.5f) / zoom
        val dx = wx - sim.heroX
        val dy = wy - 1.8f
        val dz = wz - sim.heroY
        val sx = viewW * 0.5f + (dx * rx + dz * rz) * scalePx
        val glY = viewH * 0.5f + (dx * ux + dy * uy + dz * uz) * scalePx
        return sx to (viewH - glY)
    }

    private fun ensureAtlasUploaded(ctx: Context, assets: LoadedGameAssets) {
        if (::atlasTexture.isInitialized) return
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

    private fun drawAtlasRegion(
        uv: AtlasUv,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        flipX: Boolean,
        color: Color
    ) {
        val slice = TextureSlice(
            atlasTexture,
            (uv.u0 * atlasTexture.width).toInt(),
            (uv.v0 * atlasTexture.height).toInt(),
            ((uv.u1 - uv.u0) * atlasTexture.width).toInt().coerceAtLeast(1),
            ((uv.v1 - uv.v0) * atlasTexture.height).toInt().coerceAtLeast(1)
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

    private fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Color) {
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

    private fun drawRing(cx: Float, cy: Float, radius: Float, color: Color) {
        val segs = 16
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (Math.PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segs) * (Math.PI.toFloat() * 2f)
            val x0 = cx + cos(a0) * radius
            val y0 = cy + sin(a0) * radius * 0.56f
            val x1 = cx + cos(a1) * radius
            val y1 = cy + sin(a1) * radius * 0.56f
            drawLine(x0, y0, x1, y1, 2.8f, color)
        }
    }

    private fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, thickness: Float, color: Color) {
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
