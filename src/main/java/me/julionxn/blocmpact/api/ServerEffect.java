package me.julionxn.blocmpact.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server half of an effect: gameplay that must follow the same timeline as the visuals (damage, knockback,
 * block changes). It runs the same {@link FxClock} with the same speed and seed as every client, so a function
 * shared through common code (a radius, a front) hits exactly where the shader draws.
 * <p>
 * Attach it with {@link Effects.Spawn#server(ServerEffect)} or register it for a type with {@link ServerEffects}.
 */
public abstract class ServerEffect {
    private ServerLevel level;
    private FxHandle handle;
    private Anchor anchor;
    private long seed;
    private FxParams params = FxParams.EMPTY;
    private FxClock clock;
    private boolean finished, stopRequested;

    /** Called once by Blocmpact before the first tick. */
    final void bind(ServerLevel level, FxHandle handle, Anchor anchor, long seed, float speed, FxParams params) {
        this.level = level;
        this.handle = handle;
        this.anchor = anchor;
        this.seed = seed;
        this.params = params;
        this.clock = new FxClock(speed);
    }

    /** Called every server tick by Blocmpact. Returns true when the server side is over. */
    final boolean tickInternal() {
        if (!finished) clock.advance(t -> {
            if (!finished) onTick(t);
        });
        return finished || isDone();
    }

    final void stopInternal() {
        if (stopRequested) return;
        stopRequested = true;
        onStopRequested();
    }

    final void eventInternal(String name, FxParams data) {
        onEvent(name, data);
    }

    final void speedInternal(float speed) {
        clock.setSpeed(speed);
    }

    // ---- what subclasses implement

    /** One timeline tick. */
    protected abstract void onTick(int tick);

    /** True once the server part has nothing left to do. Default: never (until {@link #finish()} or a stop). */
    public boolean isDone() {
        return false;
    }

    /** The effect was asked to stop. Default: finish immediately. */
    protected void onStopRequested() {
        finish();
    }

    /** An event sent through {@link FxHandle#event}. */
    protected void onEvent(String name, FxParams data) {
    }

    /** Called when the server part is removed. */
    protected void onRemoved() {
    }

    // ---- helpers

    protected final void finish() {
        finished = true;
    }

    public final ServerLevel level() {
        return level;
    }

    public final FxHandle handle() {
        return handle;
    }

    public final Anchor anchor() {
        return anchor;
    }

    /** Current position of the anchor (follows the entity while it exists). */
    public final Vec3 origin() {
        return anchor.resolve(level, 1f);
    }

    public final Entity anchorEntity() {
        return anchor.followsEntity() ? level.getEntity(anchor.entityId()) : null;
    }

    public final long seed() {
        return seed;
    }

    public final FxParams params() {
        return params;
    }

    public final FxClock clock() {
        return clock;
    }

    /** Timeline ticks already executed. */
    public final int age() {
        return clock.age();
    }

    public final boolean stopping() {
        return stopRequested;
    }

    /** Deterministic random for a tick: identical to the client's {@code random(tick)}. */
    public final RandomSource random(int tick) {
        return FxRandom.of(seed, tick);
    }
}
