package me.julionxn.blocmpact.client.api.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A camera: position, orientation (Minecraft yaw/pitch in degrees), field of view and roll.
 *
 * @param fov vertical field of view in degrees, or {@code NaN} to keep the player's FOV
 */
public record CameraPose(Vec3 position, float yaw, float pitch, float fov, float roll) {

    /** A pose at {@code position} looking at {@code target}. */
    public static CameraPose lookAt(Vec3 position, Vec3 target, float fov, float roll) {
        Vec3 d = target.subtract(position);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        return new CameraPose(position, yaw, pitch, fov, roll);
    }

    public static CameraPose lookAt(Vec3 position, Vec3 target) {
        return lookAt(position, target, Float.NaN, 0f);
    }

    /** Interpolates towards {@code other}; angles take the shortest way round. */
    public CameraPose lerp(CameraPose other, float k) {
        Vec3 p = position.lerp(other.position, k);
        float y = yaw + Mth.wrapDegrees(other.yaw - yaw) * k;
        float pi = pitch + (other.pitch - pitch) * k;
        float f = Float.isNaN(fov) ? other.fov : Float.isNaN(other.fov) ? fov : fov + (other.fov - fov) * k;
        return new CameraPose(p, y, pi, f, roll + (other.roll - roll) * k);
    }

    public CameraPose withFov(float newFov) {
        return new CameraPose(position, yaw, pitch, newFov, roll);
    }

    public CameraPose withRoll(float newRoll) {
        return new CameraPose(position, yaw, pitch, fov, newRoll);
    }

    /** Unit vector the pose looks along. */
    public Vec3 forward() {
        return Vec3.directionFromRotation(pitch, yaw);
    }
}
