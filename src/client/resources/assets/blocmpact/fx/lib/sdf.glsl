// blocmpact:lib/sdf.glsl — signed distance functions in 2D and 3D. #include "blocmpact:lib/sdf.glsl"
// Negative inside, positive outside. Draw 2D shapes with stroke(abs(d), w) (outline) or stroke(d, 0.0) (fill), light
// them with softGlow(abs(d), w) and combine them with the operators below. The box, round box, capsule, torus, capped
// cylinder, quadratic smooth minimum and tetrahedral normal follow Inigo Quilez's articles (MIT; see
// THIRD_PARTY_NOTICES.md); the rest is written for Blocmpact. Polygons start with a vertex on the +x axis.

// ---- 2D shapes
float sdCircle(vec2 p, float r) { return length(p) - r; }
float sdBox2(vec2 p, vec2 halfSize) {
    vec2 d = abs(p) - halfSize;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}
float sdRoundBox2(vec2 p, vec2 halfSize, float r) { return sdBox2(p, halfSize - r) - r; }
float sdRing2(vec2 p, float r, float thickness) { return abs(length(p) - r) - thickness * 0.5; }
// Arc of a circle centered on the +x axis spanning ±aperture radians.
float sdArc2(vec2 p, float r, float aperture, float thickness) {
    if (abs(atan(p.y, p.x)) <= aperture) return abs(length(p) - r) - thickness * 0.5;
    vec2 end = r * vec2(cos(aperture), sin(aperture) * (p.y < 0.0 ? -1.0 : 1.0));   // the nearer end
    return length(p - end) - thickness * 0.5;
}
// Regular polygon with n sides (circumradius r).
float sdPolygon(vec2 p, float n, float r) {
    float sector = TAU / n;
    float a = mod(atan(p.y, p.x), sector) - sector * 0.5;           // angle from the normal of the nearest edge
    vec2 q = length(p) * vec2(cos(a), abs(sin(a)));
    float apothem = r * cos(PI / n), halfEdge = r * sin(PI / n);
    float d = length(q - vec2(apothem, clamp(q.y, 0.0, halfEdge)));
    return q.x > apothem ? d : -d;
}
// Star with n points between radii r0 (inner) and r1 (outer).
float sdStar(vec2 p, float n, float r1, float r0) {
    float sector = TAU / n;
    float a = abs(mod(atan(p.y, p.x) + sector * 0.5, sector) - sector * 0.5);   // 0 on an outer point
    vec2 q = length(p) * vec2(cos(a), sin(a));
    vec2 tip = vec2(r1, 0.0), notch = r0 * vec2(cos(sector * 0.5), sin(sector * 0.5));
    vec2 e = notch - tip, w = q - tip;
    float d = sdSegment2(q, tip, notch);
    return e.x * w.y - e.y * w.x > 0.0 ? -d : d;                         // same side as the center = inside
}
// Star polygon {n/k}: vertex i joined to i + k (pentagram = {5/2}, heptagram = {7/3}), rotated by `spin` radians.
// Unsigned distance to the strokes. n up to 24.
float sdPolygram(vec2 p, float n, float k, float r, float spin) {
    mat2 nextVertex = rot2(TAU / n);
    vec2 from = r * vec2(cos(spin), sin(spin)), to = rot2(k * TAU / n) * from;   // first chord, then turn it round
    float d = 1e9;
    for (int i = 0; i < 24; i++) {
        if (float(i) >= n) break;
        d = min(d, sdSegment2(p, from, to));
        from = nextVertex * from;
        to = nextVertex * to;
    }
    return d;
}
// Plus sign: arms reaching `size` from the center, `thickness` wide.
float sdCross2(vec2 p, float size, float thickness) {
    p = abs(p);
    return min(sdBox2(p, vec2(size, thickness * 0.5)), sdBox2(p, vec2(thickness * 0.5, size)));
}
// Any triangle (either winding).
float sdTriangle2(vec2 p, vec2 a, vec2 b, vec2 c) {
    float d = min(sdSegment2(p, a, b), min(sdSegment2(p, b, c), sdSegment2(p, c, a)));
    vec3 side = vec3((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x),
                     (c.x - b.x) * (p.y - b.y) - (c.y - b.y) * (p.x - b.x),
                     (a.x - c.x) * (p.y - c.y) - (a.y - c.y) * (p.x - c.x));
    bool inside = all(greaterThanEqual(side, vec3(0.0))) || all(lessThanEqual(side, vec3(0.0)));
    return inside ? -d : d;
}

// ---- 2D domain operators
// Repeats space n times around the origin (kaleidoscope): the folded point, in a sector centered on +x.
vec2 repeatPolar(vec2 p, float n) {
    float sector = TAU / n;
    float a = mod(atan(p.y, p.x) + sector * 0.5, sector) - sector * 0.5;
    return length(p) * vec2(cos(a), sin(a));
}
vec2 repeatGrid(vec2 p, vec2 cell) { return p - cell * round(p / cell); }

// ---- 3D shapes
float sdSphere(vec3 p, float r) { return length(p) - r; }
float sdBox(vec3 p, vec3 halfSize) {
    vec3 q = abs(p) - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}
float sdRoundBox(vec3 p, vec3 halfSize, float r) {
    vec3 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}
float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-12), 0.0, 1.0);
    return length(pa - ba * h) - r;
}
// Ring lying in the xz plane: `major` = radius of the ring, `minor` = radius of the tube.
float sdTorus(vec3 p, float major, float minor) {
    vec2 q = vec2(length(p.xz) - major, p.y);
    return length(q) - minor;
}
float sdCylinderY(vec3 p, float r, float halfHeight) {
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r, halfHeight);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}
// Apex at the origin, opening downwards to radius r at -height.
float sdConeY(vec3 p, float r, float height) {
    vec2 q = vec2(length(p.xz), -p.y);                              // depth below the apex grows with q.y
    vec2 rim = vec2(r, height);
    float d = min(sdSegment2(q, vec2(0.0), rim), sdSegment2(q, vec2(0.0, height), rim));
    bool inside = q.y > 0.0 && q.y < height && q.x * height < r * q.y;
    return inside ? -d : d;
}
float sdPlane(vec3 p, vec3 n, float h) { return dot(p, n) + h; }

// ---- combining
float opUnion(float a, float b) { return min(a, b); }
float opSubtract(float a, float b) { return max(a, -b); }       // a minus b
float opIntersect(float a, float b) { return max(a, b); }
// Smooth union of blend size k (quadratic smooth minimum).
float smin(float a, float b, float k) {
    k = max(k, 1e-6);
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}
float smax(float a, float b, float k) { return -smin(-a, -b, k); }
float opOnion(float d, float thickness) { return abs(d) - thickness; }

// ---- raymarching: define FX_SCENE(p) (signed distance of your scene, camera-relative p) before including, then call
// fxMarch. Returns the hit distance or -1. Stops at maxT or at the terrain (sceneT).
#ifdef FX_SCENE
float fxMarch(vec3 ro, vec3 rd, float maxT, float sceneT) {
    float limit = min(maxT, sceneT), t = 0.0;
    for (int i = 0; i < 96; i++) {
        float d = FX_SCENE(ro + rd * t);
        if (d < 0.0004 * (1.0 + t)) return t;                       // the tolerance grows with distance
        t += d * 0.95;
        if (t > limit) break;
    }
    return -1.0;
}
// Surface normal from four samples on a tetrahedron.
vec3 fxNormal(vec3 p) {
    const float h = 0.0005;
    const vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * FX_SCENE(p + k.xyy * h) + k.yyx * FX_SCENE(p + k.yyx * h)
                   + k.yxy * FX_SCENE(p + k.yxy * h) + k.xxx * FX_SCENE(p + k.xxx * h));
}
#endif
