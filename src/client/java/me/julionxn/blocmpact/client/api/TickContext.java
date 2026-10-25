package me.julionxn.blocmpact.client.api;

import me.julionxn.blocmpact.api.Anchor;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.world.Particles;
import me.julionxn.blocmpact.client.api.world.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a tick hook can use: the level, the tick's deterministic random, and level-0 helpers already bound to them.
 */
public final class TickContext {
    private final Minecraft minecraft;
    private final ClientLevel level;
    private final VisualEffect effect;
    private final int tick;
    private final Vec3 camera;
    private RandomSource random;
    private Particles particles;
    private Sounds sounds;

    public TickContext(Minecraft minecraft, ClientLevel level, VisualEffect effect, int tick, Vec3 camera) {
        this.minecraft = minecraft;
        this.level = level;
        this.effect = effect;
        this.tick = tick;
        this.camera = camera;
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public ClientLevel level() {
        return level;
    }

    public VisualEffect effect() {
        return effect;
    }

    /** Timeline tick being executed. */
    public int tick() {
        return tick;
    }

    /** Camera position (for distance-based level of detail and sound placement). */
    public Vec3 camera() {
        return camera;
    }

    /** The effect's anchor position this tick. */
    public Vec3 origin() {
        return effect.origin();
    }

    /** Deterministic random for this tick (same on every client). */
    public RandomSource random() {
        if (random == null) random = effect.random(tick);
        return random;
    }

    /** Particle helpers bound to this level, tick random and camera. */
    public Particles particles() {
        if (particles == null) particles = new Particles(level, random(), camera);
        return particles;
    }

    /** Sound helpers (positional, head-locked, edge-of-zone, delayed by distance). */
    public Sounds sounds() {
        if (sounds == null) sounds = new Sounds(camera, random());
        return sounds;
    }

    /** Starts another effect locally (a child), deriving its seed from this effect. */
    public ClientFxHandle spawn(Identifier type, Anchor anchor, FxParams params) {
        EffectSpawn s = new EffectSpawn(type, 0, anchor, effect.seed() * 31 + tick * 7L + type.hashCode(), effect.speed(), 0, params);
        return ClientEffects.play(s);
    }

    public ClientFxHandle spawn(Identifier type, Vec3 position, FxParams params) {
        return spawn(type, Anchor.at(position), params);
    }

    /** Creates tick contexts lazily for an effect (internal). */
    public interface Factory {
        ClientLevel level();

        TickContext at(int tick);
    }
}
