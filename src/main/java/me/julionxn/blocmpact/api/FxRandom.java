package me.julionxn.blocmpact.api;

import net.minecraft.util.RandomSource;

/**
 * Deterministic randomness derived from an effect's seed. Every client that receives the same seed sees the same
 * particles, the same glyphs and the same lightning, without sending anything else over the network.
 * Never use {@code Math.random()} inside an effect.
 */
public final class FxRandom {
    private FxRandom() {
    }

    /** Random source for a given timeline tick. */
    public static RandomSource of(long seed, int tick) {
        return RandomSource.create(seed ^ tick * 341873128712L);
    }

    /** Random source for a tick and an extra salt (to separate independent streams in the same tick). */
    public static RandomSource of(long seed, int tick, int salt) {
        return RandomSource.create(seed ^ tick * 341873128712L ^ salt * 132897987541L);
    }

    /** The seed folded into 0–1, handy as a shader uniform. */
    public static float seedF(long seed) {
        return (seed & 0xFFFF) / 65535f;
    }
}
