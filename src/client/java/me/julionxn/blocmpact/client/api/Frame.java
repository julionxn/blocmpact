package me.julionxn.blocmpact.client.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Everything a per-frame hook needs about the camera. The view matrix is rotation only: the world is drawn
 * relative to the camera, so always convert world positions with {@link #rel(Vec3)} (subtracting in {@code double})
 * before handing them to a shader or a mesh. That keeps full precision millions of blocks away from the origin.
 *
 * <p>The eye sits at the origin of camera-relative space even with view bobbing (whose translation Minecraft puts
 * in the projection): {@code camera} is the bobbed eye. The one exception is the frame of {@code geometry()}, whose
 * origin is Minecraft's own render position, because meshes go through Minecraft's pipeline; the difference is a
 * few hundredths of a block and projecting a point gives the same pixel with either frame.
 *
 * @param partial  partial tick of this frame (0–1)
 * @param camera   origin of camera-relative positions, in the world (see above)
 * @param view     view rotation matrix
 * @param proj     projection matrix actually used to draw the level (FOV, zoom, bobbing tilt, shake and nausea
 *                 included)
 * @param viewProj {@code proj * view}
 * @param width    framebuffer width in pixels
 * @param height   framebuffer height in pixels
 * @param seconds  wall-clock seconds, wrapped every hour (for idle animation, not for timelines)
 */
public record Frame(float partial, Vec3 camera, Matrix4fc view, Matrix4fc proj, Matrix4fc viewProj, int width, int height, float seconds) {

    /** World position relative to the camera. */
    public Vec3 rel(Vec3 world) {
        return world.subtract(camera);
    }

    /** {@link #rel(Vec3)} as floats, ready for a uniform or a vertex. */
    public Vector3f relf(Vec3 world) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    public double distance(Vec3 world) {
        return camera.distanceTo(world);
    }

    /**
     * Projects a world point. Returns {@code (u, v, w)} with u, v in 0–1 using the OpenGL convention (v grows
     * upwards, like {@code texCoord} in a shader) and w the clip-space w (≤ 0 means behind the camera).
     */
    public Vector3f projectUv(Vec3 world) {
        Vector3f r = relf(world);
        Vector4f c = viewProj.transform(new Vector4f(r.x, r.y, r.z, 1f));
        if (c.w <= 1e-4f) return new Vector3f(-10f, -10f, -1f);
        return new Vector3f(c.x / c.w * 0.5f + 0.5f, c.y / c.w * 0.5f + 0.5f, c.w);
    }

    /** Like {@link #projectUv} but with v growing downwards (screen / HUD convention). */
    public Vector3f projectScreen(Vec3 world) {
        Vector3f p = projectUv(world);
        if (p.z > 0) p.y = 1f - p.y;
        return p;
    }

    /** True if the point is in front of the camera and inside the screen (with a margin in uv units). */
    public boolean onScreen(Vec3 world, float margin) {
        Vector3f p = projectUv(world);
        return p.z > 0 && p.x > -margin && p.x < 1 + margin && p.y > -margin && p.y < 1 + margin;
    }

    /** Unit vector the camera looks along. */
    public Vector3f forward() {
        return new Matrix4f(view).invert().transformDirection(new Vector3f(0, 0, -1)).normalize();
    }

    /** Camera right vector (world space). */
    public Vector3f right() {
        return new Matrix4f(view).invert().transformDirection(new Vector3f(1, 0, 0)).normalize();
    }

    /** Camera up vector (world space). */
    public Vector3f up() {
        return new Matrix4f(view).invert().transformDirection(new Vector3f(0, 1, 0)).normalize();
    }

    /** Size of one pixel in blocks at {@code distance} (for "never thinner than a pixel" widths). */
    public float pixelSize(double distance) {
        float m11 = proj.m11();
        return (float) (2.0 * distance / (Math.abs(m11) * Math.max(height, 1)));
    }

    public float aspect() {
        return width / (float) Math.max(height, 1);
    }
}
