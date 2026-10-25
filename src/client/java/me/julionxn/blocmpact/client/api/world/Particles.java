package me.julionxn.blocmpact.client.api.world;

import me.julionxn.blocmpact.client.impl.BlocmpactConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Level-0 particle helpers bound to a level, a deterministic random and the camera (for level of detail).
 * Any {@link ParticleOptions} works: vanilla ones, {@code FxParticles.options()...build()}, or your own.
 *
 * <pre>{@code
 * ctx.particles().emit(ParticleTypes.END_ROD, Shape.ring(origin, 3, tick * 0.1), 24, Velocity.fixed(0, 0.05, 0));
 * ctx.particles().emit(spark, Shape.sphere(origin, 0.5), ctx.particles().lod(150, origin, 32), Velocity.along(0.2, 0.6));
 * }</pre>
 */
public final class Particles {
    private final ClientLevel level;
    private final RandomSource random;
    private final Vec3 camera;

    public Particles(ClientLevel level, RandomSource random, Vec3 camera) {
        this.level = level;
        this.random = random;
        this.camera = camera;
    }

    public RandomSource random() {
        return random;
    }

    /** One particle, forced visible from far away. */
    public Particles spawn(ParticleOptions particle, Vec3 pos, Vec3 velocity) {
        level.addParticle(particle, true, false, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
        return this;
    }

    /** {@code count} particles distributed by {@code shape}, each with its velocity. */
    public Particles emit(ParticleOptions particle, Shape shape, int count, Velocity velocity) {
        for (int i = 0; i < count; i++) {
            Shape.Sample s = shape.sample(i, count, random);
            spawn(particle, s.pos(), velocity.of(s, random));
        }
        return this;
    }

    /**
     * Scales a particle count down with distance from the camera: full count within {@code fullDetail} blocks, then
     * falling off; also applies the user's particle density setting. Never below 1 when {@code count > 0}.
     */
    public int lod(int count, Vec3 at, double fullDetail) {
        if (count <= 0) return 0;
        double d = camera.distanceTo(at);
        double k = d <= fullDetail ? 1 : fullDetail / d;
        return Math.max(1, (int) Math.round(count * k * BlocmpactConfig.get().particleDensity));
    }

    /** True with probability {@code p} (deterministic). */
    public boolean chance(double p) {
        return random.nextDouble() < p;
    }
}
