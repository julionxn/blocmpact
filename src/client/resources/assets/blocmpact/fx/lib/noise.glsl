// blocmpact:lib/noise.glsl — procedural noise. #include "blocmpact:lib/noise.glsl"
// All functions return roughly 0–1 unless noted. For world-anchored noise use (scenePos + CameraPos) as input.

// Quintic fade (Perlin, "Improving Noise", 2002): continuous slope and curvature across cell borders.
vec2 noiseFade(vec2 f) { return f * f * f * (f * (f * 6.0 - 15.0) + 10.0); }
vec3 noiseFade(vec3 f) { return f * f * f * (f * (f * 6.0 - 15.0) + 10.0); }

// ---- value noise: random heights on the lattice, blended smoothly (cheap; a little blocky at low octaves)
float vnoise2(vec2 p) {
    vec2 cell = floor(p), s = noiseFade(p - cell);
    float a = hash12(cell), b = hash12(cell + vec2(1.0, 0.0));
    float c = hash12(cell + vec2(0.0, 1.0)), d = hash12(cell + vec2(1.0, 1.0));
    return a + (b - a) * s.x + (c - a) * s.y + (a - b - c + d) * s.x * s.y;    // bilinear, expanded
}
float vnoise3(vec3 p) {
    vec3 cell = floor(p), s = noiseFade(p - cell);
    vec4 lower = vec4(hash13(cell), hash13(cell + vec3(1, 0, 0)), hash13(cell + vec3(0, 1, 0)), hash13(cell + vec3(1, 1, 0)));
    vec4 upper = vec4(hash13(cell + vec3(0, 0, 1)), hash13(cell + vec3(1, 0, 1)), hash13(cell + vec3(0, 1, 1)), hash13(cell + vec3(1, 1, 1)));
    vec4 z = mix(lower, upper, s.z);
    vec2 y = mix(z.xy, z.zw, s.y);
    return mix(y.x, y.y, s.x);
}

// ---- gradient noise: random slopes on the lattice (smoother, no grid look)
vec2 latticeSlope2(vec2 cell) { float a = hash12(cell) * TAU; return vec2(cos(a), sin(a)); }
vec3 latticeSlope3(vec3 cell) { vec3 g = hash33(cell) - 0.5; return g / max(length(g), 1e-4); }
float gnoise2(vec2 p) {
    vec2 cell = floor(p), f = p - cell, s = noiseFade(f);
    float a = dot(latticeSlope2(cell), f);
    float b = dot(latticeSlope2(cell + vec2(1.0, 0.0)), f - vec2(1.0, 0.0));
    float c = dot(latticeSlope2(cell + vec2(0.0, 1.0)), f - vec2(0.0, 1.0));
    float d = dot(latticeSlope2(cell + vec2(1.0, 1.0)), f - vec2(1.0, 1.0));
    float n = a + (b - a) * s.x + (c - a) * s.y + (a - b - c + d) * s.x * s.y;
    return saturate(0.5 + n * 0.7);
}
float gnoise3(vec3 p) {
    vec3 cell = floor(p), f = p - cell, s = noiseFade(f);
    vec4 lower = vec4(dot(latticeSlope3(cell), f),
                      dot(latticeSlope3(cell + vec3(1, 0, 0)), f - vec3(1, 0, 0)),
                      dot(latticeSlope3(cell + vec3(0, 1, 0)), f - vec3(0, 1, 0)),
                      dot(latticeSlope3(cell + vec3(1, 1, 0)), f - vec3(1, 1, 0)));
    vec4 upper = vec4(dot(latticeSlope3(cell + vec3(0, 0, 1)), f - vec3(0, 0, 1)),
                      dot(latticeSlope3(cell + vec3(1, 0, 1)), f - vec3(1, 0, 1)),
                      dot(latticeSlope3(cell + vec3(0, 1, 1)), f - vec3(0, 1, 1)),
                      dot(latticeSlope3(cell + vec3(1, 1, 1)), f - vec3(1, 1, 1)));
    vec4 z = mix(lower, upper, s.z);
    vec2 y = mix(z.xy, z.zw, s.y);
    return saturate(0.5 + mix(y.x, y.y, s.x) * 0.6);
}

// ---- fractal sums (octaves capped at 8). Each octave is turned so the lattice axes never line up.
const mat2 OCTAVE_TURN2 = mat2(0.4536, 0.8912, -0.8912, 0.4536);                                   // 1.1 rad
const mat3 OCTAVE_TURN3 = mat3(0.6116, 0.7119, -0.3451, -0.5924, 0.7012, 0.3967, 0.5244, -0.0381, 0.8506);   // 0.95 rad about (1,2,3)
float fbm2(vec2 p, int octaves) {
    float sum = 0.0, amp = 1.0, total = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += vnoise2(p) * amp;
        total += amp;
        p = OCTAVE_TURN2 * p * 1.97 + vec2(3.7, 1.9);
        amp *= 0.52;
    }
    return sum / max(total, 1e-4);
}
float fbm3(vec3 p, int octaves) {
    float sum = 0.0, amp = 1.0, total = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += vnoise3(p) * amp;
        total += amp;
        p = OCTAVE_TURN3 * p * 1.95 + vec3(2.3, 5.1, 0.7);
        amp *= 0.5;
    }
    return sum / max(total, 1e-4);
}
float fbm2(vec2 p) { return fbm2(p, 5); }
float fbm3(vec3 p) { return fbm3(p, 4); }
// Sharp creases: lightning-like veins, ridges, cracks.
float ridged2(vec2 p, int octaves) {
    float sum = 0.0, amp = 1.0, total = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        float crease = 1.0 - abs(gnoise2(p) * 2.0 - 1.0);
        sum += crease * crease * amp;
        total += amp;
        p = OCTAVE_TURN2 * p * 2.07 + vec2(1.3, 6.1);
        amp *= 0.5;
    }
    return sum / max(total, 1e-4);
}
// Billowy: smoke, clouds.
float turbulence2(vec2 p, int octaves) {
    float sum = 0.0, amp = 1.0, total = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += abs(gnoise2(p) * 2.0 - 1.0) * amp;
        total += amp;
        p = OCTAVE_TURN2 * p * 1.93 + vec2(8.2, 0.4);
        amp *= 0.55;
    }
    return sum / max(total, 1e-4);
}

// ---- cellular (Worley). voronoi2: x = distance to the nearest feature point, y = to the second nearest,
// z = id (0–1) of the nearest cell. voronoi3: (nearest, second nearest).
vec3 voronoi2(vec2 p) {
    vec2 cell = floor(p), f = p - cell;
    float nearest = 8.0, second = 8.0, id = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 o = vec2(float(i), float(j));
            float d = length(o + hash22(cell + o) - f);
            if (d < nearest) {
                second = nearest;
                nearest = d;
                id = hash12(cell + o + 0.37);
            } else if (d < second) {
                second = d;
            }
        }
    }
    return vec3(nearest, second, id);
}
vec2 voronoi3(vec3 p) {
    vec3 cell = floor(p), f = p - cell;
    float nearest = 8.0, second = 8.0;
    for (int k = -1; k <= 1; k++) {
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec3 o = vec3(float(i), float(j), float(k));
                float d = length(o + hash33(cell + o) - f);
                second = d < nearest ? nearest : min(second, d);
                nearest = min(nearest, d);
            }
        }
    }
    return vec2(nearest, second);
}
// Cell borders (0 inside a cell, 1 on the edges): cracks, crystals, scales, hex-like shields.
float cellEdges(vec2 p, float width) { vec3 v = voronoi2(p); return saturate(1.0 - (v.y - v.x) / max(width, 1e-4)); }

// ---- flow
// Distorts p with noise of itself: marbled, fluid, magical swirls.
vec2 warp2(vec2 p, float amount) {
    vec2 q = vec2(fbm2(p + vec2(4.1, 0.3)), fbm2(p - vec2(2.6, 7.9)));
    return p + (q - 0.5) * 2.0 * amount;
}
// Divergence-free 2D flow field: the rotated gradient of a noise potential (particle-like streams).
vec2 curl2(vec2 p) {
    const float e = 0.02;
    float dx = gnoise2(p + vec2(e, 0.0)) - gnoise2(p - vec2(e, 0.0));
    float dy = gnoise2(p + vec2(0.0, e)) - gnoise2(p - vec2(0.0, e));
    return vec2(dy, -dx) / (2.0 * e);
}
