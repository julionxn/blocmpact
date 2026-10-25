package me.julionxn.blocmpact.client.impl.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.post.ImpactFrame;
import me.julionxn.blocmpact.client.api.post.PostPass;
import me.julionxn.blocmpact.client.api.post.ScreenFx;
import me.julionxn.blocmpact.client.api.post.Uniforms;
import me.julionxn.blocmpact.client.impl.BlocmpactConfig;
import me.julionxn.blocmpact.client.impl.FailureLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The level-3 engine. Per frame: a copy of Minecraft's image, effect passes in ping-pong at a dynamic resolution
 * (plus named and persistent targets), a glow pyramid of the light the effects added, one compositor pass with the
 * camera operations, optional full-resolution overlay passes, and the result written back into Minecraft's color
 * texture. Runs at the end of the level render (before the hand and the GUI). OpenGL only; everything is guarded so a
 * failure disables the chain instead of the game.
 */
public final class PostChain {
    public static final PostChain INSTANCE = new PostChain();
    public static final Identifier FINISH = Blocmpact.id("internal/finish"), GLOW_REDUCE = Blocmpact.id("internal/glow_reduce"),
        GLOW_EXPAND = Blocmpact.id("internal/glow_expand");
    private static final int UNIT_DIFFUSE = 0, UNIT_DEPTH = 1, UNIT_SCENE = 2, FIRST_FREE_UNIT = 4, MAX_UNITS = 16;
    private static final int HISTORY_SIZE = 64, HISTORY_KEEP_FRAMES = 600;
    private static final long HISTORY_INTERVAL_NS = 40_000_000L;

    record Target(int tex, int fbo, int w, int h, int format) {
        void delete() {
            GL30.glDeleteFramebuffers(fbo);
            GL11.glDeleteTextures(tex);
        }
    }

    private static final class Named {
        Target front, back;
        boolean persistent, hdr;
        int lastFrame;

        void swap() {
            Target t = front;
            front = back;
            back = t;
        }

        void delete() {
            if (front != null) front.delete();
            if (back != null) back.delete();
        }
    }

    private final Map<String, FxProgram> programs = new HashMap<>();
    private final Set<String> broken = new HashSet<>();
    private final Map<String, Named> named = new HashMap<>();
    private final GlUniforms uniforms = new GlUniforms();
    private final GpuTimer timer = new GpuTimer();
    private final Deque<WarmTask> warmQueue = new ArrayDeque<>();
    private boolean checked, disabled, warmQueued, heldValid, available;
    private float zZeroToOne;
    private long lastHeld, lastHistoryWrite;
    private int vao, linearClamp, nearestClamp, linearRepeat, nearestRepeat, mainFbo, mainKey, width, height, effW, effH, frameCounter;
    private int historyLastRequest = Integer.MIN_VALUE / 2, historyCursor;
    private Target sceneCopy, held, ping, pong, sceneReduced, finalA, finalB, warmA, warmB, warmSrc;
    private final Target[] glowDown = new Target[5], glowUp = new Target[4], history = new Target[HISTORY_SIZE];
    private final long[] historyTime = new long[HISTORY_SIZE];
    private String lastReport = "";

    private record WarmTask(Identifier program, Map<String, String> defines) {
    }

    // ------------------------------------------------------------------ public entry

    /** True once the backend was checked and is OpenGL with everything the engine needs. */
    public boolean available() {
        return checked && available && !disabled;
    }

    public float resolutionFactor() {
        return timer.factor();
    }

    public float gpuMs() {
        return timer.lastMs();
    }

    public String status() {
        if (!checked) return "not started";
        if (disabled) return "disabled (" + lastReport + ")";
        return String.format(Locale.ROOT, "%dx%d @%.2f, %.2f ms, %d programs", effW, effH, timer.factor(), timer.lastMs(), programs.size());
    }

    public void render(Frame f, List<PostPass> passes, ScreenFx s) {
        if (!BlocmpactConfig.get().postProcessing) return;
        if (disabled) return;
        boolean historyActive = frameCounter - historyLastRequest < HISTORY_KEEP_FRAMES;
        frameCounter++;
        if (!checked && !checkBackend()) return;
        if (passes.isEmpty() && !s.active() && !historyActive) {
            heldValid = false;
            releaseUnused();
            warmupStep();
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (!(main.getColorTexture() instanceof GlTexture color) || !(main.getDepthTexture() instanceof GlTexture depth)) return;
        RenderSystem.assertOnRenderThread();
        GlState saved = GlState.capture();
        try {
            GlState.prepareForPasses();
            ensureCommon();
            ensureFull(main.width, main.height);
            attachMain(color.glId());
            GL30.glBindVertexArray(vao);
            long now = System.nanoTime();
            float holdFps = BlocmpactConfig.get().heldFrames ? s.holdFps : 0;
            if (holdFps > 0 && heldValid && now - lastHeld < 1e9 / holdFps) {
                blit(held.fbo, mainFbo, width, height, width, height, GL11.GL_NEAREST);
                return;
            }
            timer.poll(BlocmpactConfig.get().gpuBudgetMs);
            timer.begin();
            draw(f, passes, s, depth.glId());
            timer.end();
            if (holdFps > 0) {
                blit(mainFbo, held.fbo, width, height, width, height, GL11.GL_NEAREST);
                lastHeld = now;
                heldValid = true;
            } else heldValid = false;
            if (historyActive && now - lastHistoryWrite >= HISTORY_INTERVAL_NS) recordHistory(now);
            releaseUnused();
        } catch (RuntimeException e) {
            disabled = true;
            lastReport = e.toString();
            FailureLog.report("post-processing disabled after an error", e.toString(), e);
        } finally {
            saved.restore();
        }
    }

    // ------------------------------------------------------------------ frame

    private void draw(Frame f, List<PostPass> passes, ScreenFx s, int depthTex) {
        blit(mainFbo, sceneCopy.fbo, width, height, width, height, GL11.GL_NEAREST);
        BlocmpactConfig cfg = BlocmpactConfig.get();
        float scale = Math.min(1f, (float) Math.sqrt(cfg.pixelBudget * 1e6f * timer.factor() / ((float) width * height)));
        ensureEffect(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
        uniforms.frameSetup(f, depthTex);

        List<PostPass> scenePasses = new ArrayList<>(), overlayPasses = new ArrayList<>();
        for (PostPass p : passes) (p.stage() == PostPass.Stage.OVERLAY ? overlayPasses : scenePasses).add(p);

        int input = sceneCopy.tex, n = 0;
        for (PostPass pass : scenePasses) {
            FxProgram p = program(pass.program(), pass.defines());
            if (p == null) continue;
            if (pass.output() != null) {
                Named t = namedTarget(pass, effW, effH);
                runPass(p, pass, t.back, input, effW, effH);
                t.swap();
            } else {
                Target out = (n++ & 1) == 0 ? ping : pong;
                runPass(p, pass, out, input, effW, effH);
                input = out.tex;
            }
        }

        int glowTex = sceneCopy.tex, floodTex = sceneCopy.tex;
        float glowStrength = 0f;
        FxProgram reduce = program(GLOW_REDUCE, Map.of()), expand = program(GLOW_EXPAND, Map.of());
        if (reduce != null && expand != null && s.bloom > 0f) {
            // Down the pyramid (the first step keeps only the added light), then back up adding each level.
            int src = input, sw = input == sceneCopy.tex ? width : effW, sh = input == sceneCopy.tex ? height : effH;
            for (int i = 0; i < glowDown.length; i++) {
                begin(reduce, glowDown[i]);
                bind(reduce, "DiffuseSampler", UNIT_DIFFUSE, src, linearClamp);
                bind(reduce, "SceneSampler", UNIT_SCENE, sceneCopy.tex, linearClamp);
                uniforms.use(reduce, glowDown[i].w, glowDown[i].h);
                uniforms.f1("ExtractAdded", i == 0 ? 1f : 0f);
                uniforms.f1("ThresholdBloom", s.thresholdBloom);
                uniforms.f2("SrcTexel", 1f / sw, 1f / sh);
                draw3();
                src = glowDown[i].tex;
                sw = glowDown[i].w;
                sh = glowDown[i].h;
            }
            float spread = Math.max(0.3f, Math.min(1.2f, 0.35f + 0.4f * s.bloomRadius));
            for (int i = glowUp.length - 1; i >= 0; i--) {
                begin(expand, glowUp[i]);
                bind(expand, "DiffuseSampler", UNIT_DIFFUSE, src, linearClamp);
                bind(expand, "LevelSampler", 3, glowDown[i].tex, linearClamp);
                uniforms.use(expand, glowUp[i].w, glowUp[i].h);
                uniforms.f2("SrcTexel", 1f / sw, 1f / sh);
                uniforms.f1("Spread", spread);
                draw3();
                src = glowUp[i].tex;
                sw = glowUp[i].w;
                sh = glowUp[i].h;
            }
            glowTex = glowUp[0].tex;
            floodTex = glowDown[glowDown.length - 1].tex;
            glowStrength = s.bloom;
        }

        boolean low = effW < width && input != sceneCopy.tex;
        if (low) blit(sceneCopy.fbo, sceneReduced.fbo, width, height, effW, effH, GL11.GL_LINEAR);

        FxProgram c = program(FINISH, Map.of());
        if (c == null) return;
        boolean overlays = !overlayPasses.isEmpty();
        if (overlays) ensureFinal();
        int finishFbo = overlays ? finalA.fbo : mainFbo;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, finishFbo);
        GL11.glViewport(0, 0, width, height);
        GL20.glUseProgram(c.id);
        bind(c, "DiffuseSampler", UNIT_DIFFUSE, input, linearClamp);
        bind(c, "DepthSampler", UNIT_DEPTH, depthTex, nearestClamp);
        bind(c, "SceneSampler", UNIT_SCENE, sceneCopy.tex, linearClamp);
        bind(c, "ReducedSceneSampler", 3, low ? sceneReduced.tex : sceneCopy.tex, linearClamp);
        bind(c, "GlowSampler", 4, glowTex, linearClamp);
        bind(c, "FloodSampler", 5, floodTex, linearClamp);
        uniforms.use(c, width, height);
        finishUniforms(s, input == sceneCopy.tex ? width : effW, input == sceneCopy.tex ? height : effH, low, glowStrength);
        draw3();

        if (overlays) {
            int in = finalA.tex;
            int m = 0;
            for (PostPass pass : overlayPasses) {
                FxProgram p = program(pass.program(), pass.defines());
                if (p == null) continue;
                if (pass.output() != null) {
                    Named t = namedTarget(pass, width, height);
                    runPass(p, pass, t.back, in, width, height);
                    t.swap();
                } else {
                    Target out = (m++ & 1) == 0 ? finalB : finalA;
                    runPass(p, pass, out, in, width, height);
                    in = out.tex;
                }
            }
            Target last = in == finalA.tex ? finalA : finalB;
            blit(last.fbo, mainFbo, width, height, width, height, GL11.GL_NEAREST);
        }
    }

    private void runPass(FxProgram p, PostPass pass, Target out, int input, int baseW, int baseH) {
        begin(p, out);
        bind(p, "DiffuseSampler", UNIT_DIFFUSE, input, linearClamp);
        bind(p, "DepthSampler", UNIT_DEPTH, uniforms.depthTex, nearestClamp);
        bind(p, "SceneSampler", UNIT_SCENE, sceneCopy.tex, linearClamp);
        uniforms.use(p, out.w, out.h);
        uniforms.common();
        try {
            pass.setup().accept(uniforms);
        } catch (RuntimeException e) {
            FailureLog.report("uniform setup of " + pass.program() + " failed", e.toString(), e);
        }
        draw3();
    }

    private void finishUniforms(ScreenFx s, int effectW, int effectH, boolean low, float glowStrength) {
        BlocmpactConfig cfg = BlocmpactConfig.get();
        GlUniforms u = uniforms;
        u.f2("ScreenSize", width, height);
        u.f2("OutSize", width, height);
        u.f2("EffectSize", effectW, effectH);
        u.f1("GameTime", u.time);
        u.f1("DetailTransfer", low ? 1f : 0f);
        u.f2("Shake", s.shake * cfg.shakeScale, s.shakeFrequency);
        u.f3("Aberration", s.aberration, s.aberrationX, s.aberrationY);
        u.f3("ZoomBlur", s.zoomBlur, s.zoomX, s.zoomY);
        u.f3("Zoom", s.zoom, s.zoomCenterX, s.zoomCenterY);
        u.f2("Glow", glowStrength, s.adaptation);
        u.f4("Grade1", s.exposure, s.saturation, s.contrast, s.contrastPivot);
        u.f4("Grade2", s.gamma, s.hueShift, s.keepColor, s.keepColorThreshold);
        u.f3("Tint", s.tintR, s.tintG, s.tintB);
        u.f3("Lift", s.liftR, s.liftG, s.liftB);
        u.f3("Gray", s.grayR, s.grayG, s.grayB);
        u.f4("Vignette", s.vignetteR, s.vignetteG, s.vignetteB, s.vignette);
        u.f2("VignetteShape", s.vignetteInner, s.vignetteOuter);
        u.f4("Flash", s.flashR, s.flashG, s.flashB, s.flash * cfg.flashScale);
        u.f4("Fade", s.fadeR, s.fadeG, s.fadeB, s.fade);
        u.f2("Grain", s.grain, s.grainFps);
        ImpactFrame im = cfg.impactFrames ? s.impact : null;
        if (im == null) {
            u.f4("Impact", -1f, 0.5f, 0.5f, 0f);
        } else {
            u.f4("Impact", im.mode().ordinal(), s.impactX, s.impactY, s.impactTime);
            u.f4("ImpactPaper", r(im.paper()), g(im.paper()), b(im.paper()), a(im.paper()));
            u.f4("ImpactInk", r(im.ink()), g(im.ink()), b(im.ink()), a(im.ink()));
            u.f4("ImpactHigh", r(im.highlight()), g(im.highlight()), b(im.highlight()), a(im.highlight()));
            u.f4("ImpactParams", im.bias(), im.edges(), im.cell(), im.glitch());
            u.f2("ImpactParams2", im.spin(), im.invert() ? 1f : 0f);
        }
    }

    private static float r(int c) {
        return (c >> 16 & 255) / 255f;
    }

    private static float g(int c) {
        return (c >> 8 & 255) / 255f;
    }

    private static float b(int c) {
        return (c & 255) / 255f;
    }

    private static float a(int c) {
        return (c >>> 24) / 255f;
    }

    // ------------------------------------------------------------------ named targets and history

    private Named namedTarget(PostPass pass, int baseW, int baseH) {
        int w = Math.max(1, Math.round(baseW * pass.scale())), h = Math.max(1, Math.round(baseH * pass.scale()));
        Named t = named.get(pass.output());
        if (t != null && (t.front.w != w || t.front.h != h || t.hdr != pass.hdr())) {
            t.delete();
            t = null;
        }
        if (t == null) {
            t = new Named();
            int format = pass.hdr() ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
            t.front = target(w, h, format);
            t.back = target(w, h, format);
            t.hdr = pass.hdr();
            clear(t.front);
            clear(t.back);
            named.put(pass.output(), t);
        }
        t.persistent |= pass.persistent();
        t.lastFrame = frameCounter;
        return t;
    }

    int namedTexture(String name) {
        Named t = named.get(name);
        if (t == null) return 0;
        t.lastFrame = frameCounter;
        return t.front.tex;
    }

    int historyTexture(float secondsAgo) {
        historyLastRequest = frameCounter;
        long want = System.nanoTime() - (long) (Math.max(0, secondsAgo) * 1e9);
        int best = -1;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (history[i] == null || historyTime[i] == 0) continue;
            long d = Math.abs(historyTime[i] - want);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best < 0 ? sceneCopy.tex : history[best].tex;
    }

    private void recordHistory(long now) {
        int w = Math.max(1, width / 4), h = Math.max(1, height / 4);
        Target t = history[historyCursor];
        if (t != null && (t.w != w || t.h != h)) {
            t.delete();
            t = null;
        }
        if (t == null) history[historyCursor] = t = target(w, h, GL11.GL_RGBA8);
        blit(mainFbo, t.fbo, width, height, w, h, GL11.GL_LINEAR);
        historyTime[historyCursor] = now;
        historyCursor = (historyCursor + 1) % HISTORY_SIZE;
        lastHistoryWrite = now;
    }

    private void releaseUnused() {
        if ((frameCounter & 63) != 0) return;
        Iterator<Map.Entry<String, Named>> it = named.entrySet().iterator();
        while (it.hasNext()) {
            Named t = it.next().getValue();
            int idle = frameCounter - t.lastFrame;
            if (idle > (t.persistent ? 600 : 120)) {
                t.delete();
                it.remove();
            }
        }
        if (frameCounter - historyLastRequest >= HISTORY_KEEP_FRAMES) {
            for (int i = 0; i < HISTORY_SIZE; i++) {
                if (history[i] != null) {
                    history[i].delete();
                    history[i] = null;
                    historyTime[i] = 0;
                }
            }
        }
    }

    // ------------------------------------------------------------------ programs and warmup

    private static String key(Identifier program, Map<String, String> defines) {
        return defines.isEmpty() ? program.toString() : program + defines.toString();
    }

    FxProgram program(Identifier program, Map<String, String> defines) {
        String k = key(program, defines);
        FxProgram p = programs.get(k);
        if (p != null || broken.contains(k)) return p;
        try {
            p = FxProgram.compile(program, defines);
            programs.put(k, p);
            return p;
        } catch (IOException | RuntimeException e) {
            broken.add(k);
            FailureLog.report("shader " + program + " does not compile", e.getMessage());
            return null;
        }
    }

    /** Queues every {@code fx/**.fsh} found in the loaded resources for progressive precompilation. */
    private void queueWarmup() {
        warmQueued = true;
        Set<Identifier> ids = new LinkedHashSet<>(List.of(FINISH, GLOW_REDUCE, GLOW_EXPAND));
        try {
            Minecraft.getInstance().getResourceManager().listResources("fx", id -> id.getPath().endsWith(".fsh")).keySet().forEach(id -> {
                String path = id.getPath();
                ids.add(Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(3, path.length() - 4)));
            });
        } catch (RuntimeException e) {
            Blocmpact.LOGGER.warn("Could not list effect shaders for warmup", e);
        }
        for (Identifier id : ids) warmQueue.add(new WarmTask(id, Map.of()));
    }

    /** Compiles and draws queued programs once into 1×1 targets, within the per-frame time budget. */
    private void warmupStep() {
        if (!available() || !BlocmpactConfig.get().postProcessing) return;
        if (!warmQueued) queueWarmup();
        if (warmQueue.isEmpty()) return;
        long budget = (long) (BlocmpactConfig.get().warmupMsPerFrame * 1e6), start = System.nanoTime();
        GlState saved = GlState.capture();
        try {
            GlState.prepareForPasses();
            ensureCommon();
            GL30.glBindVertexArray(vao);
            if (warmSrc == null) {
                warmSrc = target(1, 1, GL11.GL_RGBA8);
                warmA = target(1, 1, GL11.GL_RGBA8);
                warmB = target(1, 1, GL30.GL_RGBA16F);
            }
            while (!warmQueue.isEmpty() && System.nanoTime() - start < budget) {
                WarmTask t = warmQueue.poll();
                FxProgram p = program(t.program(), t.defines());
                if (p == null) continue;
                for (Target out : new Target[]{warmA, warmB}) {
                    begin(p, out);
                    bind(p, "DiffuseSampler", UNIT_DIFFUSE, warmSrc.tex, linearClamp);
                    bind(p, "SceneSampler", UNIT_SCENE, warmSrc.tex, linearClamp);
                    draw3();
                }
            }
            if (warmQueue.isEmpty()) {
                GL11.glFinish();
                warmSrc.delete();
                warmA.delete();
                warmB.delete();
                warmSrc = warmA = warmB = null;
            }
        } catch (RuntimeException e) {
            disabled = true;
            lastReport = e.toString();
            FailureLog.report("shader warmup failed", e.toString(), e);
        } finally {
            saved.restore();
        }
    }

    /** Resource reload (F3+T): drop compiled programs so edited shaders are rebuilt, and warm up again. */
    public void invalidate() {
        Minecraft.getInstance().execute(() -> {
            programs.values().forEach(FxProgram::delete);
            programs.clear();
            broken.clear();
            warmQueue.clear();
            warmQueued = false;
            if (!lastReport.isEmpty() && checked && available) {
                disabled = false;
                lastReport = "";
            }
        });
    }

    // ------------------------------------------------------------------ checks and resources

    private boolean checkBackend() {
        checked = true;
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        if (!"OpenGL".equalsIgnoreCase(info.backendName())) {
            available = false;
            disabled = true;
            lastReport = info.backendName();
            FailureLog.report("Full-screen effects need OpenGL (Video Settings > Graphics API > Prefer OpenGL); using fallbacks",
                "backend " + info.backendName());
            return false;
        }
        String renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase(Locale.ROOT);
        if (renderer.contains("llvmpipe") || renderer.contains("swiftshader") || renderer.contains("lavapipe")
            || renderer.contains("microsoft basic render")) {
            available = false;
            disabled = true;
            lastReport = renderer;
            FailureLog.report("Full-screen effects disabled on a software renderer", renderer);
            return false;
        }
        if (GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) < MAX_UNITS) {
            available = false;
            disabled = true;
            lastReport = "texture units";
            FailureLog.report("Full-screen effects need 16 texture units", renderer);
            return false;
        }
        zZeroToOne = info.isZZeroToOne() ? 1f : 0f;
        available = true;
        Blocmpact.LOGGER.info("[Blocmpact] post-processing on {} ({})", GL11.glGetString(GL11.GL_RENDERER), info.backendName());
        return true;
    }

    private void ensureCommon() {
        if (vao != 0) return;
        vao = GL30.glGenVertexArrays();
        linearClamp = sampler(GL11.GL_LINEAR, GL12.GL_CLAMP_TO_EDGE);
        nearestClamp = sampler(GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE);
        linearRepeat = sampler(GL11.GL_LINEAR, GL11.GL_REPEAT);
        nearestRepeat = sampler(GL11.GL_NEAREST, GL11.GL_REPEAT);
        mainFbo = GL30.glGenFramebuffers();
    }

    /** Minecraft filters with sampler objects: texture parameters are not reliable, bind our own samplers. */
    private static int sampler(int filter, int wrap) {
        int s = GL33.glGenSamplers();
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_T, wrap);
        GL33.glSamplerParameteri(s, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        return s;
    }

    /** Every frame: after a resize GL may reuse the same id for the new texture. */
    private void attachMain(int colorId) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
        int k = colorId * 31 + width * 17 + height;
        if (k != mainKey && GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("main framebuffer incomplete");
        mainKey = k;
    }

    private void ensureFull(int w, int h) {
        if (sceneCopy != null && w == width && h == height) return;
        if (sceneCopy != null) {
            sceneCopy.delete();
            held.delete();
            if (finalA != null) {
                finalA.delete();
                finalB.delete();
                finalA = finalB = null;
            }
            for (int i = 0; i < HISTORY_SIZE; i++) {
                if (history[i] != null) history[i].delete();
                history[i] = null;
                historyTime[i] = 0;
            }
        }
        width = w;
        height = h;
        effW = effH = 0;
        heldValid = false;
        sceneCopy = target(w, h, GL11.GL_RGBA8);
        held = target(w, h, GL11.GL_RGBA8);
    }

    private void ensureFinal() {
        if (finalA != null) return;
        finalA = target(width, height, GL11.GL_RGBA8);
        finalB = target(width, height, GL11.GL_RGBA8);
    }

    private void ensureEffect(int w, int h) {
        if (ping != null && w == effW && h == effH) return;
        if (ping != null) {
            ping.delete();
            pong.delete();
            sceneReduced.delete();
            for (Target t : glowDown) t.delete();
            for (Target t : glowUp) t.delete();
        }
        effW = w;
        effH = h;
        ping = target(w, h, GL11.GL_RGBA8);
        pong = target(w, h, GL11.GL_RGBA8);
        sceneReduced = target(w, h, GL11.GL_RGBA8);
        for (int i = 0, bw = w, bh = h; i < 5; i++) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            glowDown[i] = target(bw, bh, GL30.GL_RGBA16F);
            if (i < 4) glowUp[i] = target(bw, bh, GL30.GL_RGBA16F);
        }
    }

    static Target target(int w, int h, int format) {
        int tex = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int type = format == GL30.GL_RGBA16F ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(fbo);
            GL11.glDeleteTextures(tex);
            throw new IllegalStateException("incomplete framebuffer, format 0x" + Integer.toHexString(format));
        }
        return new Target(tex, fbo, w, h, format);
    }

    private static void clear(Target t) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, t.fbo);
        GL11.glViewport(0, 0, t.w, t.h);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void begin(FxProgram p, Target out) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, out.fbo);
        GL11.glViewport(0, 0, out.w, out.h);
        GL20.glUseProgram(p.id);
    }

    private static void bind(FxProgram p, String uniform, int unit, int tex, int sampler) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL33.glBindSampler(unit, sampler);
        int l = p.location(uniform);
        if (l >= 0) GL20.glUniform1i(l, unit);
    }

    private static void blit(int from, int to, int sw, int sh, int dw, int dh, int filter) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GL30.glBlitFramebuffer(0, 0, sw, sh, 0, 0, dw, dh, GL11.GL_COLOR_BUFFER_BIT, filter);
    }

    private static void draw3() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** In 26.x with OpenGL the depth is reversed (sky = 0). Detect it from the projection instead of assuming. */
    private static boolean isReverseZ(Matrix4fc proj) {
        Vector4f a = proj.transform(new Vector4f(0, 0, -1, 1)), b = proj.transform(new Vector4f(0, 0, -100, 1));
        return b.z / b.w < a.z / a.w;
    }

    // ------------------------------------------------------------------ uniforms

    private final class GlUniforms implements Uniforms {
        FxProgram p;
        Frame frame;
        int depthTex, nextUnit, outW, outH;
        float time, revZ;
        final Matrix4f invProj = new Matrix4f(), invView = new Matrix4f();
        private final float[] m = new float[16];
        private final float[] camWrapped = new float[3];

        void frameSetup(Frame f, int depth) {
            this.frame = f;
            this.depthTex = depth;
            invProj.set(f.proj()).invert();
            invView.set(f.view()).invert();
            revZ = isReverseZ(f.proj()) ? 1f : 0f;
            time = (System.nanoTime() / 1e9f) % 3600f;
            for (int i = 0; i < 3; i++) {
                double c = i == 0 ? f.camera().x : i == 1 ? f.camera().y : f.camera().z;
                camWrapped[i] = (float) (c - Math.floor(c / 8192.0) * 8192.0);
            }
        }

        void use(FxProgram program, int w, int h) {
            this.p = program;
            this.outW = w;
            this.outH = h;
            this.nextUnit = FIRST_FREE_UNIT;
        }

        void common() {
            mat4("InvProj", invProj);
            mat4("InvView", invView);
            mat4("ViewProj", frame.viewProj());
            mat4("Proj", frame.proj());
            mat4("View", frame.view());
            f2("OutSize", outW, outH);
            f2("ScreenSize", width, height);
            f1("DepthZeroToOne", zZeroToOne);
            f1("ReverseZ", revZ);
            f1("GameTime", time);
            f3("CameraPos", camWrapped[0], camWrapped[1], camWrapped[2]);
        }

        @Override
        public Frame frame() {
            return frame;
        }

        @Override
        public void f1(String n, float x) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniform1f(l, x);
        }

        @Override
        public void f2(String n, float x, float y) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniform2f(l, x, y);
        }

        @Override
        public void f3(String n, float x, float y, float z) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniform3f(l, x, y, z);
        }

        @Override
        public void f4(String n, float x, float y, float z, float w) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniform4f(l, x, y, z, w);
        }

        @Override
        public void i1(String n, int x) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniform1i(l, x);
        }

        @Override
        public void vec4s(String n, float[] data) {
            int l = p.location(n);
            if (l >= 0 && data.length >= 4) {
                if (data.length % 4 != 0) data = java.util.Arrays.copyOf(data, data.length - data.length % 4);
                GL20.glUniform4fv(l, data);
            }
        }

        @Override
        public void mat4(String n, Matrix4fc x) {
            int l = p.location(n);
            if (l >= 0) GL20.glUniformMatrix4fv(l, false, x.get(m));
        }

        private int unit(String sampler) {
            if (p.location(sampler) < 0) return -1;
            if (nextUnit >= MAX_UNITS) {
                FailureLog.report("too many textures in one pass", "max " + (MAX_UNITS - FIRST_FREE_UNIT) + " extra textures per pass");
                return -1;
            }
            return nextUnit++;
        }

        @Override
        public void texture(String sampler, Identifier id, boolean linear, boolean repeat) {
            int unit = unit(sampler);
            if (unit < 0) return;
            int tex = 0;
            try {
                AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
                GpuTexture gpu = t.getTexture();
                if (gpu instanceof GlTexture gl) tex = gl.glId();
            } catch (RuntimeException e) {
                FailureLog.report("texture " + id + " could not be loaded", e.toString());
            }
            int s = linear ? (repeat ? linearRepeat : linearClamp) : (repeat ? nearestRepeat : nearestClamp);
            bind(p, sampler, unit, tex, s);
        }

        @Override
        public void target(String sampler, String targetName) {
            int unit = unit(sampler);
            if (unit < 0) return;
            bind(p, sampler, unit, namedTexture(targetName), linearClamp);
        }

        @Override
        public void history(String sampler, float secondsAgo) {
            int unit = unit(sampler);
            if (unit < 0) return;
            bind(p, sampler, unit, historyTexture(secondsAgo), linearClamp);
        }
    }
}
