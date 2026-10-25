package me.julionxn.blocmpact.api;

import java.util.function.IntConsumer;

/**
 * Timeline clock with variable speed, identical on server and client.
 * <p>
 * Every game tick the clock advances by {@code speed} and runs the callback once for each whole timeline tick it
 * crosses. With {@code speed = 0.5} the whole effect plays in slow motion on both sides without touching a single
 * constant; with {@code speed = 2} two timeline ticks run per game tick; with {@code speed = 0} the timeline holds
 * still. The speed can change while the clock runs ({@link #setSpeed}).
 * <p>
 * {@link #time(float)} is the continuous time used by shaders, cameras and HUDs: right after tick {@code n} ran it
 * starts at {@code n} and grows towards {@code n + speed} as the frame's partial tick goes from 0 to 1.
 */
public final class FxClock {
    private float speed;
    private double clock;
    private float step;     // what the last game tick added; time() interpolates over it, so a new speed never jumps
    private int next;

    public FxClock(float speed) {
        this(speed, 0);
    }

    /** A clock that starts at {@code startTick} without running the earlier ticks (late joiners, fast-forward). */
    public FxClock(float speed, int startTick) {
        this.speed = sanitize(speed);
        this.clock = Math.max(0, startTick);
        this.next = Math.max(0, startTick);
    }

    /** Negative and NaN speeds count as 0: the timeline cannot run backwards (its ticks already ran). */
    private static float sanitize(float speed) {
        return speed > 0 ? speed : 0f;
    }

    /** Advances one game tick, running {@code onTick} for every timeline tick crossed. */
    public void advance(IntConsumer onTick) {
        int until = (int) Math.floor(clock + 1e-6);
        while (next <= until) onTick.accept(next++);
        step = speed;
        clock += step;
    }

    public float speed() {
        return speed;
    }

    public boolean paused() {
        return speed == 0;
    }

    /**
     * Changes the speed from the next game tick on; 0 pauses (the tick at the current position still runs).
     * {@link #time} stays continuous. On an effect's clock use the handle instead ({@code FxHandle.speed},
     * {@code ClientFxHandle.speed}) so every side changes together.
     */
    public void setSpeed(float speed) {
        this.speed = sanitize(speed);
    }

    /**
     * Changes the speed at timeline position {@code at} (a {@link #clock()} value read elsewhere, usually on the
     * server), moving this clock there first so both change at the same point of the timeline. Moving back does not
     * run ticks again; moving forward runs the skipped ones on the next {@link #advance}.
     */
    public void setSpeed(float speed, double at) {
        this.speed = sanitize(speed);
        if (Double.isFinite(at)) this.clock = Math.max(0, at);
    }

    /** Number of timeline ticks already executed (the next one to run). */
    public int age() {
        return next;
    }

    /** Raw clock value in timeline ticks. */
    public double clock() {
        return clock;
    }

    /** Continuous timeline time in ticks for the frame being drawn. */
    public float time(float partial) {
        return (float) (clock - step + partial * step);
    }

    /** {@link #time(float)} in seconds of timeline. */
    public float seconds(float partial) {
        return time(partial) / 20f;
    }

    /** Converts timeline ticks to real seconds (what a countdown should display); infinite while paused. */
    public float realSeconds(float timelineTicks) {
        return timelineTicks == 0 ? 0 : timelineTicks / 20f / speed;
    }
}
