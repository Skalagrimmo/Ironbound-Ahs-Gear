package com.example.engine.svdag

import com.example.model.ZoneId
import kotlin.math.abs

/**
 * Sparse Voxel Directed Acyclic Graph (SVDAG) compiler for 32x16x32 isometric biome chunks.
 * Deduplicates identical octree subtrees into canonical DAG node IDs so Mali-T830 (Exynos 7870)
 * caches repeated gothic arches, cobblestone tiles, factory pipes, and rustwood geometry cleanly.
 */
data class SvdagCompiledChunk(
    val zoneId: ZoneId,
    val rootNodeIndex: Int,
    val uniqueNodeCount: Int,
    val rawVoxelCount: Int,
    val compressionRatio: Float,
    /**
     * Packed RGBA8 texture rows (width = 256, height = ceil(nodeCount * 3 / 256) or 4 texels per node):
     * Actually stored as 4 texels per node (stride 4 RGBA texels in a 256-wide texture):
     * Texel 0: (childMask, materialId, unused, unused)
     * Texel 1..2: 8 child node indices encoded as 16-bit (lo, hi) pairs across 4 RGBA texels!
     * Total 5 texels per DAG node -> ultra-fast dependent lookup in GLSL ES 3.00 / 2.00.
     */
    val packedRgbaBytes: ByteArray,
    val texWidth: Int,
    val texHeight: Int
)

object SvdagBuilder {

    // Material IDs rendered by the SVDAG GLSL shader
    const val MAT_EMPTY = 0
    const val MAT_COBBLESTONE = 1
    const val MAT_DARK_BRICK = 2
    const val MAT_RUST_IRON = 3
    const val MAT_BRASS_PIPE = 4
    const val MAT_AETHER_VAT = 5
    const val MAT_CANAL_WATER = 6
    const val MAT_RUSTWOOD_BARK = 7
    const val MAT_TOXIC_MOSS = 8

    private data class OctNodeKey(
        val childMask: Int,
        val material: Int,
        val c0: Int, val c1: Int, val c2: Int, val c3: Int,
        val c4: Int, val c5: Int, val c6: Int, val c7: Int
    )

    fun buildForZone(zoneId: ZoneId): SvdagCompiledChunk {
        val size = 32 // 32x32x32 octree domain (y height uses 0..15)
        val grid = IntArray(size * size * size)
        var solidCount = 0

        fun setVoxel(x: Int, y: Int, z: Int, mat: Int) {
            if (x in 0 until size && y in 0 until size && z in 0 until size) {
                val idx = (y * size + z) * size + x
                if (grid[idx] == 0 && mat != 0) solidCount++
                grid[idx] = mat
            }
        }

        // Base terrain floor (y = 0..1)
        for (z in 0 until size) {
            for (x in 0 until size) {
                val floorMat = when (zoneId) {
                    ZoneId.FACTORY_ZONE -> if ((x + z) % 4 == 0) MAT_RUST_IRON else MAT_COBBLESTONE
                    ZoneId.INTERIOR_LAB -> if ((x / 2 + z / 2) % 2 == 0) MAT_DARK_BRICK else MAT_RUST_IRON
                    ZoneId.RESIDENTIAL_DISTRICT -> if (x in 13..18 || z in 13..18) MAT_COBBLESTONE else MAT_DARK_BRICK
                    ZoneId.THE_RUINS -> if (x in 12..19) MAT_CANAL_WATER else MAT_COBBLESTONE
                    ZoneId.DARK_CONFLUENCE -> if ((x - 16) * (x - 16) + (z - 16) * (z - 16) < 36) MAT_TOXIC_MOSS else MAT_COBBLESTONE
                }
                setVoxel(x, 0, z, floorMat)
                if (floorMat != MAT_CANAL_WATER) {
                    setVoxel(x, 1, z, floorMat)
                }
            }
        }

        // Sculpt distinct gothic-industrial SVDAG silhouettes around perimeter & landmarks
        when (zoneId) {
            ZoneId.FACTORY_ZONE -> {
                // Northern gothic factory facade + twin clocktower chimneys
                for (x in 2..29) {
                    for (y in 2..8) {
                        if (x % 4 != 0 || y > 5) setVoxel(x, y, 2, MAT_DARK_BRICK)
                    }
                }
                // Smokestacks & overhead brass steam pipe
                for (y in 2..14) {
                    for (dx in 0..1) for (dz in 0..1) {
                        setVoxel(6 + dx, y, 3 + dz, MAT_RUST_IRON)
                        setVoxel(24 + dx, y, 3 + dz, MAT_RUST_IRON)
                    }
                }
                for (x in 6..25) setVoxel(x, 7, 4, MAT_BRASS_PIPE)
                // Central plaza monument plinth
                for (dx in 15..16) for (dz in 15..16) {
                    setVoxel(dx, 2, dz, MAT_COBBLESTONE)
                    setVoxel(dx, 3, dz, MAT_BRASS_PIPE)
                }
            }
            ZoneId.INTERIOR_LAB -> {
                // Alchemical vats + steel catwalk ribs
                val vats = listOf(5 to 5, 26 to 5, 5 to 26, 26 to 26)
                vats.forEach { (vx, vz) ->
                    for (y in 2..7) {
                        val m = if (y in 3..6) MAT_AETHER_VAT else MAT_BRASS_PIPE
                        setVoxel(vx, y, vz, m)
                        setVoxel(vx + 1, y, vz, m)
                        setVoxel(vx, y, vz + 1, m)
                        setVoxel(vx + 1, y, vz + 1, m)
                    }
                }
                for (x in 3..28) {
                    setVoxel(x, 2, 2, MAT_RUST_IRON)
                    setVoxel(x, 6, 2, MAT_BRASS_PIPE)
                }
            }
            ZoneId.RESIDENTIAL_DISTRICT -> {
                // Brick townhouse corners & cobblestone market stalls
                for (x in 2..10) for (z in 2..7) for (y in 2..7) {
                    if (x == 2 || x == 10 || z == 2 || z == 7 || y == 7) setVoxel(x, y, z, MAT_DARK_BRICK)
                }
                for (x in 21..29) for (z in 2..7) for (y in 2..8) {
                    if (x == 21 || x == 29 || z == 2 || z == 7 || y == 8) setVoxel(x, y, z, MAT_DARK_BRICK)
                }
                // Market awning stalls
                for (sx in listOf(7, 23)) {
                    for (dx in 0..2) for (dz in 0..1) setVoxel(sx + dx, 3, 21 + dz, MAT_BRASS_PIPE)
                }
            }
            ZoneId.THE_RUINS -> {
                // Broken stone bridge over flooded canal + crumbling pillars
                for (x in 11..20) for (z in 14..17) {
                    setVoxel(x, 1, z, MAT_COBBLESTONE)
                }
                for (pz in listOf(4, 9, 22, 27)) {
                    val hLeft = 4 + (pz % 3) * 2
                    for (y in 2..hLeft) setVoxel(9, y, pz, MAT_DARK_BRICK)
                    for (y in 2..(hLeft - 1)) setVoxel(22, y, pz, MAT_DARK_BRICK)
                }
            }
            ZoneId.DARK_CONFLUENCE -> {
                // Corroded pipes + gnarled Rustwood trees
                for (x in 2..29) {
                    setVoxel(x, 3, 4, MAT_BRASS_PIPE)
                    if (x % 3 == 0) setVoxel(x, 2, 4, MAT_RUST_IRON)
                }
                val trees = listOf(4 to 6, 26 to 7, 5 to 25, 25 to 24)
                trees.forEach { (tx, tz) ->
                    for (y in 2..9) {
                        setVoxel(tx, y, tz, MAT_RUSTWOOD_BARK)
                        if (y >= 6) {
                            setVoxel(tx + 1, y, tz, MAT_TOXIC_MOSS)
                            setVoxel(tx - 1, y, tz, MAT_TOXIC_MOSS)
                            setVoxel(tx, y, tz + 1, MAT_TOXIC_MOSS)
                        }
                    }
                }
                // Overgrown moss patches
                for (z in 2..29) for (x in 2..29) {
                    if (abs(x - 16) + abs(z - 16) in 8..10 && (x * z) % 5 == 0) {
                        setVoxel(x, 2, z, MAT_TOXIC_MOSS)
                    }
                }
            }
        }

        // Compile bottom-up into deduplicated SVDAG nodes
        val nodeTable = ArrayList<OctNodeKey>(1024)
        val dedupMap = HashMap<OctNodeKey, Int>(1024)

        // Index 0 is reserved empty node
        nodeTable.add(OctNodeKey(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        fun intern(key: OctNodeKey): Int {
            return dedupMap.getOrPut(key) {
                val id = nodeTable.size
                nodeTable.add(key)
                id
            }
        }

        fun buildNode(ox: Int, oy: Int, oz: Int, halfSize: Int): Int {
            if (halfSize == 1) {
                // 2x2x2 leaf block
                var mask = 0
                var dominantMat = 0
                val children = IntArray(8)
                for (c in 0 until 8) {
                    val cx = ox + (c and 1)
                    val cy = oy + ((c shr 1) and 1)
                    val cz = oz + ((c shr 2) and 1)
                    val mat = grid[(cy * size + cz) * size + cx]
                    if (mat != 0) {
                        mask = mask or (1 shl c)
                        dominantMat = mat
                        children[c] = mat
                    }
                }
                if (mask == 0) return 0
                return intern(
                    OctNodeKey(
                        mask, dominantMat,
                        children[0], children[1], children[2], children[3],
                        children[4], children[5], children[6], children[7]
                    )
                )
            }

            var mask = 0
            val children = IntArray(8)
            var matSample = 0
            val q = halfSize / 2
            for (c in 0 until 8) {
                val cx = ox + if ((c and 1) != 0) halfSize else 0
                val cy = oy + if ((c and 2) != 0) halfSize else 0
                val cz = oz + if ((c and 4) != 0) halfSize else 0
                val childId = buildNode(cx, cy, cz, q)
                if (childId != 0) {
                    mask = mask or (1 shl c)
                    children[c] = childId
                    if (matSample == 0) matSample = nodeTable[childId].material
                }
            }
            if (mask == 0) return 0
            return intern(
                OctNodeKey(
                    mask, matSample,
                    children[0], children[1], children[2], children[3],
                    children[4], children[5], children[6], children[7]
                )
            )
        }

        val rootIdx = buildNode(0, 0, 0, size / 2)
        val nodeCount = nodeTable.size
        // Pack 5 RGBA texels per node in a 256-px wide texture
        val texWidth = 256
        val totalTexels = nodeCount * 5
        val texHeight = ((totalTexels + texWidth - 1) / texWidth).coerceAtLeast(1)
        val rgba = ByteArray(texWidth * texHeight * 4)

        for (i in 0 until nodeCount) {
            val n = nodeTable[i]
            val base = i * 5 * 4
            // Texel 0: (childMask, material, isLeafFlag, 255)
            rgba[base + 0] = n.childMask.toByte()
            rgba[base + 1] = n.material.toByte()
            rgba[base + 2] = 0
            rgba[base + 3] = 255.toByte()

            val cs = intArrayOf(n.c0, n.c1, n.c2, n.c3, n.c4, n.c5, n.c6, n.c7)
            // Texel 1..4: 2 child indices per RGBA texel (16-bit lo/hi each)
            for (pair in 0 until 4) {
                val tBase = base + (1 + pair) * 4
                val a = cs[pair * 2]
                val b = cs[pair * 2 + 1]
                rgba[tBase + 0] = (a and 0xFF).toByte()
                rgba[tBase + 1] = ((a shr 8) and 0xFF).toByte()
                rgba[tBase + 2] = (b and 0xFF).toByte()
                rgba[tBase + 3] = ((b shr 8) and 0xFF).toByte()
            }
        }

        return SvdagCompiledChunk(
            zoneId = zoneId,
            rootNodeIndex = rootIdx,
            uniqueNodeCount = nodeCount,
            rawVoxelCount = solidCount,
            compressionRatio = solidCount.toFloat() / nodeCount.coerceAtLeast(1),
            packedRgbaBytes = rgba,
            texWidth = texWidth,
            texHeight = texHeight
        )
    }
}
