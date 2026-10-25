package me.julionxn.blocmpact.client.api;

import me.julionxn.blocmpact.api.Anchor;
import me.julionxn.blocmpact.api.FxParams;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * How an effect instance was started. Arrives from the server, from a command or from {@link ClientEffects#play}.
 *
 * @param type       effect type id
 * @param id         instance id (positive: started by the server; negative: local to this client)
 * @param anchor     where it lives
 * @param seed       seed for every random decision
 * @param speed      initial timeline speed (0 starts paused); see {@link VisualEffect#speed()} for the current one
 * @param startTick  first timeline tick to run (greater than 0 when joining a running effect)
 * @param params     parameters
 */
public record EffectSpawn(Identifier type, long id, Anchor anchor, long seed, float speed, int startTick, FxParams params) {

    public boolean isLocal() {
        return id < 0;
    }

    public EffectSpawn withParams(FxParams newParams) {
        return new EffectSpawn(type, id, anchor, seed, speed, startTick, newParams);
    }

    /** A local spawn description; the id is assigned by {@link ClientEffects#play(EffectSpawn)}. */
    public static EffectSpawn local(Identifier type, Anchor anchor, FxParams params) {
        return new EffectSpawn(type, 0, anchor, ThreadLocalRandom.current().nextLong(), 1f, 0, params);
    }

    public static EffectSpawn local(Identifier type, Vec3 position, FxParams params) {
        return local(type, Anchor.at(position), params);
    }

    public EffectSpawn withSeed(long newSeed) {
        return new EffectSpawn(type, id, anchor, newSeed, speed, startTick, params);
    }

    public EffectSpawn withSpeed(float newSpeed) {
        return new EffectSpawn(type, id, anchor, seed, newSpeed, startTick, params);
    }

    public EffectSpawn withId(long newId) {
        return new EffectSpawn(type, newId, anchor, seed, speed, startTick, params);
    }
}
