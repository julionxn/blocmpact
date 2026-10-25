package me.julionxn.blocmpact.client.api.env;

import me.julionxn.blocmpact.api.FxMath;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overrides of what Minecraft itself draws: fog, sky, sun/moon/stars, clouds, world light and weather.
 * Works with any graphics backend because it changes the inputs of vanilla rendering, not the image.
 * <p>
 * Every override has a {@code weight} (0–1): the vanilla value is blended towards yours. Overrides from several
 * effects apply in start order, each blending from the result of the previous one. Fill it every frame in
 * {@code VisualEffect.environment}; nothing persists between frames.
 * <p>
 * Any {@link EnvironmentAttribute} the client reads can be overridden with {@link #set}: numbers and colors blend,
 * other types are replaced once the weight passes 0.5.
 */
public final class EnvironmentFx {
    private final Map<EnvironmentAttribute<?>, List<AttributeOverride<?>>> attributes = new IdentityHashMap<>();
    private final List<CloudHole> cloudHoles = new ArrayList<>();
    private float fogStart = Float.NaN, fogEnd = Float.NaN, fogDistanceWeight;
    private float skyFogEnd = Float.NaN, skyFogWeight, cloudFogEnd = Float.NaN, cloudFogWeight;
    private final Vector4f fogColor = new Vector4f();
    private float fogColorWeight;
    private float rain = Float.NaN, rainWeight, thunder = Float.NaN, thunderWeight;

    public record AttributeOverride<T>(T value, float weight) {
    }

    /** A circular gap in the cloud layer, in world coordinates. {@code ragged} 0–1 roughens the edge. */
    public record CloudHole(double x, double z, double radius, float ragged, long seed) {
    }

    // ---- generic

    /** Blends any environment attribute towards {@code value}. */
    public <T> EnvironmentFx set(EnvironmentAttribute<T> attribute, T value, float weight) {
        if (weight <= 0) return this;
        attributes.computeIfAbsent(attribute, k -> new ArrayList<>(2)).add(new AttributeOverride<>(value, FxMath.clamp01(weight)));
        return this;
    }

    // ---- atmospheric fog (the biome / dimension fog in the open air)

    public EnvironmentFx fogColor(int rgb, float weight) {
        return set(EnvironmentAttributes.FOG_COLOR, rgb3(rgb), weight);
    }

    /** Distances where the open-air fog starts and becomes total. */
    public EnvironmentFx fogDistance(float start, float end, float weight) {
        set(EnvironmentAttributes.FOG_START_DISTANCE, start, weight);
        return set(EnvironmentAttributes.FOG_END_DISTANCE, end, weight);
    }

    public EnvironmentFx skyFogEnd(float distance, float weight) {
        return set(EnvironmentAttributes.SKY_FOG_END_DISTANCE, distance, weight);
    }

    public EnvironmentFx cloudFogEnd(float distance, float weight) {
        return set(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, distance, weight);
    }

    public EnvironmentFx waterFogColor(int rgb, float weight) {
        return set(EnvironmentAttributes.WATER_FOG_COLOR, rgb3(rgb), weight);
    }

    public EnvironmentFx waterFogDistance(float start, float end, float weight) {
        set(EnvironmentAttributes.WATER_FOG_START_DISTANCE, start, weight);
        return set(EnvironmentAttributes.WATER_FOG_END_DISTANCE, end, weight);
    }

    // ---- final fog (applies on top of every fog: air, water, lava, blindness, powder snow...)

    /** Overrides the final fog distances whatever the camera is in. */
    public EnvironmentFx fog(float start, float end, float weight) {
        if (weight > fogDistanceWeight) {
            fogStart = start;
            fogEnd = end;
            fogDistanceWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    /** Overrides the final fog color (ARGB; alpha is fog opacity). */
    public EnvironmentFx fogFinalColor(int argb, float weight) {
        if (weight > fogColorWeight) {
            fogColor.set(FxMath.red(argb), FxMath.green(argb), FxMath.blue(argb), FxMath.alpha(argb));
            fogColorWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    /** Final sky fog end distance. */
    public EnvironmentFx fogSkyEnd(float end, float weight) {
        if (weight > skyFogWeight) {
            skyFogEnd = end;
            skyFogWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    /** Final cloud fog end distance. */
    public EnvironmentFx fogCloudEnd(float end, float weight) {
        if (weight > cloudFogWeight) {
            cloudFogEnd = end;
            cloudFogWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    // ---- sky

    public EnvironmentFx skyColor(int rgb, float weight) {
        return set(EnvironmentAttributes.SKY_COLOR, rgb3(rgb), weight);
    }

    /** Sunrise/sunset glow color (ARGB, alpha = intensity). */
    public EnvironmentFx sunriseColor(int argb, float weight) {
        return set(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, argb4(argb), weight);
    }

    /** Sun angle in degrees (0 = noon, 180 = midnight). */
    public EnvironmentFx sunAngle(float degrees, float weight) {
        return set(EnvironmentAttributes.SUN_ANGLE, degrees, weight);
    }

    public EnvironmentFx moonAngle(float degrees, float weight) {
        return set(EnvironmentAttributes.MOON_ANGLE, degrees, weight);
    }

    public EnvironmentFx starAngle(float degrees, float weight) {
        return set(EnvironmentAttributes.STAR_ANGLE, degrees, weight);
    }

    /** Star brightness 0–1 (stars show in daylight too when forced). */
    public EnvironmentFx starBrightness(float brightness, float weight) {
        return set(EnvironmentAttributes.STAR_BRIGHTNESS, brightness, weight);
    }

    // ---- clouds

    /** Cloud color (ARGB, alpha = opacity: 0 hides the clouds). */
    public EnvironmentFx cloudColor(int argb, float weight) {
        return set(EnvironmentAttributes.CLOUD_COLOR, argb4(argb), weight);
    }

    public EnvironmentFx cloudHeight(float y, float weight) {
        return set(EnvironmentAttributes.CLOUD_HEIGHT, y, weight);
    }

    /**
     * Opens a hole in the clouds. Clouds are built from 12×12-block cells, so the edge follows that grid
     * ({@code ragged} adds per-sector irregularity). The mesh is rebuilt while holes change; keep them few.
     */
    public EnvironmentFx cloudHole(double x, double z, double radius, float ragged, long seed) {
        if (radius > 0) cloudHoles.add(new CloudHole(x, z, radius, ragged, seed));
        return this;
    }

    public EnvironmentFx cloudHole(double x, double z, double radius) {
        return cloudHole(x, z, radius, 0, 0);
    }

    // ---- world light (lightmap)

    /** Color of sky light on blocks. */
    public EnvironmentFx skyLightColor(int rgb, float weight) {
        return set(EnvironmentAttributes.SKY_LIGHT_COLOR, rgb3(rgb), weight);
    }

    /** How much sky light reaches the world (0 = night-dark, 1 = full day). */
    public EnvironmentFx skyLightFactor(float factor, float weight) {
        return set(EnvironmentAttributes.SKY_LIGHT_FACTOR, factor, weight);
    }

    /** Ambient light added everywhere (lifts the darkest areas). */
    public EnvironmentFx ambientLight(int rgb, float weight) {
        return set(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, rgb3(rgb), weight);
    }

    /** Tint of block light (torches, lava...). */
    public EnvironmentFx blockLightTint(int rgb, float weight) {
        return set(EnvironmentAttributes.BLOCK_LIGHT_TINT, rgb3(rgb), weight);
    }

    public EnvironmentFx nightVisionColor(int rgb, float weight) {
        return set(EnvironmentAttributes.NIGHT_VISION_COLOR, rgb3(rgb), weight);
    }

    // ---- weather (visual only, on this client)

    /** Visual rain strength 0–1 (precipitation, darker sky). Does not change server weather. */
    public EnvironmentFx rain(float level, float weight) {
        if (weight > rainWeight) {
            rain = level;
            rainWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    /** Visual thunder strength 0–1. Does not change server weather. */
    public EnvironmentFx thunder(float level, float weight) {
        if (weight > thunderWeight) {
            thunder = level;
            thunderWeight = FxMath.clamp01(weight);
        }
        return this;
    }

    // ---- read side (used by Blocmpact's hooks)

    public boolean isEmpty() {
        return attributes.isEmpty() && cloudHoles.isEmpty() && fogDistanceWeight <= 0 && fogColorWeight <= 0 && skyFogWeight <= 0
            && cloudFogWeight <= 0 && rainWeight <= 0 && thunderWeight <= 0;
    }

    @SuppressWarnings("unchecked")
    public <T> List<AttributeOverride<T>> overrides(EnvironmentAttribute<T> attribute) {
        List<AttributeOverride<?>> l = attributes.get(attribute);
        return l == null ? List.of() : (List<AttributeOverride<T>>) (List<?>) l;
    }

    public List<CloudHole> cloudHoles() {
        return cloudHoles;
    }

    public float fogStart() {
        return fogStart;
    }

    public float fogEnd() {
        return fogEnd;
    }

    public float fogDistanceWeight() {
        return fogDistanceWeight;
    }

    public Vector4f fogFinalColor() {
        return fogColor;
    }

    public float fogColorWeight() {
        return fogColorWeight;
    }

    public float skyFogEnd() {
        return skyFogEnd;
    }

    public float skyFogWeight() {
        return skyFogWeight;
    }

    public float cloudFogEnd() {
        return cloudFogEnd;
    }

    public float cloudFogWeight() {
        return cloudFogWeight;
    }

    public float rain() {
        return rain;
    }

    public float rainWeight() {
        return rainWeight;
    }

    public float thunder() {
        return thunder;
    }

    public float thunderWeight() {
        return thunderWeight;
    }

    private static Vector3f rgb3(int rgb) {
        return new Vector3f(FxMath.red(rgb), FxMath.green(rgb), FxMath.blue(rgb));
    }

    private static Vector4f argb4(int argb) {
        return new Vector4f(FxMath.red(argb), FxMath.green(argb), FxMath.blue(argb), FxMath.alpha(argb));
    }
}
