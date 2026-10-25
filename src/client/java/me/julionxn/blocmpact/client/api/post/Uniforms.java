package me.julionxn.blocmpact.client.api.post;

import me.julionxn.blocmpact.client.api.Frame;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/**
 * Sets a pass's uniforms. Names the shader does not declare are silently ignored, so one setup lambda can feed
 * several variants of a shader.
 * <p>
 * Always provided by the engine: {@code DiffuseSampler} (previous pass), {@code DepthSampler} (Minecraft's depth),
 * {@code SceneSampler} (untouched scene), {@code InvProj}, {@code InvView}, {@code ViewProj}, {@code Proj},
 * {@code View}, {@code OutSize}, {@code ScreenSize}, {@code DepthZeroToOne}, {@code ReverseZ}, {@code GameTime},
 * {@code CameraPos}. Free for effects: {@code P0..P7} (vec4), {@code Pts[32]} (vec4 array), {@code Target} (vec3).
 */
public interface Uniforms {
    /** The frame being drawn (camera, matrices, partial tick). */
    Frame frame();

    void f1(String name, float x);

    void f2(String name, float x, float y);

    void f3(String name, float x, float y, float z);

    void f4(String name, float x, float y, float z, float w);

    void i1(String name, int x);

    /** Uploads {@code data.length / 4} vec4s into an array uniform such as {@code Pts}. */
    void vec4s(String name, float[] data);

    void mat4(String name, Matrix4fc matrix);

    /** Binds a texture loaded through Minecraft's texture manager ({@code ns:textures/...png}), linear and repeating. */
    default void texture(String sampler, Identifier texture) {
        texture(sampler, texture, true, true);
    }

    void texture(String sampler, Identifier texture, boolean linear, boolean repeat);

    /** Binds the latest content of a named target written by a pass with {@code PostPass.to/toPersistent}. */
    void target(String sampler, String targetName);

    /**
     * Binds the final image as it was about {@code secondsAgo} seconds ago (up to ~2 s, quarter resolution).
     * Requesting it starts recording the history; it is released after a while without use.
     */
    void history(String sampler, float secondsAgo);

    // ---- conveniences

    default void v3(String name, Vec3 v) {
        f3(name, (float) v.x, (float) v.y, (float) v.z);
    }

    default void v3(String name, Vector3fc v) {
        f3(name, v.x(), v.y(), v.z());
    }

    /** A world position converted to camera-relative coordinates (what every shader position must be). */
    default void world(String name, Vec3 worldPos) {
        v3(name, frame().rel(worldPos));
    }

    /** A packed {@code 0xAARRGGBB} color as vec4 (r, g, b, a) in 0–1. */
    default void color(String name, int argb) {
        f4(name, (argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    /** A packed color as vec3 (alpha ignored). */
    default void rgb(String name, int rgb) {
        f3(name, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
    }

    /** A world position projected to the screen: vec3(u, v, w) with w ≤ 0 when behind the camera. */
    default void screenPoint(String name, Vec3 worldPos) {
        v3(name, frame().projectUv(worldPos));
    }
}
