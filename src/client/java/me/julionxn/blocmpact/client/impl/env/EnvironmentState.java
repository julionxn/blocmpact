package me.julionxn.blocmpact.client.impl.env;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.impl.FrameState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.List;

/** Applies the frame's {@link EnvironmentFx} to the values vanilla reads. */
public final class EnvironmentState {
    private EnvironmentState() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T apply(EnvironmentAttribute<T> attribute, T vanilla) {
        EnvironmentFx env = FrameState.environment();
        List<EnvironmentFx.AttributeOverride<T>> list = env.overrides(attribute);
        if (list.isEmpty()) return vanilla;
        Object value = vanilla;
        boolean angle = attribute == EnvironmentAttributes.SUN_ANGLE || attribute == EnvironmentAttributes.MOON_ANGLE
            || attribute == EnvironmentAttributes.STAR_ANGLE;
        for (EnvironmentFx.AttributeOverride<T> o : list) value = blend(value, o.value(), o.weight(), angle);
        return (T) value;
    }

    private static Object blend(Object from, Object to, float w, boolean angle) {
        if (from instanceof Float a && to instanceof Float b) {
            return angle ? a + Mth.wrapDegrees(b - a) * w : FxMath.lerp(a, b, w);
        }
        if (from instanceof Vector3fc a && to instanceof Vector3fc b) return new Vector3f(a).lerp(b, w);
        if (from instanceof Vector4fc a && to instanceof Vector4fc b) return new Vector4f(a).lerp(b, w);
        if (from instanceof Integer a && to instanceof Integer b) return Math.round(FxMath.lerp(a, b, w));
        if (from instanceof Double a && to instanceof Double b) return FxMath.lerp(a, b, w);
        return w >= 0.5f ? to : from;
    }

    /** Final fog override (any fog type). */
    public static void applyFog(FogData data) {
        EnvironmentFx env = FrameState.environment();
        if (env.fogDistanceWeight() > 0) {
            float w = env.fogDistanceWeight();
            data.environmentalStart = FxMath.lerp(data.environmentalStart, env.fogStart(), w);
            data.environmentalEnd = FxMath.lerp(data.environmentalEnd, env.fogEnd(), w);
            data.renderDistanceStart = FxMath.lerp(data.renderDistanceStart, Math.max(env.fogStart(), data.renderDistanceStart * (1 - w)), w);
            data.renderDistanceEnd = FxMath.lerp(data.renderDistanceEnd, Math.min(Math.max(env.fogEnd(), 0.01f), data.renderDistanceEnd), w);
        }
        if (env.skyFogWeight() > 0) data.skyEnd = FxMath.lerp(data.skyEnd, env.skyFogEnd(), env.skyFogWeight());
        if (env.cloudFogWeight() > 0) data.cloudEnd = FxMath.lerp(data.cloudEnd, env.cloudFogEnd(), env.cloudFogWeight());
        if (env.fogColorWeight() > 0 && data.color != null) data.color.lerp(env.fogFinalColor(), env.fogColorWeight());
    }

    public static float rain(float vanilla) {
        EnvironmentFx env = FrameState.environment();
        return env.rainWeight() > 0 ? FxMath.lerp(vanilla, env.rain(), env.rainWeight()) : vanilla;
    }

    public static float thunder(float vanilla) {
        EnvironmentFx env = FrameState.environment();
        return env.thunderWeight() > 0 ? FxMath.lerp(vanilla, env.thunder(), env.thunderWeight()) : vanilla;
    }
}
