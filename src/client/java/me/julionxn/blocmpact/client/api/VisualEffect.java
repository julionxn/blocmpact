package me.julionxn.blocmpact.client.api;

import me.julionxn.blocmpact.api.Anchor;
import me.julionxn.blocmpact.api.FxClock;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.api.FxRandom;
import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.api.entity.EntityFx;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.api.geometry.GeometryContext;
import me.julionxn.blocmpact.client.api.hud.HudContext;
import me.julionxn.blocmpact.client.api.post.PassCollector;
import me.julionxn.blocmpact.client.api.post.ScreenFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client side of an effect: a timeline plus optional hooks, one per layer. Override only what the effect uses.
 *
 * <ul>
 *   <li>{@link #onTick} — once per timeline tick: particles, sounds, spawning children, state changes.</li>
 *   <li>{@link #passes} — level 3: full-screen shader passes over the image Minecraft just drew (OpenGL only).</li>
 *   <li>{@link #screen} — level 3: camera operations of the shared compositor (bloom, grading, flash, impact frames...).</li>
 *   <li>{@link #geometry} — level 2: meshes in the world through Minecraft's render API (any backend).</li>
 *   <li>{@link #hud} — level 1: 2D overlay.</li>
 *   <li>{@link #environment} — vanilla fog, sky, clouds, light and weather.</li>
 *   <li>{@link #camera} — camera shake, roll, FOV and offsets (any backend).</li>
 *   <li>{@link #entities} — outlines and model poses of entities.</li>
 * </ul>
 *
 * Time: {@link #time(float)} is continuous timeline ticks for drawing; {@code onTick(ctx, t)} receives whole ticks.
 * With a speed other than 1 both stretch together. The speed can change while the effect runs
 * ({@code FxHandle.speed}, {@link ClientFxHandle#speed(float)}); at 0 the timeline holds still: no {@code onTick} and a
 * frozen {@link #time}. All randomness must come from {@link #random} / {@link #hash} so every player sees the same thing.
 */
public abstract class VisualEffect {
    private final EffectSpawn spawn;
    private final FxClock clock;
    private final float seedF;
    private Vec3 prevPos, pos;
    private boolean finished, stopRequested, failed;
    private int stopTick = -1;

    protected VisualEffect(EffectSpawn spawn) {
        this.spawn = spawn;
        this.clock = new FxClock(spawn.speed(), spawn.startTick());
        this.seedF = FxRandom.seedF(spawn.seed());
        this.pos = this.prevPos = spawn.anchor().position();
    }

    // ------------------------------------------------------------------ hooks

    /** Called once, before the first tick. */
    protected void onStart(TickContext ctx) {
    }

    /** Called once per timeline tick ({@code tick} starts at {@link EffectSpawn#startTick()}). */
    protected void onTick(TickContext ctx, int tick) {
    }

    /** A named event sent by the server ({@code FxHandle.event}) or locally. */
    protected void onEvent(TickContext ctx, String name, FxParams data) {
    }

    /**
     * Someone asked the effect to stop (server, command, {@link ClientEffects#stop}). Default: finish now.
     * Override to play an outro and call {@link #finish()} when it ends; {@link #stopTick()} tells when it was asked.
     */
    protected void onStopRequested(TickContext ctx) {
        finish();
    }

    /** Called when the effect is removed, for whatever reason. */
    protected void onRemoved() {
    }

    /**
     * Timeline length in ticks; the effect ends after running tick {@code duration()}. Default -1: it runs until
     * {@link #finish()} or a stop request.
     */
    public int duration() {
        return -1;
    }

    /** Frame hooks are skipped when the camera is farther than this from the effect (default 512 blocks). */
    public double range() {
        return 512;
    }

    /** Level 3: append full-screen passes. Called every frame while in range. */
    public void passes(Frame frame, PassCollector out) {
    }

    /** Level 3: contribute to the shared compositor. Called every frame while in range. */
    public void screen(Frame frame, ScreenFx screen) {
    }

    /** Level 2: submit world geometry. Called every frame while in range. */
    public void geometry(GeometryContext ctx) {
    }

    /** Level 1: draw on the HUD. Called every frame while in range. */
    public void hud(HudContext ctx) {
    }

    /** Overrides of fog, sky, clouds, light and weather. Called every frame while in range. */
    public void environment(Frame frame, EnvironmentFx env) {
    }

    /** Camera shake, roll, FOV and offset. Called every frame while in range. */
    public void camera(Frame frame, CameraFx camera) {
    }

    /** Entity outlines and model poses. Called every frame while in range. */
    public void entities(Frame frame, EntityFx entities) {
    }

    /** True to pause every world sound except the {@code MASTER}, {@code PLAYERS} and {@code UI} categories. */
    public boolean silencing(Frame frame) {
        return false;
    }

    // ------------------------------------------------------------------ helpers for subclasses

    /** Ends the effect after the current tick. */
    protected final void finish() {
        finished = true;
    }

    public final EffectSpawn spawn() {
        return spawn;
    }

    public final Identifier type() {
        return spawn.type();
    }

    public final long id() {
        return spawn.id();
    }

    public final long seed() {
        return spawn.seed();
    }

    /** Seed folded into 0–1 for shaders. */
    public final float seedF() {
        return seedF;
    }

    /** Current timeline speed (0 = paused). {@link EffectSpawn#speed()} is the one it started with. */
    public final float speed() {
        return clock.speed();
    }

    public final FxParams params() {
        return spawn.params();
    }

    public final FxClock clock() {
        return clock;
    }

    /** Timeline ticks already executed. */
    public final int age() {
        return clock.age();
    }

    /** Continuous timeline time in ticks for this frame. */
    public final float time(float partial) {
        return clock.time(partial);
    }

    /** {@link #time} in seconds. */
    public final float seconds(float partial) {
        return clock.seconds(partial);
    }

    /** Seconds of timeline elapsed since {@code tick} (negative before it). */
    public final float since(float tick, float partial) {
        return (clock.time(partial) - tick) / 20f;
    }

    /** Anchor position at the last tick. */
    public final Vec3 origin() {
        return pos;
    }

    /** Anchor position interpolated for this frame. */
    public final Vec3 origin(float partial) {
        if (prevPos == pos) return pos;
        return new Vec3(FxMath.lerp(prevPos.x, pos.x, partial), FxMath.lerp(prevPos.y, pos.y, partial), FxMath.lerp(prevPos.z, pos.z, partial));
    }

    public final Anchor anchor() {
        return spawn.anchor();
    }

    /** The entity the effect follows, if any and loaded. */
    public final Entity anchorEntity(ClientLevel level) {
        return spawn.anchor().followsEntity() && level != null ? level.getEntity(spawn.anchor().entityId()) : null;
    }

    /** Deterministic random for a timeline tick. */
    public final RandomSource random(int tick) {
        return FxRandom.of(spawn.seed(), tick);
    }

    /** Deterministic random for a tick and a salt (independent streams in the same tick). */
    public final RandomSource random(int tick, int salt) {
        return FxRandom.of(spawn.seed(), tick, salt);
    }

    /** Deterministic number in [0, 1) for an index. */
    public final float hash(long index) {
        return FxMath.hash(spawn.seed(), index);
    }

    public final boolean stopping() {
        return stopRequested;
    }

    /** Timeline tick at which a stop was requested, or -1. */
    public final int stopTick() {
        return stopTick;
    }

    public boolean isDone() {
        return finished || failed || duration() >= 0 && age() > duration();
    }

    // ------------------------------------------------------------------ internal plumbing

    @ApiStatus.Internal
    public final void internalStart(TickContext ctx) {
        this.prevPos = this.pos = spawn.anchor().resolve(ctx.level(), 1f);
        onStart(ctx);
    }

    @ApiStatus.Internal
    public final void internalTick(TickContext.Factory contexts) {
        ClientLevel level = contexts.level();
        prevPos = pos;
        pos = spawn.anchor().resolve(level, 1f);
        clock.advance(t -> {
            if (!isDone()) onTick(contexts.at(t), t);
        });
    }

    @ApiStatus.Internal
    public final void internalStop(TickContext ctx, boolean immediate) {
        if (immediate || clock.paused()) {      // a frozen outro would never end
            finished = true;
            return;
        }
        if (stopRequested) return;
        stopRequested = true;
        stopTick = clock.age();
        onStopRequested(ctx);
    }

    /** {@code at}: timeline position to move to first (the server's), or NaN to change right here. */
    @ApiStatus.Internal
    public final void internalSpeed(float speed, double at) {
        if (stopRequested || finished) return;
        clock.setSpeed(speed, at);
    }

    @ApiStatus.Internal
    public final void internalEvent(TickContext ctx, String name, FxParams data) {
        onEvent(ctx, name, data);
    }

    @ApiStatus.Internal
    public final void internalFail() {
        failed = true;
    }

    @ApiStatus.Internal
    public final void internalRemoved() {
        onRemoved();
    }
}
