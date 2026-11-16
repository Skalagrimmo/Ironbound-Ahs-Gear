package com.example.engine.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.EnemyArchetype
import com.example.model.HeroClass
import com.example.model.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class AnimKind {
    IDLE,
    WALK,
    ATTACK,
    HURT,
    DEATH
}

data class AtlasUv(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val aspect: Float
)

class LoadedGameAssets(
    val atlasBitmap: Bitmap,
    val heroPortraits: Map<HeroClass, ImageBitmap>,
    val worldMapBitmap: ImageBitmap,
    val zoneThumbnails: Map<ZoneId, ImageBitmap>,
    private val heroFrames: Map<Pair<HeroClass, AnimKind>, List<AtlasUv>>,
    private val enemyFrames: Map<Pair<EnemyArchetype, AnimKind>, List<AtlasUv>>
) {
    fun getHeroUv(hc: HeroClass, anim: AnimKind, animTime: Float): AtlasUv {
        val list = heroFrames[hc to anim] ?: heroFrames[hc to AnimKind.IDLE]!!
        val idx = ((animTime * 4.5f).toInt()) % list.size
        return list[idx]
    }

    fun getEnemyUv(archetype: EnemyArchetype, anim: AnimKind, animTime: Float): AtlasUv {
        val list = enemyFrames[archetype to anim] ?: enemyFrames[archetype to AnimKind.IDLE]!!
        val idx = ((animTime * 4.2f).toInt()) % list.size
        return list[idx]
    }
}

object SpriteSheetLoader {

    // Exact pixel coordinates measured from 1408x768 sprites_characters.jpg
    private val sixColsLeftA = listOf(194 to 280, 284 to 362, 366 to 444, 448 to 526, 530 to 607, 611 to 688)
    private val sixColsLeftB = listOf(194 to 275, 279 to 357, 361 to 439, 443 to 521, 525 to 604, 608 to 688)
    private val sixColsRight = listOf(724 to 856, 864 to 958, 964 to 1068, 1074 to 1186, 1194 to 1284, 1292 to 1386)

    fun loadAll(context: Context): LoadedGameAssets {
        val spriteSheet = context.assets.open("sprites_characters.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        val biomeSheet = context.assets.open("world_biomes.jpg").use {
            BitmapFactory.decodeStream(it)
        }

        val portraits = mapOf(
            HeroClass.KNIGHT to cropCompose(spriteSheet, 14, 44, 182, 244),
            HeroClass.RIFLEMAN to cropCompose(spriteSheet, 14, 290, 182, 494),
            HeroClass.TECHNOMANCER to cropCompose(spriteSheet, 14, 534, 182, 738)
        )

        // Exact panels from 1408x768 world_biomes.jpg
        val worldMap = cropCompose(biomeSheet, 16, 528, 326, 750)
        val zoneThumbs = mapOf(
            ZoneId.FACTORY_ZONE to cropCompose(biomeSheet, 12, 12, 698, 488),
            ZoneId.INTERIOR_LAB to cropCompose(biomeSheet, 712, 12, 1046, 248),
            ZoneId.RESIDENTIAL_DISTRICT to cropCompose(biomeSheet, 1058, 12, 1394, 248),
            ZoneId.THE_RUINS to cropCompose(biomeSheet, 346, 506, 698, 754),
            ZoneId.DARK_CONFLUENCE to cropCompose(biomeSheet, 712, 262, 1394, 754)
        )

        // Build clean 1024x1024 transparent sprite atlas by edge-floodfill removing grid background
        val atlasSize = 1024
        val cellSize = 128
        val atlas = Bitmap.createBitmap(atlasSize, atlasSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(atlas)

        var cellIdx = 0
        val heroFrames = mutableMapOf<Pair<HeroClass, AnimKind>, MutableList<AtlasUv>>()
        val enemyFrames = mutableMapOf<Pair<EnemyArchetype, AnimKind>, MutableList<AtlasUv>>()

        fun addCell(srcRect: Rect): AtlasUv {
            val col = cellIdx % 8
            val row = cellIdx / 8
            cellIdx++
            val dstX = col * cellSize
            val dstY = row * cellSize
            val extracted = extractTransparentSprite(spriteSheet, srcRect)
            // Scale cleanly preserving aspect inside 120x120 padded inside 128x128 cell
            val maxDim = max(extracted.width, extracted.height).coerceAtLeast(1)
            val scale = 118f / maxDim
            val dw = (extracted.width * scale).toInt().coerceAtLeast(1)
            val dh = (extracted.height * scale).toInt().coerceAtLeast(1)
            val ox = dstX + (cellSize - dw) / 2
            val oy = dstY + (cellSize - dh) / 2
            canvas.drawBitmap(extracted, null, Rect(ox, oy, ox + dw, oy + dh), null)
            extracted.recycle()
            return AtlasUv(
                u0 = dstX / atlasSize.toFloat(),
                v0 = dstY / atlasSize.toFloat(),
                u1 = (dstX + cellSize) / atlasSize.toFloat(),
                v1 = (dstY + cellSize) / atlasSize.toFloat(),
                aspect = dw.toFloat() / dh.toFloat()
            )
        }

        // Armored Knight: row1 y=66..146, row2 y=170..248
        heroFrames[HeroClass.KNIGHT to AnimKind.IDLE] = mutableListOf(
            addCell(Rect(sixColsLeftA[0].first, 170, sixColsLeftA[0].second, 248)),
            addCell(Rect(sixColsLeftA[0].first, 66, sixColsLeftA[0].second, 146))
        )
        heroFrames[HeroClass.KNIGHT to AnimKind.WALK] = mutableListOf(
            addCell(Rect(sixColsLeftA[1].first, 170, sixColsLeftA[1].second, 248)),
            addCell(Rect(sixColsLeftA[2].first, 66, sixColsLeftA[2].second, 146))
        )
        heroFrames[HeroClass.KNIGHT to AnimKind.ATTACK] = mutableListOf(
            addCell(Rect(sixColsLeftA[2].first, 170, sixColsLeftA[2].second, 248)),
            addCell(Rect(sixColsLeftA[3].first, 170, sixColsLeftA[3].second, 248))
        )
        heroFrames[HeroClass.KNIGHT to AnimKind.HURT] = mutableListOf(
            addCell(Rect(sixColsLeftA[4].first, 170, sixColsLeftA[4].second, 248))
        )
        heroFrames[HeroClass.KNIGHT to AnimKind.DEATH] = mutableListOf(
            addCell(Rect(sixColsLeftA[5].first, 170, sixColsLeftA[5].second, 248))
        )

        // Rifleman-Archer: row1 y=318..398, row2 y=418..498
        heroFrames[HeroClass.RIFLEMAN to AnimKind.IDLE] = mutableListOf(
            addCell(Rect(sixColsLeftA[0].first, 418, sixColsLeftA[0].second, 498)),
            addCell(Rect(sixColsLeftA[0].first, 318, sixColsLeftA[0].second, 398))
        )
        heroFrames[HeroClass.RIFLEMAN to AnimKind.WALK] = mutableListOf(
            addCell(Rect(sixColsLeftA[1].first, 418, sixColsLeftA[1].second, 498)),
            addCell(Rect(sixColsLeftA[2].first, 318, sixColsLeftA[2].second, 398))
        )
        heroFrames[HeroClass.RIFLEMAN to AnimKind.ATTACK] = mutableListOf(
            addCell(Rect(sixColsLeftA[2].first, 418, sixColsLeftA[2].second, 498)),
            addCell(Rect(sixColsLeftA[3].first, 418, sixColsLeftA[3].second, 498))
        )
        heroFrames[HeroClass.RIFLEMAN to AnimKind.HURT] = mutableListOf(
            addCell(Rect(sixColsLeftA[4].first, 418, sixColsLeftA[4].second, 498))
        )
        heroFrames[HeroClass.RIFLEMAN to AnimKind.DEATH] = mutableListOf(
            addCell(Rect(sixColsLeftA[5].first, 418, sixColsLeftA[5].second, 498))
        )

        // Technomancer Mage: row1 y=558..645, row2 y=660..742
        heroFrames[HeroClass.TECHNOMANCER to AnimKind.IDLE] = mutableListOf(
            addCell(Rect(sixColsLeftB[0].first, 660, sixColsLeftB[0].second, 742)),
            addCell(Rect(sixColsLeftB[0].first, 558, sixColsLeftB[0].second, 645))
        )
        heroFrames[HeroClass.TECHNOMANCER to AnimKind.WALK] = mutableListOf(
            addCell(Rect(sixColsLeftB[2].first, 558, sixColsLeftB[2].second, 645)),
            addCell(Rect(sixColsLeftB[3].first, 558, sixColsLeftB[3].second, 645))
        )
        heroFrames[HeroClass.TECHNOMANCER to AnimKind.ATTACK] = mutableListOf(
            addCell(Rect(sixColsLeftB[1].first, 660, sixColsLeftB[1].second, 742)),
            addCell(Rect(sixColsLeftB[2].first, 660, sixColsLeftB[2].second, 742))
        )
        heroFrames[HeroClass.TECHNOMANCER to AnimKind.HURT] = mutableListOf(
            addCell(Rect(sixColsLeftB[4].first, 660, sixColsLeftB[4].second, 742))
        )
        heroFrames[HeroClass.TECHNOMANCER to AnimKind.DEATH] = mutableListOf(
            addCell(Rect(sixColsLeftB[5].first, 660, sixColsLeftB[5].second, 742))
        )

        // Enemies (Right side):
        // Construct y=70..208
        mapEnemyRow(enemyFrames, EnemyArchetype.CONSTRUCT, 70, 208, ::addCell)
        // Scrapper y=304..408
        mapEnemyRow(enemyFrames, EnemyArchetype.SCRAPPER, 304, 408, ::addCell)
        // Cultist y=486..600
        mapEnemyRow(enemyFrames, EnemyArchetype.CULTIST, 486, 600, ::addCell)

        // Rat Swarm y=654..740 (4 cols)
        val rIdle0 = addCell(Rect(sixColsRight[0].first, 654, sixColsRight[0].second, 740))
        val rIdle1 = addCell(Rect(sixColsRight[1].first, 654, sixColsRight[1].second, 740))
        val rScurry = addCell(Rect(sixColsRight[2].first, 654, sixColsRight[2].second, 740))
        val rAtk = addCell(Rect(sixColsRight[3].first, 654, sixColsRight[3].second, 740))
        enemyFrames[EnemyArchetype.RAT_SWARM to AnimKind.IDLE] = mutableListOf(rIdle0, rIdle1)
        enemyFrames[EnemyArchetype.RAT_SWARM to AnimKind.WALK] = mutableListOf(rScurry, rIdle1)
        enemyFrames[EnemyArchetype.RAT_SWARM to AnimKind.ATTACK] = mutableListOf(rAtk)
        enemyFrames[EnemyArchetype.RAT_SWARM to AnimKind.HURT] = mutableListOf(rIdle0)
        enemyFrames[EnemyClassFallback(EnemyArchetype.RAT_SWARM) to AnimKind.DEATH] = mutableListOf(rIdle1)

        spriteSheet.recycle()
        biomeSheet.recycle()

        return LoadedGameAssets(
            atlasBitmap = atlas,
            heroPortraits = portraits,
            worldMapBitmap = worldMap,
            zoneThumbnails = zoneThumbs,
            heroFrames = heroFrames,
            enemyFrames = enemyFrames
        )
    }

    private fun EnemyClassFallback(e: EnemyArchetype) = e

    private fun mapEnemyRow(
        map: MutableMap<Pair<EnemyArchetype, AnimKind>, MutableList<AtlasUv>>,
        archetype: EnemyArchetype,
        y0: Int,
        y1: Int,
        addCell: (Rect) -> AtlasUv
    ) {
        val idle0 = addCell(Rect(sixColsRight[0].first, y0, sixColsRight[0].second, y1))
        val idle1 = addCell(Rect(sixColsRight[1].first, y0, sixColsRight[1].second, y1))
        val walk = addCell(Rect(sixColsRight[2].first, y0, sixColsRight[2].second, y1))
        val atk = addCell(Rect(sixColsRight[3].first, y0, sixColsRight[3].second, y1))
        val hurt = addCell(Rect(sixColsRight[4].first, y0, sixColsRight[4].second, y1))
        val death = addCell(Rect(sixColsRight[5].first, y0, sixColsRight[5].second, y1))

        map[archetype to AnimKind.IDLE] = mutableListOf(idle0, idle1)
        map[archetype to AnimKind.WALK] = mutableListOf(walk, idle1)
        map[archetype to AnimKind.ATTACK] = mutableListOf(atk)
        map[archetype to AnimKind.HURT] = mutableListOf(hurt)
        map[archetype to AnimKind.DEATH] = mutableListOf(death)
    }

    private fun cropCompose(src: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): ImageBitmap {
        val cx = x0.coerceIn(0, src.width - 1)
        val cy = y0.coerceIn(0, src.height - 1)
        val cw = (x1 - cx).coerceIn(1, src.width - cx)
        val ch = (y1 - cy).coerceIn(1, src.height - cy)
        return Bitmap.createBitmap(src, cx, cy, cw, ch).asImageBitmap()
    }

    /**
     * Flood-fills from cell borders to key out the cool dark-slate grid background (#28333E)
     * while preserving internal dark armor outlines and glowing spell particles.
     */
    private fun extractTransparentSprite(sheet: Bitmap, r: Rect): Bitmap {
        val w = r.width().coerceAtLeast(1)
        val h = r.height().coerceAtLeast(1)
        val pixels = IntArray(w * h)
        sheet.getPixels(pixels, 0, w, r.left, r.top, w, h)

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        fun isBgSlate(c: Int): Boolean {
            val rr = Color.red(c)
            val gg = Color.green(c)
            val bb = Color.blue(c)
            val mx = max(rr, max(gg, bb))
            val mn = min(rr, min(gg, bb))
            // Grid cell background is dark low-chroma blue-slate (~20..76 brightness)
            return mx < 86 && (mx - mn) < 26 && (bb >= rr - 4)
        }

        fun enqueueIfBg(x: Int, y: Int) {
            val idx = y * w + x
            if (!visited[idx] && isBgSlate(pixels[idx])) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until w) {
            enqueueIfBg(x, 0)
            enqueueIfBg(x, h - 1)
        }
        for (y in 0 until h) {
            enqueueIfBg(0, y)
            enqueueIfBg(w - 1, y)
        }

        while (head < tail) {
            val idx = queue[head++]
            pixels[idx] = 0x00000000
            val cx = idx % w
            val cy = idx / w
            if (cx > 0) enqueueIfBg(cx - 1, cy)
            if (cx < w - 1) enqueueIfBg(cx + 1, cy)
            if (cy > 0) enqueueIfBg(cx, cy - 1)
            if (cy < h - 1) enqueueIfBg(cx, cy + 1)
        }

        // Soften 1px fringe around cleared background
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (pixels[i] != 0 && isBgSlate(pixels[i])) {
                    val adjBg = (if (pixels[i - 1] == 0) 1 else 0) +
                        (if (pixels[i + 1] == 0) 1 else 0) +
                        (if (pixels[i - w] == 0) 1 else 0) +
                        (if (pixels[i + w] == 0) 1 else 0)
                    if (adjBg >= 2) {
                        val c = pixels[i]
                        pixels[i] = Color.argb(95, Color.red(c), Color.green(c), Color.blue(c))
                    }
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
