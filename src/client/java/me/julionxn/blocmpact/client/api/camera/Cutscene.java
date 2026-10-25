package me.julionxn.blocmpact.client.api.camera;

import me.julionxn.blocmpact.api.FxMath;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A short film for the local player: a list of {@link Shot}s on a clock. Play it with {@link Cinematics#play}.
 * <p>
 * The clock either runs on its own ({@code speed} timeline ticks per game tick) or follows an external time
 * source, typically an effect's {@code time(partial)}, so camera cuts, flashes, shaders and HUD land on the same
 * frame without coordination.
 */
public final class Cutscene {
    private final List<Shot> shots;
    private final float speed, fadeIn, fadeOut, letterbox;
    private final int letterboxColor, priority;
    private final boolean hideHud, hideHand, collide, skippable;
    private final Function<Float, Float> timeSource;
    private final Function<Float, Vec3> focus;
    private final float length;

    private Cutscene(Builder b) {
        this.shots = List.copyOf(b.shots);
        this.speed = b.speed;
        this.fadeIn = b.fadeIn;
        this.fadeOut = b.fadeOut;
        this.letterbox = b.letterbox;
        this.letterboxColor = b.letterboxColor;
        this.priority = b.priority;
        this.hideHud = b.hideHud;
        this.hideHand = b.hideHand;
        this.collide = b.collide;
        this.skippable = b.skippable;
        this.timeSource = b.timeSource;
        this.focus = b.focus;
        float end = b.length;
        for (Shot s : shots) end = Math.max(end, s.end());
        this.length = end;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Shot> shots() {
        return shots;
    }

    public float speed() {
        return speed;
    }

    public float length() {
        return length;
    }

    public float fadeIn() {
        return fadeIn;
    }

    public float fadeOut() {
        return fadeOut;
    }

    public float letterbox() {
        return letterbox;
    }

    public int letterboxColor() {
        return letterboxColor;
    }

    public int priority() {
        return priority;
    }

    public boolean hideHud() {
        return hideHud;
    }

    public boolean hideHand() {
        return hideHand;
    }

    public boolean collide() {
        return collide;
    }

    public boolean skippable() {
        return skippable;
    }

    /** External time in ticks for this frame, or null if the cutscene runs its own clock. */
    public Float externalTime(float partial) {
        return timeSource == null ? null : timeSource.apply(partial);
    }

    public Vec3 focus(float partial) {
        return focus == null ? Vec3.ZERO : focus.apply(partial);
    }

    /** Fade weight at time {@code t} (0 outside, 1 in the middle). */
    public float weight(float t) {
        if (t < 0 || t > length) return 0;
        float in = fadeIn <= 0 ? 1 : FxMath.smooth(t / fadeIn);
        float out = fadeOut <= 0 ? 1 : FxMath.smooth((length - t) / fadeOut);
        return Math.min(in, out);
    }

    public static final class Builder {
        private final List<Shot> shots = new ArrayList<>();
        private float speed = 1f, fadeIn = 5, fadeOut = 12, letterbox, length;
        private int letterboxColor = 0xFF000000, priority;
        private boolean hideHud = true, hideHand = true, collide = true, skippable = true;
        private Function<Float, Float> timeSource;
        private Function<Float, Vec3> focus;

        public Builder shot(Shot shot) {
            shots.add(shot);
            return this;
        }

        /** Own clock speed (ignored with {@link #time}). */
        public Builder speed(float speed) {
            this.speed = speed;
            return this;
        }

        /** Follows an external time in ticks (for example {@code effect::time}). */
        public Builder time(Function<Float, Float> partialToTicks) {
            this.timeSource = partialToTicks;
            return this;
        }

        /** Point the shots are built around (returned by {@link ShotContext#focus()}). */
        public Builder focus(Function<Float, Vec3> partialToPosition) {
            this.focus = partialToPosition;
            return this;
        }

        public Builder focus(Vec3 point) {
            return focus(p -> point);
        }

        /** Blend in and out with the player's camera, in ticks (defaults 5 and 12). */
        public Builder fades(float in, float out) {
            this.fadeIn = in;
            this.fadeOut = out;
            return this;
        }

        /** Total length when it should outlast the last shot. */
        public Builder length(float ticks) {
            this.length = ticks;
            return this;
        }

        /** Cinema bars: fraction of the screen height covered top and bottom (0 = none), and their color. */
        public Builder letterbox(float fraction, int argb) {
            this.letterbox = fraction;
            this.letterboxColor = argb;
            return this;
        }

        /** A playing cutscene is only replaced by one with greater or equal priority. */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder hideHud(boolean hide) {
            this.hideHud = hide;
            return this;
        }

        public Builder hideHand(boolean hide) {
            this.hideHand = hide;
            return this;
        }

        /** Keeps the camera out of solid blocks (default on). */
        public Builder collide(boolean collide) {
            this.collide = collide;
            return this;
        }

        public Builder skippable(boolean skippable) {
            this.skippable = skippable;
            return this;
        }

        public Cutscene build() {
            return new Cutscene(this);
        }
    }
}
