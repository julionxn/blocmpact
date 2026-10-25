// blocmpact:lib/light.glsl — techniques for drawing in the 3D world from a full-screen pass, by tracing view rays
// analytically against the depth buffer. Every position is camera-relative. Functions return intensities or
// geometric facts; you choose colors and how to combine them. Requires beginPixel(texCoord) first.
// #include "blocmpact:lib/light.glsl"

// Intensity of a glowing segment a→b: crisp core plus two halos. Never thinner than a pixel, hidden by terrain.
// core/near/far weight the three layers (try 2.5, 0.8, 0.2).
float beamGlow(vec3 rd, float sceneT, vec3 a, vec3 b, float width, float core, float near, float far) {
    float t;
    float d = raySegmentDist(vec3(0.0), rd, a, b, t);
    if (t > sceneT) return 0.0;
    float w = max(width, t * fxPixel * 0.7);
    fxAA = t * fxPixel * 0.7;
    return stroke(d, w) * core + softGlow(d, w * 3.0) * near + softGlow(d, w * 14.0) * far;
}
float beamGlow(vec3 rd, float sceneT, vec3 a, vec3 b, float width) { return beamGlow(rd, sceneT, a, b, width, 2.5, 0.8, 0.2); }

// Jagged path between a and b: `segments` pieces whose joints jump by `jitter` × length, re-rolled `rate` times per
// second of `time`. Lightning, electric arcs, cracks of energy.
float lightningGlow(vec3 rd, float sceneT, vec3 a, vec3 b, float time, float rate, int segments, float jitter, float width) {
    float seed = floor(time * rate);
    vec3 dir = b - a;
    vec3 s1 = normalize(cross(dir, abs(normalize(dir).y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 s2 = normalize(cross(dir, s1));
    vec3 prev = a;
    float acc = 0.0;
    for (int i = 1; i <= 32; i++) {
        if (i > segments) break;
        float k = float(i) / float(segments);
        vec2 j = (hash22(vec2(float(i), seed)) - 0.5) * length(dir) * jitter * (i == segments ? 0.0 : 1.0);
        vec3 next = a + dir * k + s1 * j.x + s2 * j.y;
        acc = max(acc, beamGlow(rd, sceneT, prev, next, width));
        prev = next;
    }
    return acc;
}

// Vertical cylinder of radius r over c, from c.y + foot to c.y + top. Returns (hit t, radial 0 axis → 1 edge,
// height above c, angle) or x = -1 when missed or hidden.
vec4 columnHit(vec3 rd, float sceneT, vec3 c, float r, float foot, float top) {
    vec2 h = rayCylinderY(vec3(0.0), rd, c, r);
    float tIn = max(h.x, 0.0);
    if (h.y <= 0.0 || tIn > sceneT) return vec4(-1.0);
    float y = rd.y * tIn - c.y;
    if (y < foot || y > top) return vec4(-1.0);
    float tAx;
    float dAx = raySegmentDist(vec3(0.0), rd, c + vec3(0.0, foot, 0.0), c + vec3(0.0, top, 0.0), tAx);
    vec3 hp = rd * tIn - c;
    return vec4(tIn, saturate(dAx / r), y, atan(hp.z, hp.x));
}

// Sphere shell: (t front, t back) of the visible faces (−1 = none) and their normals. Shields, bubbles, domes.
vec2 sphereShell(vec3 rd, float sceneT, vec3 c, float r, out vec3 nFront, out vec3 nBack) {
    vec2 h = raySphere(vec3(0.0), rd, c, r);
    float f = h.x > 0.0 && h.x < sceneT ? h.x : -1.0;
    float b = h.y > 0.0 && h.y < sceneT ? h.y : -1.0;
    nFront = f > 0.0 ? normalize(rd * f - c) : vec3(0.0);
    nBack = b > 0.0 ? normalize(rd * b - c) : vec3(0.0);
    return vec2(f, b);
}
// Rim light: 0 facing the camera, 1 at grazing angles. power ≈ 2–5.
float fresnel(vec3 n, vec3 rd, float power) { return pow(1.0 - abs(dot(n, rd)), power); }

// Terrain decals: 2D coordinates on the ground around a center (1 = radius) and the AA width to use with stroke().
vec2 decalUV(vec3 rel, float r) { return rel.xz / r; }
float decalAA(vec3 rd, float sceneT, float r) { return fxPixel * sceneT / (r * max(abs(rd.y), 0.08)) * 0.7; }
// Decal on any plane through c with normal n and in-plane axis u: returns plane coords (1 = radius) or 1e9.
vec2 planeUV(vec3 rd, float sceneT, vec3 c, vec3 n, vec3 u, float r) {
    float t = rayPlane(vec3(0.0), rd, c, n);
    if (t <= 0.0 || t > sceneT) return vec2(1e9);
    vec3 p = rd * t - c, v = cross(n, u);
    fxAA = fxPixel * t / (r * max(abs(dot(rd, n)), 0.08)) * 0.7;
    return vec2(dot(p, u), dot(p, v)) / r;
}

// Expanding front: gaussian ring of `width` at distance `front` (hd = horizontal distance to the center).
float frontRing(float hd, float front, float width) { return exp(-pow((hd - front) / width, 2.0)); }
// Refraction of a front: pushes uv away from a screen point (centerUv) with an x·exp(−x²) profile. Read the scene
// with the returned uv. strength ≈ 0.01–0.02.
vec2 frontRefract(vec2 uv, vec2 centerUv, float hd, float front, float width, float strength) {
    float x = (hd - front) / width;
    return uv + normalize(uv - centerUv + 1e-5) * x * exp(-x * x) * strength;
}

// Fake light: how much a point at `dist` from a source of `radius` is lit (no dynamic lights needed).
float lightReach(float dist, float radius) { return min(radius * 1.6 / (dist + radius * 0.25) * exp(-dist / (radius * 2.5)), 3.0); }

// True if a camera-relative point is hidden behind terrain (bias in blocks).
bool occluded(vec3 p, float bias) {
    vec3 sp = project(p);
    if (sp.z <= 0.0) return true;
    if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0) return false;
    float d = texture(DepthSampler, sp.xy).r;
    return !skyAt(d) && length(unproject(sp.xy, d)) < length(p) - bias;
}

// Screen-space glow around a 3D point: core + halo, occluded by terrain. size in screen-height units (≈0.01).
float pointGlow(vec3 p, float size) {
    if (occluded(p, 1.0)) return 0.0;
    vec3 sp = project(p);
    vec2 v = (texCoord - sp.xy) * vec2(OutSize.x / OutSize.y, 1.0);
    float r = length(v);
    return exp(-r * r / (size * size * 0.12)) * 3.0 + softGlow(r, size * 2.5) * 0.5;
}
// Star-shaped spikes around a 3D point: `count` rays of `len` (screen-height units), `sharpness` ≈ 20–200 (thinner
// rays as it grows), rotated by `angle` radians.
float pointSpikes(vec3 p, float count, float len, float sharpness, float angle) {
    if (occluded(p, 1.0)) return 0.0;
    vec3 sp = project(p);
    vec2 v = rot2(angle) * ((texCoord - sp.xy) * vec2(OutSize.x / OutSize.y, 1.0));
    float r = length(v), a = atan(v.y, v.x);
    float k = pow(abs(cos(a * count * 0.5)), sharpness);
    return k * exp(-r / max(len, 1e-4));
}
