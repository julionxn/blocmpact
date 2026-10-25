package me.julionxn.blocmpact.client.api.camera;

import me.julionxn.blocmpact.api.FxMath;

/**
 * One shot of a {@link Cutscene}: a camera path between two cutscene ticks.
 * A {@link #cut} starts exactly on its first pose; a {@link #sweep} blends from wherever the camera was
 * (the previous shot or the player) during {@code blendTicks}.
 */
public record Shot(float start, float end, float blendTicks, Easing easing, Path path) {

    /** Computes the pose of the shot. {@code k} is the eased 0–1 progress through the shot. */
    @FunctionalInterface
    public interface Path {
        CameraPose pose(ShotContext ctx, float k);
    }

    @FunctionalInterface
    public interface Easing {
        float apply(float k);
    }

    /** Smooth start and stop (cubic), the default: every shot is a dolly move. */
    public static final Easing DOLLY = FxMath::easeInOutCubic;
    public static final Easing LINEAR = k -> k;

    public static Shot cut(float start, float end, Path path) {
        return new Shot(start, end, 0, DOLLY, path);
    }

    public static Shot sweep(float start, float end, float blendTicks, Path path) {
        return new Shot(start, end, blendTicks, DOLLY, path);
    }

    public Shot easing(Easing e) {
        return new Shot(start, end, blendTicks, e, path);
    }

    public boolean contains(float t) {
        return t >= start && t < end;
    }

    /** Eased progress at cutscene time {@code t}. */
    public float progress(float t) {
        return easing.apply(FxMath.progress(t, start, end));
    }
}
