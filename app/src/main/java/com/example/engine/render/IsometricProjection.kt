package com.example.engine.render

import kotlin.math.sqrt

/**
 * Shared isometric camera basis and coordinate transforms for the SVDAG
 * raymarcher, LittleKt sprites, and Compose pointer input.
 *
 * The GLSL ray direction points from the camera into the scene. Its camera-up
 * vector is therefore cross(-rayDir, right), not cross(right, -rayDir).
 */
internal object IsometricProjection {
    const val CAMERA_TARGET_HEIGHT = 1.8f
    const val GROUND_PLANE_HEIGHT = 2.0f
    const val ZOOM = 11.5f

    internal data class Vec3(val x: Float, val y: Float, val z: Float) {
        fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
        fun length(): Float = sqrt(dot(this))
    }

    internal data class ScreenPoint(val x: Float, val y: Float)
    internal data class WorldPoint(val x: Float, val z: Float)

    private fun normalize(v: Vec3): Vec3 {
        val length = v.length().coerceAtLeast(1e-6f)
        return Vec3(v.x / length, v.y / length, v.z / length)
    }

    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

    // Keep the source directions in sync with SVDAG_FRAGMENT_SHADER.
    internal val rayDirection = normalize(Vec3(-1f, -1.25f, -1f))
    internal val right = normalize(Vec3(1f, 0f, -1f))
    internal val up = normalize(
        cross(
            Vec3(-rayDirection.x, -rayDirection.y, -rayDirection.z),
            right
        )
    )

    fun pixelsPerWorldUnit(viewHeight: Float): Float =
        (viewHeight.coerceAtLeast(1f) * 0.5f) / ZOOM

    /**
     * Projects a world point to LittleKt's GL-style viewport coordinates:
     * origin at bottom-left, positive Y upward.
     */
    fun worldToLittleKtX(
        worldX: Float,
        worldZ: Float,
        cameraX: Float,
        cameraZ: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Float {
        val scale = pixelsPerWorldUnit(viewHeight)
        val dx = worldX - cameraX
        val dz = worldZ - cameraZ
        return viewWidth.coerceAtLeast(1f) * 0.5f +
            (dx * right.x + dz * right.z) * scale
    }

    fun worldToLittleKtY(
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        cameraX: Float,
        cameraZ: Float,
        viewHeight: Float
    ): Float {
        val scale = pixelsPerWorldUnit(viewHeight)
        val dx = worldX - cameraX
        val dy = worldY - CAMERA_TARGET_HEIGHT
        val dz = worldZ - cameraZ
        return viewHeight.coerceAtLeast(1f) * 0.5f +
            (dx * up.x + dy * up.y + dz * up.z) * scale
    }

    fun worldToLittleKt(
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        cameraX: Float,
        cameraZ: Float,
        viewWidth: Float,
        viewHeight: Float
    ): ScreenPoint = ScreenPoint(
        worldToLittleKtX(worldX, worldZ, cameraX, cameraZ, viewWidth, viewHeight),
        worldToLittleKtY(worldX, worldY, worldZ, cameraX, cameraZ, viewHeight)
    )

    /** Projects a world point to Compose coordinates (origin at top-left). */
    fun worldToCompose(
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        cameraX: Float,
        cameraZ: Float,
        viewWidth: Float,
        viewHeight: Float
    ): ScreenPoint {
        val height = viewHeight.coerceAtLeast(1f)
        val glPoint = worldToLittleKt(
            worldX, worldY, worldZ, cameraX, cameraZ, viewWidth, height
        )
        return ScreenPoint(glPoint.x, height - glPoint.y)
    }

    /**
     * Unprojects a Compose point onto a horizontal world plane.
     * The plane height defaults to the arena floor.
     */
    fun composeToWorld(
        screenX: Float,
        screenY: Float,
        viewWidth: Float,
        viewHeight: Float,
        cameraX: Float,
        cameraZ: Float,
        planeHeight: Float = GROUND_PLANE_HEIGHT
    ): WorldPoint {
        val width = viewWidth.coerceAtLeast(1f)
        val height = viewHeight.coerceAtLeast(1f)
        val scale = pixelsPerWorldUnit(height)

        val projectedX = (screenX - width * 0.5f) / scale
        val glY = height - screenY
        val projectedY = (glY - height * 0.5f) / scale

        // Remove the vertical component contributed by the selected plane.
        val horizontalY = projectedY - (planeHeight - CAMERA_TARGET_HEIGHT) * up.y

        // Invert the 2x2 projection matrix for the world X/Z ground plane.
        val determinant = right.x * up.z - right.z * up.x
        val safeDeterminant = if (kotlin.math.abs(determinant) < 1e-6f) {
            error("Isometric camera basis is singular")
        } else {
            determinant
        }
        val dx = (projectedX * up.z - right.z * horizontalY) / safeDeterminant
        val dz = (right.x * horizontalY - projectedX * up.x) / safeDeterminant

        return WorldPoint(cameraX + dx, cameraZ + dz)
    }
}
