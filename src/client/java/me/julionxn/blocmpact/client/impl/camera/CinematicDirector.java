package me.julionxn.blocmpact.client.impl.camera;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.camera.CameraPose;
import me.julionxn.blocmpact.client.api.camera.Cutscene;
import me.julionxn.blocmpact.client.api.camera.Shot;
import me.julionxn.blocmpact.client.api.camera.ShotContext;
import me.julionxn.blocmpact.client.impl.BlocmpactConfig;
import me.julionxn.blocmpact.client.impl.FailureLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Plays one cutscene at a time on the local camera, blending with the player's view by a fade weight. */
public final class CinematicDirector {
    public static final CinematicDirector INSTANCE = new CinematicDirector();
    private static final float SKIP_FADE_TICKS = 6f;

    private Cutscene current;
    private float clock, skipStart = Float.NaN;
    private float lastTime = Float.NaN, lastWeight;
    private CameraPose lastPose;

    public boolean play(Cutscene cutscene) {
        if (!BlocmpactConfig.get().cinematics) return false;
        if (current != null && cutscene.priority() < current.priority()) return false;
        current = cutscene;
        clock = 0;
        skipStart = Float.NaN;
        return true;
    }

    public void skip() {
        if (current != null && current.skippable() && Float.isNaN(skipStart)) skipStart = lastTime;
    }

    public void stop() {
        current = null;
        lastWeight = 0;
        lastTime = Float.NaN;
    }

    public boolean active() {
        return current != null;
    }

    public Cutscene current() {
        return current;
    }

    public float weight() {
        return current == null ? 0 : lastWeight;
    }

    public float time() {
        return current == null ? Float.NaN : lastTime;
    }

    /** Game tick: advances the own clock and ends finished cutscenes. */
    public void tick(Minecraft mc) {
        if (current == null) return;
        if (mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        clock += current.speed();
        float t = Float.isNaN(lastTime) ? clock : lastTime;
        if (t > current.length() + 1 || !Float.isNaN(skipStart) && t - skipStart > SKIP_FADE_TICKS + 1) stop();
    }

    private float timeAt(float partial) {
        Float ext = current.externalTime(partial);
        return ext != null ? ext : clock - current.speed() + partial * current.speed();
    }

    /**
     * Pose for this frame blended with the vanilla pose, or null when no cutscene is playing.
     * Called from the camera hook after vanilla positioned the camera.
     */
    public CameraPose pose(CameraPose vanilla, float partial) {
        if (current == null) return null;
        try {
            float t = timeAt(partial);
            lastTime = t;
            float w = current.weight(t);
            if (!Float.isNaN(skipStart)) w *= 1 - FxMath.smooth((t - skipStart) / SKIP_FADE_TICKS);
            lastWeight = w;
            if (w <= 0) return null;
            CameraPose shot = shotPose(t, partial, vanilla);
            if (shot == null) return null;
            if (current.collide()) shot = collide(shot);
            lastPose = shot;
            return vanilla.lerp(shot, w);
        } catch (RuntimeException e) {
            FailureLog.report("cutscene camera failed", e.toString(), e);
            stop();
            return null;
        }
    }

    private CameraPose shotPose(float t, float partial, CameraPose vanilla) {
        ShotContext ctx = new ShotContext(current, t, partial, vanilla);
        Shot active = null, previous = null;
        for (Shot s : current.shots()) {
            if (s.contains(t)) active = s;
            else if (s.end() <= t && (previous == null || s.end() > previous.end())) previous = s;
        }
        if (active == null) {
            if (previous == null) return null;
            return previous.path().pose(ctx, 1f);
        }
        CameraPose pose = active.path().pose(ctx, active.progress(t));
        if (active.blendTicks() > 0 && t < active.start() + active.blendTicks()) {
            CameraPose from = previous != null ? previous.path().pose(ctx, previous.progress(active.start())) : vanilla;
            pose = from.lerp(pose, FxMath.smooth((t - active.start()) / active.blendTicks()));
        }
        return pose;
    }

    /** Keeps the camera out of blocks: casts from the point it looks at towards the desired position. */
    private static CameraPose collide(CameraPose pose) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Entity viewer = mc.getCameraEntity();
        if (level == null || viewer == null) return pose;
        Vec3 look = pose.position().add(pose.forward().scale(4));
        BlockHitResult hit = level.clip(new ClipContext(look, pose.position(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
            CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return pose;
        Vec3 d = pose.position().subtract(look);
        double len = d.length();
        if (len < 1e-4) return pose;
        Vec3 safe = hit.getLocation().subtract(d.scale(0.4 / len));
        return new CameraPose(safe, pose.yaw(), pose.pitch(), pose.fov(), pose.roll());
    }

    public float roll() {
        return current == null || lastPose == null ? 0 : lastPose.roll() * lastWeight;
    }

    public float fov(float vanillaFov) {
        if (current == null || lastPose == null || Float.isNaN(lastPose.fov()) || lastWeight <= 0) return vanillaFov;
        return FxMath.lerp(vanillaFov, lastPose.fov(), lastWeight);
    }

    public boolean hideHud() {
        return current != null && current.hideHud() && lastWeight > 0.3f;
    }

    public boolean hideHand() {
        return current != null && current.hideHand() && lastWeight > 0.05f;
    }

    public float letterbox() {
        return current == null ? 0 : current.letterbox() * lastWeight;
    }

    public int letterboxColor() {
        return current == null ? 0 : current.letterboxColor();
    }
}
