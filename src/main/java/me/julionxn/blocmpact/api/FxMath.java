package me.julionxn.blocmpact.api;

/**
 * Shared curves and helpers. Everything here is pure and lives in common code, so the server
 * (damage, terrain) and the client (shaders, particles, HUD) can drive their work with the very same functions.
 * <p>
 * Conventions: {@code t} is usually timeline ticks, {@code k} a normalized 0–1 progress, {@code s} seconds.
 */
public final class FxMath {
    public static final float PI = (float) Math.PI, TAU = (float) (Math.PI * 2);

    private FxMath() {
    }

    // ---- basics

    public static float clamp01(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static double clamp01(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static float clamp(float x, float lo, float hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }

    public static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    public static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    /** Where {@code x} sits between {@code a} and {@code b}, clamped to 0–1. */
    public static float progress(float x, float a, float b) {
        return clamp01((x - a) / (Math.abs(b - a) < 1e-6f ? 1e-6f : (b - a)));
    }

    /** Maps {@code x} from [a, b] to [c, d] (not clamped). */
    public static float remap(float x, float a, float b, float c, float d) {
        return c + (d - c) * (x - a) / (Math.abs(b - a) < 1e-6f ? 1e-6f : (b - a));
    }

    public static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    public static float step(float edge, float x) {
        return x < edge ? 0 : 1;
    }

    public static float smoothstep(float e0, float e1, float x) {
        return smooth(progress(x, e0, e1));
    }

    // ---- envelopes

    /**
     * Trapezoid: 0 before {@code in0}, rises to 1 at {@code in1}, holds, falls from {@code out0} to 0 at {@code out1}.
     * Almost every animated value can be written as a product of windows.
     */
    public static float window(float t, float in0, float in1, float out0, float out1) {
        float up = (t - in0) / Math.max(in1 - in0, 0.001f);
        float down = (t - out0) / Math.max(out1 - out0, 0.001f);
        return clamp01(up) * (1f - clamp01(down));
    }

    /** {@link #window} with smoothstep edges. */
    public static float smoothWindow(float t, float in0, float in1, float out0, float out1) {
        return smooth(progress(t, in0, in1)) * (1f - smooth(progress(t, out0, out1)));
    }

    /** Instant rise at {@code start} and exponential decay with rate {@code k} per unit of {@code t}. 0 before start. */
    public static float decay(float t, float start, float k) {
        return t < start ? 0 : (float) Math.exp(-(t - start) * k);
    }

    /** Fast attack, slow release: rises in roughly {@code 1/attack} and falls with {@code release}. */
    public static float impulse(float x, float attack, float release) {
        if (x <= 0) return 0;
        return (float) ((1 - Math.exp(-x * attack)) * Math.exp(-x * release));
    }

    /** Damped oscillation around 0 that starts at 1: springs, wobbles, recoil. */
    public static float spring(float x, float frequency, float damping) {
        if (x <= 0) return 1;
        return (float) (Math.cos(x * frequency * TAU) * Math.exp(-x * damping));
    }

    /** Square-of-the-input "trauma" response: small values stay calm, large values shake a lot. */
    public static float trauma(float x) {
        x = clamp01(x);
        return x * x;
    }

    // ---- easing (input and output 0–1)

    public static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    public static float smoother(float x) {
        x = clamp01(x);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    public static float easeInQuad(float x) {
        x = clamp01(x);
        return x * x;
    }

    public static float easeOutQuad(float x) {
        x = clamp01(x);
        return 1 - (1 - x) * (1 - x);
    }

    public static float easeInOutQuad(float x) {
        x = clamp01(x);
        return x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
    }

    public static float easeInCubic(float x) {
        x = clamp01(x);
        return x * x * x;
    }

    public static float easeOutCubic(float x) {
        x = clamp01(x);
        float u = 1 - x;
        return 1 - u * u * u;
    }

    public static float easeInOutCubic(float x) {
        x = clamp01(x);
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    public static float easeInExpo(float x) {
        x = clamp01(x);
        return x == 0 ? 0 : (float) Math.pow(2, 10 * x - 10);
    }

    public static float easeOutExpo(float x) {
        x = clamp01(x);
        return x == 1 ? 1 : 1 - (float) Math.pow(2, -10 * x);
    }

    /** Overshoots past 1 before settling; {@code s ≈ 1.7} is a classic amount. */
    public static float easeOutBack(float x, float s) {
        x = clamp01(x) - 1;
        return x * x * ((s + 1) * x + s) + 1;
    }

    public static float easeOutElastic(float x) {
        x = clamp01(x);
        if (x == 0 || x == 1) return x;
        return (float) (Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (TAU / 3)) + 1);
    }

    public static float easeOutBounce(float x) {
        x = clamp01(x);
        float n = 7.5625f, d = 2.75f;
        if (x < 1 / d) return n * x * x;
        if (x < 2 / d) return n * (x -= 1.5f / d) * x + 0.75f;
        if (x < 2.5 / d) return n * (x -= 2.25f / d) * x + 0.9375f;
        return n * (x -= 2.625f / d) * x + 0.984375f;
    }

    // ---- rhythm

    /**
     * Phase (in cycles) of a pulse whose frequency glides from {@code f0} to {@code f1} Hz between {@code start}
     * and {@code end} ticks, following {@code k^shape}. Because it is the integral of the frequency it never jumps.
     * Use {@code fract(phase)} for a sawtooth and {@code floor(phase)} to count beats. Negative before start.
     */
    public static float pulsePhase(float t, float start, float end, float f0, float f1, float shape) {
        float s = (t - start) / 20f;
        if (s < 0) return -1;
        float span = Math.max((end - start) / 20f, 1e-4f);
        if (s <= span) return f0 * s + (f1 - f0) * span * (float) Math.pow(s / span, shape + 1) / (shape + 1);
        return f0 * s + (f1 - f0) * (span / (shape + 1) + (s - span));
    }

    /** A beat shape from a phase: 1 right at each beat, falling with {@code sharpness}. */
    public static float beat(float phase, float sharpness) {
        if (phase < 0) return 0;
        return (float) Math.exp(-fract(phase) * sharpness);
    }

    public static float saw(float x) {
        return fract(x);
    }

    public static float triangle(float x) {
        return 1 - Math.abs(fract(x) * 2 - 1);
    }

    public static float square(float x, float duty) {
        return fract(x) < duty ? 1 : 0;
    }

    /** Smooth 0–1 oscillation at {@code hz} for {@code seconds}. */
    public static float wave(float seconds, float hz) {
        return 0.5f + 0.5f * (float) Math.sin(seconds * hz * TAU);
    }

    // ---- physics helpers

    /** Speed of sound in blocks per tick (340 m/s). */
    public static final float SOUND_BLOCKS_PER_TICK = 17f;

    /** Tick at which a sound emitted at {@code tick} arrives at {@code distance} blocks. */
    public static float soundArrival(float tick, double distance) {
        return tick + (float) (Math.max(0, distance) / SOUND_BLOCKS_PER_TICK);
    }

    /** Blast-wave style radius {@code R * (dt / duration)^power} (power ≈ 0.4–0.6 decelerates like a real blast). */
    public static double blastRadius(double dt, double radius, double duration, double power) {
        return dt <= 0 ? 0 : radius * Math.pow(dt / duration, power);
    }

    /** Inverse of {@link #blastRadius}: how long until the front reaches {@code distance}. */
    public static double blastTime(double distance, double radius, double duration, double power) {
        return duration * Math.pow(Math.max(distance, 0) / radius, 1 / power);
    }

    // ---- color

    public static int argb(float a, float r, float g, float b) {
        return Math.round(clamp01(a) * 255) << 24 | Math.round(clamp01(r) * 255) << 16 | Math.round(clamp01(g) * 255) << 8 | Math.round(clamp01(b) * 255);
    }

    /** Replaces the alpha of a packed color with {@code alpha} (0–1). */
    public static int withAlpha(int argb, float alpha) {
        return Math.round(clamp01(alpha) * 255) << 24 | argb & 0xFFFFFF;
    }

    /** Multiplies the alpha of a packed color by {@code k}. */
    public static int fadeAlpha(int argb, float k) {
        return withAlpha(argb, (argb >>> 24) / 255f * k);
    }

    public static float red(int argb) {
        return (argb >> 16 & 255) / 255f;
    }

    public static float green(int argb) {
        return (argb >> 8 & 255) / 255f;
    }

    public static float blue(int argb) {
        return (argb & 255) / 255f;
    }

    public static float alpha(int argb) {
        return (argb >>> 24) / 255f;
    }

    /** Component-wise interpolation of two packed colors, alpha included. */
    public static int lerpColor(int a, int b, float k) {
        k = clamp01(k);
        return argb(lerp(alpha(a), alpha(b), k), lerp(red(a), red(b), k), lerp(green(a), green(b), k), lerp(blue(a), blue(b), k));
    }

    /** HSV (all 0–1) to packed opaque color. */
    public static int hsv(float h, float s, float v) {
        h = fract(h) * 6;
        int i = (int) Math.floor(h);
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        return switch (i) {
            case 0 -> argb(1, v, u, p);
            case 1 -> argb(1, q, v, p);
            case 2 -> argb(1, p, v, u);
            case 3 -> argb(1, p, q, v);
            case 4 -> argb(1, u, p, v);
            default -> argb(1, v, p, q);
        };
    }

    // ---- hashing (stable on every machine)

    /** Deterministic hash of a seed and an index into [0, 1). */
    public static float hash(long seed, long index) {
        long x = seed ^ index * 0x9E3779B97F4A7C15L;
        x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
        x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / (float) (1L << 24);
    }

    /** {@link #hash} mapped to [lo, hi). */
    public static float hash(long seed, long index, float lo, float hi) {
        return lo + (hi - lo) * hash(seed, index);
    }

    /** Smooth 1D value noise in [0, 1], deterministic for a seed. */
    public static float noise(long seed, float x) {
        float i = (float) Math.floor(x), f = x - i;
        float u = f * f * (3 - 2 * f);
        return lerp(hash(seed, (long) i), hash(seed, (long) i + 1), u);
    }

    /** Fractal sum of {@link #noise}, roughly in [0, 1]. */
    public static float fbm(long seed, float x, int octaves) {
        float v = 0, a = 0.5f, norm = 0;
        for (int o = 0; o < octaves; o++) {
            v += a * noise(seed + o * 7919L, x);
            norm += a;
            x *= 2.03f;
            a *= 0.5f;
        }
        return v / norm;
    }
}
