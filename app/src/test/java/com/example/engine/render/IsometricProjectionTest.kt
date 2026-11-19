package com.example.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IsometricProjectionTest {

    @Test
    fun cameraBasisIsOrthonormalAndUsesShaderCameraHandedness() {
        val right = IsometricProjection.right
        val ray = IsometricProjection.rayDirection
        val up = IsometricProjection.up

        assertEquals(1f, right.length(), 1e-5f)
        assertEquals(1f, ray.length(), 1e-5f)
        assertEquals(1f, up.length(), 1e-5f)
        assertEquals(0f, right.dot(ray), 1e-5f)
        assertEquals(0f, right.dot(up), 1e-5f)
        assertEquals(0f, ray.dot(up), 1e-5f)

        // This is the orientation produced by cross(-rayDirection, right).
        assertTrue(up.x < 0f)
        assertTrue(up.y > 0f)
        assertTrue(up.z < 0f)
        assertEquals(-0.4682929f, up.x, 1e-5f)
        assertEquals(0.7492686f, up.y, 1e-5f)
        assertEquals(-0.4682929f, up.z, 1e-5f)
    }

    @Test
    fun worldComposeAndGroundUnprojectionRoundTripAcrossViewports() {
        val viewports = listOf(
            360f to 800f,
            1280f to 720f,
            2400f to 1080f,
            1080f to 2400f
        )
        val cameraCenters = listOf(
            16f to 16f,
            23.25f to 9.5f,
            7.75f to 25.125f
        )
        val worldPoints = listOf(
            16f to 16f,
            4.5f to 27.5f,
            27.5f to 4.5f,
            11.25f to 19.75f,
            25.125f to 8.625f
        )

        for ((width, height) in viewports) {
            for ((cameraX, cameraZ) in cameraCenters) {
                for ((worldX, worldZ) in worldPoints) {
                    val screen = IsometricProjection.worldToCompose(
                        worldX, IsometricProjection.GROUND_PLANE_HEIGHT, worldZ,
                        cameraX, cameraZ, width, height
                    )
                    val world = IsometricProjection.composeToWorld(
                        screen.x, screen.y, width, height, cameraX, cameraZ
                    )

                    assertEquals(
                        "world X round-trip at viewport ${width}x${height}",
                        worldX, world.x, 1e-4f
                    )
                    assertEquals(
                        "world Z round-trip at viewport ${width}x${height}",
                        worldZ, world.z, 1e-4f
                    )
                }
            }
        }
    }

    @Test
    fun unprojectionAccountsForTheSelectedHorizontalPlaneHeight() {
        val width = 1080f
        val height = 2400f
        val cameraX = 17.5f
        val cameraZ = 12.25f
        val worldX = 21.75f
        val worldZ = 6.5f
        val planeHeights = listOf(1.8f, 2f, 3.2f, 4.75f)

        for (planeY in planeHeights) {
            val screen = IsometricProjection.worldToCompose(
                worldX, planeY, worldZ, cameraX, cameraZ, width, height
            )
            val world = IsometricProjection.composeToWorld(
                screen.x, screen.y, width, height, cameraX, cameraZ, planeY
            )
            assertEquals(worldX, world.x, 1e-4f)
            assertEquals(worldZ, world.z, 1e-4f)
        }
    }

    @Test
    fun higherWorldPointsProjectHigherOnTheScreen() {
        val low = IsometricProjection.worldToCompose(
            16f, 2f, 16f, 16f, 16f, 1080f, 1920f
        )
        val high = IsometricProjection.worldToCompose(
            16f, 3f, 16f, 16f, 16f, 1080f, 1920f
        )
        assertTrue("higher world Y must reduce top-down screen Y", high.y < low.y)
    }

    @Test
    fun horizontalScaleDependsOnViewportHeightNotWidth() {
        val cameraX = 16f
        val cameraZ = 16f
        val pointX = 18f
        val pointZ = 16f
        val wide = IsometricProjection.worldToLittleKt(
            pointX, 2f, pointZ, cameraX, cameraZ, 2400f, 1080f
        )
        val narrow = IsometricProjection.worldToLittleKt(
            pointX, 2f, pointZ, cameraX, cameraZ, 360f, 1080f
        )

        val expectedOffset =
            ((pointX - cameraX) * IsometricProjection.right.x +
                (pointZ - cameraZ) * IsometricProjection.right.z) *
                IsometricProjection.pixelsPerWorldUnit(1080f)
        assertEquals(expectedOffset, wide.x - 1200f, 1e-3f)
        assertEquals(expectedOffset, narrow.x - 180f, 1e-3f)
        assertTrue(abs(wide.y - narrow.y) < 1e-4f)
    }
}
