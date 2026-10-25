// blocmpact:lib/screen.glsl — image-space tools: distortions (apply to the uv BEFORE reading the scene),
// filters (read DiffuseSampler several times) and masks. #include "blocmpact:lib/screen.glsl"
// Order inside a pass: move the uv → read → change the color.

// ---- uv distortions
vec2 wobble(vec2 uv, float amount, float t) { return uv + vec2(sin(uv.y * 18.0 + t * 2.3), cos(uv.x * 14.0 + t * 1.7)) * 0.006 * amount; }
// Smooth pseudo-noise from crossed, self-modulated sine waves: 0–1, no lattice, cheap (no noise.glsl needed).
float screenNoise(vec2 p) {
    float a = sin(p.x * 1.71 + sin(p.y * 2.37 + 0.4));
    float b = sin(p.y * 1.33 + sin(p.x * 2.91 + 1.7));
    float c = sin((p.x + p.y) * 0.83 + 2.2);
    return 0.5 + (a * b * 0.7 + c * 0.3) * 0.5;
}
// Hot air shimmer (amount ≈ 0.002–0.006). Keep it local: multiply amount by a mask around the heat source.
vec2 heatHaze(vec2 uv, float amount, float t) {
    vec2 n = vec2(screenNoise(uv * vec2(90.0, 50.0) + vec2(0.0, t * 9.0)), screenNoise(uv * vec2(70.0, 40.0) - vec2(t * 7.0, 0.0)));
    return uv + (n - 0.5) * amount;
}
vec2 swirl(vec2 uv, vec2 center, float radius, float angle) {
    vec2 asp = vec2(OutSize.x / OutSize.y, 1.0), d = (uv - center) * asp;
    float k = saturate(1.0 - length(d) / radius), a = angle * k * k;
    return center + rot2(a) * d / asp;
}
vec2 pixelate(vec2 uv, float rows) { vec2 g = vec2(rows * OutSize.x / OutSize.y, rows); return (floor(uv * g) + 0.5) / g; }
vec2 barrel(vec2 uv, float k) { vec2 d = uv - 0.5; return 0.5 + d * (1.0 + k * dot(d, d)); }
vec2 pinch(vec2 uv, vec2 center, float radius, float strength) {
    vec2 asp = vec2(OutSize.x / OutSize.y, 1.0), d = (uv - center) * asp;
    float r = length(d), k = saturate(1.0 - r / radius);
    return center + d * (1.0 - strength * k * k) / asp;
}
vec2 ripple(vec2 uv, vec2 center, float t, float freq, float speed, float amount) {
    vec2 asp = vec2(OutSize.x / OutSize.y, 1.0), d = (uv - center) * asp;
    float r = length(d);
    return uv + normalize(d + 1e-5) / asp * sin(r * freq - t * speed) * amount * exp(-r * 3.0);
}
vec2 kaleido(vec2 uv, vec2 center, float segments) {
    vec2 asp = vec2(OutSize.x / OutSize.y, 1.0), d = (uv - center) * asp;
    float an = TAU / segments, a = abs(mod(atan(d.y, d.x), an) - an * 0.5);
    return center + length(d) * vec2(cos(a), sin(a)) / asp;
}
vec2 mirrorX(vec2 uv) { return vec2(0.5 - abs(uv.x - 0.5), uv.y); }

// ---- filters
vec3 chromatic(vec2 uv, vec2 center, float amount) {
    vec2 d = uv - center, off = d * dot(d * vec2(OutSize.x / OutSize.y, 1.0), d * vec2(OutSize.x / OutSize.y, 1.0)) * amount * 0.08;
    return vec3(texture(DiffuseSampler, uv + off).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - off).b);
}
vec3 radialBlur(vec2 uv, vec2 center, float amount) {
    vec3 acc = vec3(0.0); float wsum = 0.0;
    for (int i = 0; i < 12; i++) {
        float w = 1.0 - float(i) / 12.0;
        acc += texture(DiffuseSampler, mix(uv, center, float(i) / 11.0 * amount * 0.12)).rgb * w; wsum += w;
    }
    return acc / wsum;
}
vec3 directionalBlur(vec2 uv, vec2 dir, float samples) {
    vec3 acc = vec3(0.0); float n = 0.0;
    for (int i = 0; i < 24; i++) {
        if (float(i) >= samples) break;
        acc += texture(DiffuseSampler, uv + dir * (float(i) / max(samples - 1.0, 1.0) - 0.5)).rgb; n += 1.0;
    }
    return acc / max(n, 1.0);
}
// 9-tap blur of radius r pixels (use twice, or on a reduced named target, for wider blurs).
vec3 boxBlur(sampler2D s, vec2 uv, float r) {
    vec2 px = r / OutSize; vec3 acc = vec3(0.0);
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) acc += texture(s, uv + vec2(x, y) * px).rgb;
    return acc / 9.0;
}
vec3 glitch(vec2 uv, float amount, float t) {
    float band = floor(uv.y * 26.0);
    float off = (hash11(band + floor(t * 30.0) * 13.0) - 0.5) * 0.08 * amount * step(0.6, hash11(band * 1.7 + floor(t * 10.0)));
    return vec3(texture(DiffuseSampler, uv + vec2(off + 0.006 * amount, 0.0)).r, texture(DiffuseSampler, uv + vec2(off, 0.0)).g,
                texture(DiffuseSampler, uv + vec2(off - 0.006 * amount, 0.0)).b);
}
// Light shafts from a screen point: marches towards lightUv summing only the light the effects ADDED (DiffuseSampler
// minus SceneSampler), so the sun or snow never cast rays. Returns light to add.
vec3 lightShafts(vec2 uv, vec2 lightUv, float length_, float decayRate) {
    const int SAMPLES = 32;
    vec2 stepUv = (lightUv - uv) * length_ / float(SAMPLES);
    vec2 p = uv + stepUv * hash12(gl_FragCoord.xy);                // jittered start: fine noise instead of banding
    vec3 sum = vec3(0.0);
    float weight = 1.0;
    for (int i = 0; i < SAMPLES; i++) {
        if (any(lessThan(p, vec2(0.0))) || any(greaterThan(p, vec2(1.0)))) break;
        sum += max(texture(DiffuseSampler, p).rgb - texture(SceneSampler, p).rgb, 0.0) * weight;
        weight *= decayRate;
        p += stepUv;
    }
    return sum / float(SAMPLES);
}

// ---- masks
float vignetteMask(vec2 uv, float inner, float outer) { return smoothstep(inner, outer, length(centered(uv))); }
float scanlines(vec2 uv, float density, float t) { return 0.5 + 0.5 * sin(uv.y * OutSize.y * density + t * 6.0); }
// Edges where the depth jumps (relative to distance, so equally thin near and far).
float depthEdges(vec2 uv) {
    vec2 px = 1.0 / OutSize;
    float c = sceneDistAt(uv);
    float e = abs(sceneDistAt(uv + vec2(px.x, 0.0)) - c) + abs(sceneDistAt(uv - vec2(px.x, 0.0)) - c)
            + abs(sceneDistAt(uv + vec2(0.0, px.y)) - c) + abs(sceneDistAt(uv - vec2(0.0, px.y)) - c);
    return smoothstep(0.02, 0.08, e / max(c, 1.0));
}
// Edges of the image itself (Sobel on luminance).
float colorEdges(sampler2D s, vec2 uv, float r) {
    vec2 px = r / OutSize;
    float tl = lum(texture(s, uv + px * vec2(-1, 1)).rgb), t = lum(texture(s, uv + px * vec2(0, 1)).rgb), tr = lum(texture(s, uv + px * vec2(1, 1)).rgb);
    float l = lum(texture(s, uv + px * vec2(-1, 0)).rgb), rr = lum(texture(s, uv + px * vec2(1, 0)).rgb);
    float bl = lum(texture(s, uv + px * vec2(-1, -1)).rgb), b = lum(texture(s, uv + px * vec2(0, -1)).rgb), br = lum(texture(s, uv + px * vec2(1, -1)).rgb);
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * rr + br, gy = tl + 2.0 * t + tr - bl - 2.0 * b - br;
    return length(vec2(gx, gy));
}
// Spherical front leaving the camera: multiply by depthEdges for scanners.
float distanceFront(float sceneT, float radius, float width) { return gauss(sceneT - radius, width); }
// Ordered dithering threshold (4x4 Bayer) for retro looks and stylized transparency.
float bayer4(vec2 fragCoord) {
    vec2 p = mod(floor(fragCoord), 4.0);
    int i = int(p.x) + int(p.y) * 4;
    int m[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(m[i]) + 0.5) / 16.0;
}
