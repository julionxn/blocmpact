package me.julionxn.blocmpact.client.impl.env;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.impl.FrameState;

import java.util.List;

/**
 * Cloud cells are 12×12 blocks and drift 0.03 blocks per tick along +X. The hook repeats vanilla's grid math to know
 * where each cell lies in the world and skips the cells inside a hole.
 */
public final class CloudCutouts {
    private static double cameraX, cameraZ, fracX, fracZ;
    private static long gameTime;
    private static float partial;
    private static int fracForWidth = -1, fracForHeight = -1;
    private static boolean active;
    private static int lastSignature;
    private static long lastRebuildTick = Long.MIN_VALUE;

    private CloudCutouts() {
    }

    /**
     * Called at the start of {@code CloudRenderer.prepare}. Returns true when the mesh must be rebuilt because the
     * set of holes changed.
     */
    public static boolean prepare(double camX, double camZ, long time, float partialTick) {
        List<EnvironmentFx.CloudHole> holes = FrameState.environment().cloudHoles();
        active = !holes.isEmpty();
        cameraX = camX;
        cameraZ = camZ;
        gameTime = time;
        partial = partialTick;
        fracForWidth = fracForHeight = -1;
        int sig = signature(holes);
        boolean changed = sig != lastSignature || active && time != lastRebuildTick; // clouds drift: once per tick
        lastSignature = sig;
        if (changed) lastRebuildTick = time;
        return changed;
    }

    /** True if the cell at offset (dx, dz) from the camera cell must not be built (texture size in cells). */
    public static boolean skip(int dx, int dz, int textureWidth, int textureHeight) {
        if (!active) return false;
        if (textureWidth != fracForWidth || textureHeight != fracForHeight) {
            float drift = (float) (gameTime % (textureWidth * 400L)) + partial;
            double x = cameraX + drift * 0.03f, z = cameraZ + 3.96f;
            double w = textureWidth * 12.0, h = textureHeight * 12.0;
            x -= Math.floor(x / w) * w;
            z -= Math.floor(z / h) * h;
            fracX = x - Math.floor(x / 12.0) * 12.0;
            fracZ = z - Math.floor(z / 12.0) * 12.0;
            fracForWidth = textureWidth;
            fracForHeight = textureHeight;
        }
        double cx = cameraX + dx * 12.0 - fracX + 6.0, cz = cameraZ + dz * 12.0 - fracZ + 6.0;
        for (EnvironmentFx.CloudHole hole : FrameState.environment().cloudHoles()) {
            double ddx = cx - hole.x(), ddz = cz - hole.z();
            double r = hole.radius();
            if (hole.ragged() > 0) {
                double a = Math.atan2(ddz, ddx);
                int sector = (int) Math.floor((a + Math.PI) / (Math.PI * 2) * 24);
                r *= 1 + (FxMath.hash(hole.seed(), sector) - 0.5) * hole.ragged();
            }
            if (ddx * ddx + ddz * ddz <= r * r) return true;
        }
        return false;
    }

    private static int signature(List<EnvironmentFx.CloudHole> holes) {
        int h = holes.size();
        for (EnvironmentFx.CloudHole hole : holes) {
            h = h * 31 + (int) Math.floor(hole.x() / 6);
            h = h * 31 + (int) Math.floor(hole.z() / 6);
            h = h * 31 + (int) Math.floor(hole.radius() / 3);
        }
        return h;
    }
}
