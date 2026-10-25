package me.julionxn.blocmpact.client.impl;

import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.api.entity.EntityFx;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.api.post.PostPass;
import me.julionxn.blocmpact.client.api.post.ScreenFx;
import me.julionxn.blocmpact.client.impl.post.PostChain;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame data shared by the hooks: captured matrices, the {@link Frame}s handed to effects, and the environment,
 * camera and entity contributions gathered at the start of each frame.
 *
 * <p>View bobbing puts a translation inside the projection, so the eye is not at the origin of camera-relative space.
 * Shaders and the {@link Frame} helpers assume it is: {@link #build} moves that translation into the camera position
 * (same pixels, eye at the origin). Geometry is the exception: Minecraft's pipeline draws relative to its own render
 * origin, so the geometry frame keeps the projection as captured.
 */
public final class FrameState {
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static Vec3 renderCamera = Vec3.ZERO;
    private static volatile Frame last;
    private static Frame hud;
    private static EnvironmentFx environment = new EnvironmentFx();
    private static CameraFx camera = new CameraFx();
    private static EntityFx entities = new EntityFx();
    private static float framePartial, shakeSeconds;

    private FrameState() {
    }

    /** Start of a frame (before the camera update): gather environment, camera and entity overrides. */
    public static void beginFrame(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        EnvironmentFx env = new EnvironmentFx();
        CameraFx cam = new CameraFx();
        EntityFx ent = new EntityFx();
        shakeSeconds = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        if (mc.level != null) {
            framePartial = delta.getGameTimeDeltaPartialTick(false);
            Frame f = build(framePartial, mc.gameRenderer.mainCamera().position(), VIEW, PROJ, true);
            EffectManager.INSTANCE.forEachInRange(f, "environment", fx -> fx.environment(f, env));
            EffectManager.INSTANCE.forEachInRange(f, "camera", fx -> fx.camera(f, cam));
            EffectManager.INSTANCE.forEachInRange(f, "entities", fx -> fx.entities(f, ent));
        }
        environment = env;
        camera = cam;
        entities = ent;
    }

    /**
     * Extraction, right before the HUD: the camera state of the frame about to be rendered is final, but the level
     * (and the real projection) comes later. Rebuilds that projection the way {@code GameRenderer.renderLevel} does,
     * so the HUD projects with this frame's matrices instead of the previous frame's (which lag behind view bobbing
     * and camera shake).
     *
     * @param bob the pose {@code bobHurt} and {@code bobView} produce for this frame
     */
    public static void captureHud(GameRenderState state, Matrix4fc bob, float partial) {
        if (!state.shouldRenderLevel || Minecraft.getInstance().level == null) {
            hud = null;
            return;
        }
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        Matrix4f proj = new Matrix4f(camera.projectionMatrix).mul(bob);
        PlayerRenderState player = state.levelRenderState.playerRenderState;
        float scale = state.optionsRenderState.screenEffectScale;
        float warp = Math.max(player.portalEffectIntensity, player.nauseaEffectIntensity) * scale * scale;
        if (warp > 0) {
            float k = 5f / (warp * warp + 5f) - warp * 0.04f;
            k *= k;
            Vector3f axis = new Vector3f(0, Mth.SQRT_OF_TWO / 2f, Mth.SQRT_OF_TWO / 2f);
            float angle = player.spinningEffectAngle * Mth.DEG_TO_RAD;
            proj.rotate(angle, axis).scale(1f / k, 1f, 1f).rotate(-angle, axis);
        }
        hud = build(partial, camera.pos, camera.viewRotationMatrix, proj, true);
    }

    /** Level render start: the camera state actually used this frame. */
    public static void captureView(CameraRenderState state) {
        VIEW.set(state.viewRotationMatrix);
        renderCamera = state.pos;
    }

    /** The projection actually used to draw the level (FOV, zoom and bobbing included). */
    public static void captureProjection(Matrix4f projection) {
        PROJ.set(projection);
    }

    /** Level render end: build the exact frame and run the level-3 chain. */
    public static void renderPost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Frame f = build(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), renderCamera, VIEW, PROJ, true);
        last = f;
        List<PostPass> passes = new ArrayList<>();
        ScreenFx screen = new ScreenFx();
        EffectManager.INSTANCE.forEachInRange(f, "passes", fx -> fx.passes(f, passes::add));
        EffectManager.INSTANCE.forEachInRange(f, "screen", fx -> fx.screen(f, screen));
        PostChain.INSTANCE.render(f, passes, screen);
    }

    /**
     * @param eyeAtOrigin move the translation inside {@code proj} (view bobbing) into the camera position so the eye
     *                    sits at the origin of camera-relative space; false keeps Minecraft's render origin
     */
    private static Frame build(float partial, Vec3 cameraPos, Matrix4fc viewIn, Matrix4fc projIn, boolean eyeAtOrigin) {
        Minecraft mc = Minecraft.getInstance();
        var target = mc.gameRenderer.mainRenderTarget();
        Matrix4f view = new Matrix4f(viewIn), proj = new Matrix4f(projIn);
        if (eyeAtOrigin) {
            // A perspective projection sends the eye to (0, 0, c, 0): pull that point back through the inverse.
            Vector4f e = new Matrix4f(proj).invert().transform(new Vector4f(0, 0, 1, 0));
            if (Math.abs(e.w) > 1e-12f) {
                Vector3f eye = new Vector3f(e.x / e.w, e.y / e.w, e.z / e.w);
                float len2 = eye.lengthSquared();
                if (len2 > 1e-12f && len2 < 1f) {
                    proj.translate(eye);
                    Vector3f d = new Matrix4f(view).invert().transformDirection(eye);
                    cameraPos = cameraPos.add(d.x, d.y, d.z);
                }
            }
        }
        return new Frame(partial, cameraPos, view, proj, new Matrix4f(proj).mul(view), target.width, target.height,
            (System.nanoTime() / 1e9f) % 3600f);
    }

    /** The frame most recently rendered (null before the first one). */
    public static Frame lastFrame() {
        return last;
    }

    /** The frame the HUD being extracted belongs to (falls back to the last rendered one). */
    public static Frame hudFrame() {
        return hud != null ? hud : last;
    }

    /**
     * The frame being rendered right now (inside the level render), for geometry: camera-relative to Minecraft's render
     * origin, with the projection exactly as Minecraft uses it.
     */
    public static Frame renderFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return build(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), renderCamera, VIEW, PROJ, false);
    }

    /** A frame for the current moment, using the latest matrices. */
    public static Frame currentFrame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return build(partial, mc.gameRenderer.mainCamera().position(), VIEW, PROJ, true);
    }

    public static EnvironmentFx environment() {
        return environment;
    }

    public static CameraFx camera() {
        return camera;
    }

    public static EntityFx entities() {
        return entities;
    }

    public static float framePartial() {
        return framePartial;
    }

    /** Wall-clock seconds sampled once per frame, so every {@code bobHurt} call of a frame shakes the same way. */
    public static float shakeSeconds() {
        return shakeSeconds;
    }
}
