package me.julionxn.blocmpact.client.api.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Initial velocity of each particle from its {@link Shape.Sample} (blocks per tick). Combine with {@link #plus}. */
@FunctionalInterface
public interface Velocity {
    Vec3 of(Shape.Sample sample, RandomSource random);

    Velocity NONE = (s, r) -> Vec3.ZERO;

    /** Along the sample direction (outward for rings and spheres): bursts. Negative = inward. */
    static Velocity along(double speed) {
        return (s, r) -> s.dir().scale(speed);
    }

    /** Along the sample direction with a random speed in [min, max]. */
    static Velocity along(double min, double max) {
        return (s, r) -> s.dir().scale(min + (max - min) * r.nextDouble());
    }

    static Velocity fixed(Vec3 v) {
        return (s, r) -> v;
    }

    static Velocity fixed(double x, double y, double z) {
        return fixed(new Vec3(x, y, z));
    }

    /** Random direction, uniform speed up to {@code speed}. */
    static Velocity random(double speed) {
        return (s, r) -> Shape.randomDir(r).scale(speed * r.nextDouble());
    }

    /** Arrives at {@code target} after {@code ticks} (for particles without drag): converging energy, suction. */
    static Velocity towards(Vec3 target, double ticks) {
        return (s, r) -> target.subtract(s.pos()).scale(1.0 / Math.max(ticks, 1e-3));
    }

    /** Away from a point, like a blast from {@code center}. */
    static Velocity awayFrom(Vec3 center, double speed) {
        return (s, r) -> {
            Vec3 d = s.pos().subtract(center);
            return d.lengthSqr() < 1e-9 ? Shape.randomDir(r).scale(speed) : d.normalize().scale(speed);
        };
    }

    /** Around an axis through {@code center} (swirl): tangent to circles around {@code axis}. */
    static Velocity swirl(Vec3 center, Vec3 axis, double speed) {
        Vec3 ax = axis.normalize();
        return (s, r) -> {
            Vec3 t = ax.cross(s.pos().subtract(center));
            return t.lengthSqr() < 1e-9 ? Vec3.ZERO : t.normalize().scale(speed);
        };
    }

    default Velocity plus(Velocity other) {
        return (s, r) -> of(s, r).add(other.of(s, r));
    }

    default Velocity scale(double k) {
        return (s, r) -> of(s, r).scale(k);
    }
}
