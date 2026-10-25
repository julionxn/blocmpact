package me.julionxn.blocmpact.client.api.camera;

import net.minecraft.world.phys.Vec3;

/**
 * Camera operations that work with any graphics backend (they move the real camera, not the image).
 * Every effect in range writes into the same instance each frame; each method documents how values combine.
 */
public final class CameraFx {
    public float shake, shakeFrequency = 12f, roll, yaw, pitch, fovScale = 1f, fovAdd;
    public double offsetX, offsetY, offsetZ;

    /** Rotational shake in degrees (≈0.5 hit, 3 big impact). Keeps the max. */
    public CameraFx shake(float degrees) {
        shake = Math.max(shake, degrees);
        return this;
    }

    /** Shake speed in Hz (default 12). The fastest wins. */
    public CameraFx shakeFrequency(float hz) {
        shakeFrequency = Math.max(shakeFrequency, hz);
        return this;
    }

    /** Tilts the horizon (degrees). Adds. */
    public CameraFx roll(float degrees) {
        roll += degrees;
        return this;
    }

    /** Extra rotation in degrees (recoil, a look towards something). Adds. */
    public CameraFx rotate(float yawDegrees, float pitchDegrees) {
        yaw += yawDegrees;
        pitch += pitchDegrees;
        return this;
    }

    /** Multiplies the field of view (0.8 zooms in, 1.2 widens). Multiplies. */
    public CameraFx fovScale(float scale) {
        fovScale *= scale;
        return this;
    }

    /** Adds degrees to the field of view. Adds. */
    public CameraFx fov(float degrees) {
        fovAdd += degrees;
        return this;
    }

    /** Moves the camera in world space (blocks). Adds. */
    public CameraFx offset(Vec3 offset) {
        offsetX += offset.x;
        offsetY += offset.y;
        offsetZ += offset.z;
        return this;
    }

    public boolean active() {
        return shake > 0 || roll != 0 || yaw != 0 || pitch != 0 || fovScale != 1f || fovAdd != 0 || offsetX != 0 || offsetY != 0 || offsetZ != 0;
    }
}
