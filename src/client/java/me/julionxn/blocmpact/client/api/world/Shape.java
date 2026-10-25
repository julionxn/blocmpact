package me.julionxn.blocmpact.client.api.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where particles appear: a function from (index, count, random) to a position and a direction (outward normal,
 * tangent or axis, depending on the shape). Combine with a {@link Velocity} in {@link Particles#emit}.
 * Shapes with regular spacing (ring, sphere, helix, line) ignore the random so they read as clean forms.
 */
@FunctionalInterface
public interface Shape {
    Sample sample(int index, int count, RandomSource random);

    /** A point and a direction. */
    record Sample(Vec3 pos, Vec3 dir) {
    }

    Vec3 UP = new Vec3(0, 1, 0);

    static Shape point(Vec3 p) {
        return (i, n, r) -> new Sample(p, randomDir(r));
    }

    /** Evenly spaced points on a circle; {@code phase} in radians rotates it (animate it to spin). dir = outward. */
    static Shape ring(Vec3 center, Vec3 normal, double radius, double phase) {
        Vec3[] b = basis(normal);
        return (i, n, r) -> {
            double a = phase + i * Math.PI * 2 / n;
            Vec3 d = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            return new Sample(center.add(d.scale(radius)), d);
        };
    }

    /** Horizontal ring. */
    static Shape ring(Vec3 center, double radius, double phase) {
        return ring(center, UP, radius, phase);
    }

    /** Filled disc with uniform density (sunflower pattern). dir = outward in the plane. */
    static Shape disc(Vec3 center, Vec3 normal, double radius) {
        Vec3[] b = basis(normal);
        double golden = Math.PI * (3 - Math.sqrt(5));
        return (i, n, r) -> {
            double rr = radius * Math.sqrt((i + 0.5) / n), a = i * golden;
            Vec3 d = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            return new Sample(center.add(d.scale(rr)), d);
        };
    }

    /** Uniform points on a sphere surface (Fibonacci spiral, no clumping at the poles). dir = outward. */
    static Shape sphere(Vec3 center, double radius) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        return (i, n, r) -> {
            double y = 1 - 2 * (i + 0.5) / n, k = Math.sqrt(1 - y * y), a = golden * i;
            Vec3 d = new Vec3(Math.cos(a) * k, y, Math.sin(a) * k);
            return new Sample(center.add(d.scale(radius)), d);
        };
    }

    /** Upper half of a sphere. */
    static Shape dome(Vec3 center, double radius) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        return (i, n, r) -> {
            double y = 1 - (i + 0.5) / n, k = Math.sqrt(1 - y * y), a = golden * i;
            Vec3 d = new Vec3(Math.cos(a) * k, y, Math.sin(a) * k);
            return new Sample(center.add(d.scale(radius)), d);
        };
    }

    /** Random points inside a ball. dir = outward. */
    static Shape ball(Vec3 center, double radius) {
        return (i, n, r) -> {
            Vec3 d = randomDir(r);
            return new Sample(center.add(d.scale(radius * Math.cbrt(r.nextDouble()))), d);
        };
    }

    /** Helix around an axis: auras, tornados, columns. {@code phase} spins it. dir = tangent. */
    static Shape helix(Vec3 base, Vec3 axis, double radius, double height, double turns, double phase) {
        Vec3 ax = axis.normalize();
        Vec3[] b = basis(ax);
        return (i, n, r) -> {
            double k = n <= 1 ? 0 : i / (double) (n - 1), a = phase + k * turns * Math.PI * 2;
            Vec3 out = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            Vec3 tangent = ax.cross(out);
            return new Sample(base.add(ax.scale(k * height)).add(out.scale(radius)), tangent);
        };
    }

    /** Evenly spaced points on a segment (trails, beams, connections). dir = along the segment. */
    static Shape line(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        Vec3 dir = d.lengthSqr() < 1e-12 ? UP : d.normalize();
        return (i, n, r) -> new Sample(a.add(d.scale(n <= 1 ? 0.5 : i / (double) (n - 1))), dir);
    }

    /**
     * Horizontal arc in front of a yaw (degrees, Minecraft convention): weapon swings.
     * {@code from}/{@code to} are degrees relative to the yaw. dir = outward.
     */
    static Shape arc(Vec3 center, float yaw, double radius, double from, double to) {
        return (i, n, r) -> {
            double a = Math.toRadians(yaw + from + (to - from) * (n <= 1 ? 0.5 : i / (n - 1.0)));
            Vec3 d = new Vec3(-Math.sin(a), 0, Math.cos(a));
            return new Sample(center.add(d.scale(radius)), d);
        };
    }

    /** Random directions inside a cone (sprays, jets, muzzle flashes). Points at the apex; dir = spray direction. */
    static Shape cone(Vec3 apex, Vec3 direction, double halfAngleDegrees) {
        Vec3 ax = direction.normalize();
        Vec3[] b = basis(ax);
        double cosMax = Math.cos(Math.toRadians(halfAngleDegrees));
        return (i, n, r) -> {
            double c = 1 - r.nextDouble() * (1 - cosMax), s = Math.sqrt(1 - c * c), a = r.nextDouble() * Math.PI * 2;
            Vec3 d = ax.scale(c).add(b[0].scale(Math.cos(a) * s)).add(b[1].scale(Math.sin(a) * s));
            return new Sample(apex, d);
        };
    }

    /** Random points inside a box. dir = random. */
    static Shape box(AABB box) {
        return (i, n, r) -> new Sample(new Vec3(box.minX + r.nextDouble() * box.getXsize(), box.minY + r.nextDouble() * box.getYsize(),
            box.minZ + r.nextDouble() * box.getZsize()), randomDir(r));
    }

    /** Archimedean spiral in a plane from {@code r0} to {@code r1}. dir = outward. */
    static Shape spiral(Vec3 center, Vec3 normal, double r0, double r1, double turns, double phase) {
        Vec3[] b = basis(normal);
        return (i, n, r) -> {
            double k = n <= 1 ? 0 : i / (double) (n - 1), a = phase + k * turns * Math.PI * 2, rr = r0 + (r1 - r0) * k;
            Vec3 d = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            return new Sample(center.add(d.scale(rr)), d);
        };
    }

    /** Moves every sample by a random offset up to {@code amount} blocks. */
    default Shape jitter(double amount) {
        return (i, n, r) -> {
            Sample s = sample(i, n, r);
            return new Sample(s.pos().add((r.nextDouble() - 0.5) * 2 * amount, (r.nextDouble() - 0.5) * 2 * amount,
                (r.nextDouble() - 0.5) * 2 * amount), s.dir());
        };
    }

    /** Projects every sample down onto the terrain (keeps it if the chunk is not loaded). */
    default Shape onGround(net.minecraft.world.level.Level level, double lift) {
        return (i, n, r) -> {
            Sample s = sample(i, n, r);
            double y = Terrain.groundY(level, s.pos().x, s.pos().z, s.pos().y);
            return new Sample(new Vec3(s.pos().x, y + lift, s.pos().z), s.dir());
        };
    }

    // ---- helpers

    static Vec3 randomDir(RandomSource r) {
        double y = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, k = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(a) * k, y, Math.sin(a) * k);
    }

    /** Two unit vectors perpendicular to {@code normal} and to each other. */
    static Vec3[] basis(Vec3 normal) {
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 t = ref.cross(n).normalize();
        return new Vec3[]{t, n.cross(t)};
    }
}
