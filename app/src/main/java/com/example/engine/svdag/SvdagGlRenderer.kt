package com.example.engine.svdag

import android.opengl.GLES20
import android.util.Log
import com.example.model.SvdagQualityPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 / 2.0 SVDAG Isometric Raymarcher tuned for Exynos 7870 (ARM Mali-T830 MP1).
 * Uses a scalable offscreen FBO (0.50x / 0.75x / 1.00x) and a compact RGBA8 DAG lookup texture
 * to render detailed 3D voxelized gothic-industrial architecture with minimal memory bandwidth.
 */
class SvdagGlRenderer {

    @Volatile
    var lastGlError: String? = null
        private set

    private var svdagProgram = 0
    private var blitProgram = 0
    private var quadVbo = 0
    private var svdagTexId = 0
    private var fboId = 0
    private var fboColorTexId = 0
    private var fboWidth = 0
    private var fboHeight = 0

    private var currentChunk: SvdagCompiledChunk? = null
    private var currentPreset = SvdagQualityPreset.BALANCED
    private var initialized = false

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // x, y, u, v
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f
            ))
            position(0)
        }

    fun initIfNeeded() {
        if (initialized) return
        svdagProgram = createProgram(VERTEX_SHADER, SVDAG_FRAGMENT_SHADER)
        blitProgram = createProgram(VERTEX_SHADER, BLIT_FRAGMENT_SHADER)

        val bufs = IntArray(1)
        GLES20.glGenBuffers(1, bufs, 0)
        quadVbo = bufs[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4 * 4 * 4, quadVertices, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        svdagTexId = texs[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, svdagTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        initialized = true
    }

    fun uploadChunk(chunk: SvdagCompiledChunk) {
        initIfNeeded()
        if (currentChunk?.zoneId == chunk.zoneId) return
        currentChunk = chunk
        val buf = ByteBuffer.allocateDirect(chunk.packedRgbaBytes.size)
        buf.put(chunk.packedRgbaBytes)
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, svdagTexId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            chunk.texWidth,
            chunk.texHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buf
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    // REPAIR_HOOK: Точка рендерингу SVDAG у масштабований FBO (Exynos 7870 / Mali-T830) із перевіркою стану шейдерів та FBO
    fun renderBackdrop(
        viewportW: Int,
        viewportH: Int,
        camX: Float,
        camY: Float,
        timeSec: Float,
        isPaused: Boolean,
        preset: SvdagQualityPreset
    ) {
        initIfNeeded()
        val chunk = currentChunk ?: return
        currentPreset = preset

        if (svdagProgram == 0 || blitProgram == 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewportW, viewportH)
            GLES20.glClearColor(0.05f, 0.06f, 0.08f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        val targetW = (viewportW * preset.scale).toInt().coerceIn(240, 1920)
        val targetH = (viewportH * preset.scale).toInt().coerceIn(160, 1920)
        ensureFbo(targetW, targetH)
        if (fboId == 0) {
            // Безпечний фолбек при неповному FBO: рендер безпосередньо у головний буфер
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewportW, viewportH)
            GLES20.glClearColor(0.05f, 0.06f, 0.08f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        // Pass 1: Render SVDAG voxel environment into Mali-T830 scaled FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, targetW, targetH)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glUseProgram(svdagProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, svdagTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(svdagProgram, "uSvdagTex"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(svdagProgram, "uSvdagTexSize"), chunk.texWidth.toFloat(), chunk.texHeight.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(svdagProgram, "uRootNode"), chunk.rootNodeIndex.toFloat())
        GLES20.glUniform2f(GLES20.glGetUniformLocation(svdagProgram, "uResolution"), targetW.toFloat(), targetH.toFloat())
        GLES20.glUniform2f(GLES20.glGetUniformLocation(svdagProgram, "uCamCenter"), camX, camY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(svdagProgram, "uTime"), timeSec)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(svdagProgram, "uZoneIndex"), chunk.zoneId.index.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(svdagProgram, "uPaused"), if (isPaused) 1f else 0f)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(svdagProgram, "uMaxSteps"), preset.maxSteps)

        drawQuad(svdagProgram)

        // Pass 2: Upscale FBO cleanly to full screen framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportW, viewportH)
        GLES20.glUseProgram(blitProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboColorTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blitProgram, "uColorTex"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(blitProgram, "uPaused"), if (isPaused) 1f else 0f)
        drawQuad(blitProgram)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun drawQuad(program: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val uvLoc = GLES20.glGetAttribLocation(program, "aUv")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 16, 8)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun ensureFbo(w: Int, h: Int) {
        if (fboId != 0 && fboWidth == w && fboHeight == h) return
        if (fboId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES20.glDeleteTextures(1, intArrayOf(fboColorTexId), 0)
        }
        fboWidth = w
        fboHeight = h

        val fbos = IntArray(1)
        val texs = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        GLES20.glGenTextures(1, texs, 0)
        fboId = fbos[0]
        fboColorTexId = texs[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboColorTexId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboColorTexId, 0)
        val fbStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (fbStatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            val msg = "Неповний FBO для SVDAG (${w}x${h}): status=0x${Integer.toHexString(fbStatus)}"
            lastGlError = msg
            Log.e(TAG, msg)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES20.glDeleteTextures(1, intArrayOf(fboColorTexId), 0)
            fboId = 0
            fboColorTexId = 0
            fboWidth = 0
            fboHeight = 0
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun dispose() {
        if (!initialized) return
        if (fboId != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        if (fboColorTexId != 0) GLES20.glDeleteTextures(1, intArrayOf(fboColorTexId), 0)
        if (svdagTexId != 0) GLES20.glDeleteTextures(1, intArrayOf(svdagTexId), 0)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        if (svdagProgram != 0) GLES20.glDeleteProgram(svdagProgram)
        if (blitProgram != 0) GLES20.glDeleteProgram(blitProgram)
        fboId = 0
        fboColorTexId = 0
        svdagTexId = 0
        quadVbo = 0
        svdagProgram = 0
        blitProgram = 0
        fboWidth = 0
        fboHeight = 0
        currentChunk = null
        initialized = false
    }

    // REPAIR_HOOK: Діагностика компіляції та лінкування GLSL-шейдерів OpenGL ES
    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) {
            if (vs != 0) GLES20.glDeleteShader(vs)
            if (fs != 0) GLES20.glDeleteShader(fs)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (linked[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(prog)
            val msg = "Помилка лінкування OpenGL програми: $info"
            lastGlError = msg
            Log.e(TAG, msg)
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(s)
            val kind = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            val msg = "Помилка компіляції $kind шейдера: $info"
            lastGlError = msg
            Log.e(TAG, msg)
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }

    companion object {
        private const val TAG = "SvdagGlRenderer"
        private const val VERTEX_SHADER = """
            attribute vec2 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        private const val BLIT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uColorTex;
            uniform float uPaused;
            varying vec2 vUv;
            void main() {
                vec3 col = texture2D(uColorTex, vUv).rgb;
                // Subtle tactical pause chronometer desaturation & cyan rim vignette
                vec2 d = vUv - vec2(0.5);
                float vig = dot(d, d);
                if (uPaused > 0.5) {
                    float luma = dot(col, vec3(0.299, 0.587, 0.114));
                    col = mix(col, vec3(luma) * vec3(0.82, 0.96, 1.12), 0.35);
                    col = mix(col, vec3(0.18, 0.58, 0.68), smoothstep(0.18, 0.62, vig) * 0.45);
                } else {
                    col *= (1.0 - smoothstep(0.25, 0.72, vig) * 0.32);
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """

        // Compact SVDAG isometric voxel raymarcher optimized for Mali-T830 (Exynos 7870)
        private const val SVDAG_FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uSvdagTex;
            uniform vec2 uSvdagTexSize;
            uniform float uRootNode;
            uniform vec2 uResolution;
            uniform vec2 uCamCenter;
            uniform float uTime;
            uniform float uZoneIndex;
            uniform float uPaused;
            uniform int uMaxSteps;
            varying vec2 vUv;

            vec4 fetchSvdagTexel(int texelIdx) {
                float fi = float(texelIdx);
                float row = floor(fi / uSvdagTexSize.x);
                float col = fi - row * uSvdagTexSize.x;
                vec2 uv = (vec2(col, row) + 0.5) / uSvdagTexSize;
                return texture2D(uSvdagTex, uv) * 255.0 + 0.5;
            }

            // Traverses 5-level deduplicated SVDAG at integer voxel coordinate (0..31)
            int querySvdag(vec3 p) {
                if (p.x < 0.0 || p.x >= 32.0 || p.y < 0.0 || p.y >= 16.0 || p.z < 0.0 || p.z >= 32.0) {
                    return 0;
                }
                int node = int(uRootNode);
                float halfSize = 16.0;
                vec3 origin = vec3(0.0);

                for (int level = 0; level < 5; level++) {
                    if (node <= 0) return 0;
                    vec4 head = fetchSvdagTexel(node * 5);
                    int mask = int(head.r);

                    int bx = (p.x >= origin.x + halfSize) ? 1 : 0;
                    int by = (p.y >= origin.y + halfSize) ? 1 : 0;
                    int bz = (p.z >= origin.z + halfSize) ? 1 : 0;
                    int childBit = bx + by * 2 + bz * 4;

                    // Bit test without bitwise ops for strict GLSL ES 1.00 / Mali-T830 compatibility
                    float p2 = pow(2.0, float(childBit));
                    int bitOn = int(mod(floor(float(mask) / p2), 2.0));
                    if (bitOn == 0) return 0;

                    int pairIdx = childBit / 2;
                    vec4 pairTex = fetchSvdagTexel(node * 5 + 1 + pairIdx);
                    int childVal = (childBit - pairIdx * 2 == 0)
                        ? int(pairTex.r) + int(pairTex.g) * 256
                        : int(pairTex.b) + int(pairTex.a) * 256;

                    if (level == 4) {
                        return childVal;
                    }
                    origin += vec3(float(bx), float(by), float(bz)) * halfSize;
                    halfSize *= 0.5;
                    node = childVal;
                }
                return 0;
            }

            vec3 shadeMaterial(int mat, vec3 wp, vec3 nrm) {
                float checker = mod(floor(wp.x) + floor(wp.z), 2.0);
                float grain = fract(sin(dot(floor(wp * 2.0), vec3(12.9898, 78.233, 37.719))) * 43758.5453);
                vec3 base = vec3(0.22, 0.23, 0.25);
                if (mat == 1) { // Cobblestone
                    base = mix(vec3(0.19, 0.20, 0.23), vec3(0.26, 0.25, 0.24), checker * 0.45 + grain * 0.35);
                } else if (mat == 2) { // Dark Brick
                    base = mix(vec3(0.24, 0.16, 0.14), vec3(0.32, 0.21, 0.17), grain * 0.5);
                } else if (mat == 3) { // Rust Iron
                    base = mix(vec3(0.18, 0.19, 0.21), vec3(0.34, 0.20, 0.13), grain * 0.65);
                } else if (mat == 4) { // Brass Pipe
                    base = mix(vec3(0.44, 0.32, 0.16), vec3(0.66, 0.50, 0.24), grain * 0.4);
                } else if (mat == 5) { // Aether Vat
                    float pulse = 0.75 + 0.25 * sin(uTime * 3.0 + wp.y);
                    base = vec3(0.16, 0.62, 0.72) * pulse;
                } else if (mat == 6) { // Canal Water
                    float ripple = 0.85 + 0.15 * sin(wp.x * 2.0 + uTime * 2.2) * cos(wp.z * 2.0 - uTime * 1.7);
                    base = vec3(0.08, 0.17, 0.23) * ripple;
                } else if (mat == 7) { // Rustwood Bark
                    base = mix(vec3(0.18, 0.14, 0.11), vec3(0.27, 0.21, 0.15), grain * 0.5);
                } else if (mat == 8) { // Toxic Moss
                    base = mix(vec3(0.14, 0.26, 0.15), vec3(0.22, 0.38, 0.18), grain * 0.5);
                }

                // Directional gothic furnace light + subtle sky fill
                vec3 lightDir = normalize(vec3(0.55, 0.82, 0.42));
                float ndl = max(dot(nrm, lightDir), 0.18);
                // Ambient occlusion near wall bases
                float ao = clamp(0.62 + wp.y * 0.06, 0.55, 1.0);
                // Warm furnace rim glow
                float ember = max(0.0, 1.0 - length(wp.xz - uCamCenter) * 0.09);
                vec3 lit = base * (ndl * ao * 1.18) + vec3(0.24, 0.11, 0.05) * ember * 0.38;
                return lit;
            }

            void main() {
                float aspect = uResolution.x / max(uResolution.y, 1.0);
                vec2 ndc = vUv * 2.0 - 1.0;
                ndc.x *= aspect;

                // Strictly orthonormal isometric camera centered on hero
                vec3 right = normalize(vec3(1.0, 0.0, -1.0));
                vec3 rayDir = normalize(vec3(-1.0, -1.25, -1.0));
                vec3 up = normalize(cross(right, -rayDir));

                vec3 target = vec3(uCamCenter.x, 1.8, uCamCenter.y);
                float zoom = 11.5;
                vec3 rayOrigin = target + right * (ndc.x * zoom) + up * (ndc.y * zoom) - rayDir * 24.0;

                // Intersect arena AABB [0..32, 0..16, 0..32]
                vec3 invD = 1.0 / rayDir;
                vec3 t0s = (vec3(0.0, 0.0, 0.0) - rayOrigin) * invD;
                vec3 t1s = (vec3(32.0, 16.0, 32.0) - rayOrigin) * invD;
                float tMin = max(max(min(t0s.x, t1s.x), min(t0s.y, t1s.y)), min(t0s.z, t1s.z));
                float tMax = min(min(max(t0s.x, t1s.x), max(t0s.y, t1s.y)), max(t0s.z, t1s.z));

                vec3 sky = mix(vec3(0.05, 0.06, 0.08), vec3(0.11, 0.13, 0.16), clamp(vUv.y, 0.0, 1.0));
                if (uZoneIndex > 3.5) {
                    sky = mix(vec3(0.05, 0.08, 0.07), vec3(0.10, 0.15, 0.12), clamp(vUv.y, 0.0, 1.0));
                }

                if (tMax <= max(tMin, 0.0)) {
                    gl_FragColor = vec4(sky, 1.0);
                    return;
                }

                float t = max(tMin, 0.0) + 0.02;
                vec3 hitColor = sky;

                for (int i = 0; i < 48; i++) {
                    if (i >= uMaxSteps || t > tMax) break;
                    vec3 pos = rayOrigin + rayDir * t;
                    int mat = querySvdag(pos);
                    if (mat > 0) {
                        // Refine face normal from local voxel center
                        vec3 cellFrac = fract(pos) - 0.5;
                        vec3 aFrac = abs(cellFrac);
                        vec3 nrm = vec3(0.0, 1.0, 0.0);
                        if (aFrac.x >= aFrac.y && aFrac.x >= aFrac.z) {
                            nrm = vec3(sign(cellFrac.x), 0.0, 0.0);
                        } else if (aFrac.z >= aFrac.x && aFrac.z >= aFrac.y) {
                            nrm = vec3(0.0, 0.0, sign(cellFrac.z));
                        } else {
                            nrm = vec3(0.0, sign(cellFrac.y), 0.0);
                        }
                        hitColor = shadeMaterial(mat, pos, nrm);
                        break;
                    }
                    t += (pos.y > 8.5) ? 0.90 : 0.56;
                }

                // Subtle isometric combat arena grid lines on floor
                gl_FragColor = vec4(hitColor, 1.0);
            }
        """
    }
}
