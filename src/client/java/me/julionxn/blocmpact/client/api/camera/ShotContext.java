package me.julionxn.blocmpact.client.api.camera;

import me.julionxn.blocmpact.api.FxMath;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a shot path can read: cutscene time, the subject being filmed and the player's own camera pose.
 * Write shots relative to the subject (never in absolute coordinates) so they work anywhere.
 */
public final class ShotContext {
    private final Cutscene cutscene;
    private final float time, partial;
    private final CameraPose playerPose;

    public ShotContext(Cutscene cutscene, float time, float partial, CameraPose playerPose) {
        this.cutscene = cutscene;
        this.time = time;
        this.partial = partial;
        this.playerPose = playerPose;
    }

    public Cutscene cutscene() {
        return cutscene;
    }

    /** Cutscene time in ticks. */
    public float time() {
        return time;
    }

    public float partial() {
        return partial;
    }

    /** Where the vanilla camera would be this frame. */
    public CameraPose playerPose() {
        return playerPose;
    }

    /** The cutscene's focus point ({@link Cutscene.Builder#focus}). */
    public Vec3 focus() {
        return cutscene.focus(partial);
    }

    /** The local player (the usual "caster"). */
    public Entity player() {
        return Minecraft.getInstance().player;
    }

    public Vec3 playerFeet() {
        Entity p = player();
        return p == null ? focus() : p.getPosition(partial);
    }

    /** Horizontal unit vector from the focus towards the player (or north if they coincide). */
    public Vec3 home() {
        Vec3 d = playerFeet().subtract(focus());
        Vec3 h = new Vec3(d.x, 0, d.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
    }

    /** {@code dir} rotated 90° to the right around the vertical axis. */
    public static Vec3 side(Vec3 dir) {
        return new Vec3(-dir.z, 0, dir.x);
    }

    /** {@code point} orbited around {@code pivot} by {@code degrees} (vertical axis). */
    public static Vec3 orbit(Vec3 point, Vec3 pivot, float degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        Vec3 d = point.subtract(pivot);
        return pivot.add(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
    }

    /** Linear interpolation helper for numbers inside a shot. */
    public static float l(float k, float from, float to) {
        return FxMath.lerp(from, to, k);
    }
}
