package me.julionxn.blocmpact.api;

import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * A keyframed curve with any number of components (1 for a scalar, 3 for a position, 4 for a color...).
 * Values outside the first and last key are held. Shared by server and client.
 *
 * <pre>{@code
 * Keyframes height = Keyframes.builder(1).interp(Keyframes.Interp.CUBIC)
 *     .key(0, 9000).key(40, 600).key(48, 20).build();
 * float h = height.get(t);
 * }</pre>
 *
 * For values that span many orders of magnitude (distances, zoom), key their {@code log10} and use
 * {@code Math.pow(10, curve.get(t))}: the motion then feels uniform at every scale.
 */
public final class Keyframes {
    public enum Interp {
        /** Jumps at each key. */
        STEP,
        /** Straight segments. */
        LINEAR,
        /** Smoothstep between keys: stops at every key. */
        SMOOTH,
        /** Monotone cubic (PCHIP, Fritsch–Carlson): smooth through the keys and never overshoots them. */
        CUBIC
    }

    private final float[] times;
    private final float[][] values; // [key][component]
    private final float[][] slopes; // [key][component], only for CUBIC
    private final int dims;
    private final Interp interp;

    private Keyframes(float[] times, float[][] values, int dims, Interp interp) {
        this.times = times;
        this.values = values;
        this.dims = dims;
        this.interp = interp;
        this.slopes = interp == Interp.CUBIC ? pchipSlopes(times, values, dims) : null;
    }

    public static Builder builder(int dims) {
        return new Builder(dims);
    }

    /** Scalar curve from alternating {@code time, value} pairs. */
    public static Keyframes of(Interp interp, float... timeValuePairs) {
        Builder b = builder(1).interp(interp);
        for (int i = 0; i + 1 < timeValuePairs.length; i += 2) b.key(timeValuePairs[i], timeValuePairs[i + 1]);
        return b.build();
    }

    public int dims() {
        return dims;
    }

    public float start() {
        return times[0];
    }

    public float end() {
        return times[times.length - 1];
    }

    /** First component at time {@code t}. */
    public float get(float t) {
        return get(t, 0);
    }

    /** Component {@code c} at time {@code t}. */
    public float get(float t, int c) {
        int n = times.length;
        if (n == 1 || t <= times[0]) return values[0][c];
        if (t >= times[n - 1]) return values[n - 1][c];
        int i = segment(t);
        float t0 = times[i], t1 = times[i + 1], h = t1 - t0, k = (t - t0) / h;
        float a = values[i][c], b = values[i + 1][c];
        return switch (interp) {
            case STEP -> a;
            case LINEAR -> a + (b - a) * k;
            case SMOOTH -> a + (b - a) * k * k * (3 - 2 * k);
            case CUBIC -> {
                float k2 = k * k, k3 = k2 * k;
                float h00 = 2 * k3 - 3 * k2 + 1, h10 = k3 - 2 * k2 + k, h01 = -2 * k3 + 3 * k2, h11 = k3 - k2;
                yield h00 * a + h10 * h * slopes[i][c] + h01 * b + h11 * h * slopes[i + 1][c];
            }
        };
    }

    /** All components at time {@code t}, written into {@code out} (allocated if null). */
    public float[] get(float t, float[] out) {
        if (out == null || out.length < dims) out = new float[dims];
        for (int c = 0; c < dims; c++) out[c] = get(t, c);
        return out;
    }

    /** First three components as a vector. */
    public Vec3 getVec3(float t) {
        return new Vec3(get(t, 0), dims > 1 ? get(t, 1) : 0, dims > 2 ? get(t, 2) : 0);
    }

    private int segment(float t) {
        int i = Arrays.binarySearch(times, t);
        if (i >= 0) return Math.min(i, times.length - 2);
        return Math.max(0, Math.min(-i - 2, times.length - 2));
    }

    private static float[][] pchipSlopes(float[] x, float[][] y, int dims) {
        int n = x.length;
        float[][] m = new float[n][dims];
        if (n < 2) return m;
        for (int c = 0; c < dims; c++) {
            float[] d = new float[n - 1];
            for (int i = 0; i < n - 1; i++) d[i] = (y[i + 1][c] - y[i][c]) / (x[i + 1] - x[i]);
            m[0][c] = d[0];
            m[n - 1][c] = d[n - 2];
            for (int i = 1; i < n - 1; i++) {
                if (d[i - 1] * d[i] <= 0) {
                    m[i][c] = 0;
                } else {
                    float h0 = x[i] - x[i - 1], h1 = x[i + 1] - x[i];
                    float w1 = 2 * h1 + h0, w2 = h1 + 2 * h0;
                    m[i][c] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]);
                }
            }
        }
        return m;
    }

    public static final class Builder {
        private final int dims;
        private Interp interp = Interp.LINEAR;
        private final java.util.TreeMap<Float, float[]> keys = new java.util.TreeMap<>();

        private Builder(int dims) {
            if (dims < 1) throw new IllegalArgumentException("dims must be >= 1");
            this.dims = dims;
        }

        public Builder interp(Interp interp) {
            this.interp = interp;
            return this;
        }

        public Builder key(float time, float... value) {
            if (value.length != dims) throw new IllegalArgumentException("expected " + dims + " components, got " + value.length);
            keys.put(time, value.clone());
            return this;
        }

        public Builder key(float time, Vec3 value) {
            return key(time, (float) value.x, (float) value.y, (float) value.z);
        }

        public Keyframes build() {
            if (keys.isEmpty()) throw new IllegalStateException("no keys");
            float[] t = new float[keys.size()];
            float[][] v = new float[keys.size()][];
            int i = 0;
            for (var e : keys.entrySet()) {
                t[i] = e.getKey();
                v[i++] = e.getValue();
            }
            return new Keyframes(t, v, dims, interp);
        }
    }
}
