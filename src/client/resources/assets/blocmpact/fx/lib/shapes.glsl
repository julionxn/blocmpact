// blocmpact:lib/shapes.glsl — procedural symbols and patterns, crisp at any distance (no textures).
// Most return a distance; draw them with stroke(d, width) after setting fxAA (decalAA / planeUV do it).
// #include "blocmpact:lib/shapes.glsl"  (uses sdSegment2 and ring from core)

// Dashed ring: `dashes` segments around, `fill` 0–1 of each one drawn, rotated by `spin` turns.
float dashedRing(vec2 p, float radius, float w, float dashes, float fill, float spin) {
    float turn = atan(p.y, p.x) / TAU + 0.5 + spin;
    return ring(length(p), radius, w) * step(fract(turn * dashes), fill);
}
// Radial tick marks between r0 and r1.
float ticks(vec2 p, float r0, float r1, float count, float w, float spin) {
    float r = length(p), a = atan(p.y, p.x) / TAU + 0.5;
    float d = abs(fract(a * count - spin) - 0.5) * TAU * r / count;
    return stroke(d, w) * step(r0, r) * step(r, r1);
}
// Rotating radar wedge: the edge sits at `turns` (counterclockwise) and leaves a tail `tail` turns long behind it.
float radarSweep(vec2 p, float turns, float tail) {
    float behind = fract(turns - (atan(p.y, p.x) / TAU + 0.5));
    return exp(-behind / max(tail, 1e-3));
}
// Hexagonal grid of pointy-top cells with circumradius `size`: x = distance to the cell border, yz = cell id (axial).
vec3 hexGrid(vec2 p, float size) {
    vec2 q = p / size;
    float axQ = q.x * 0.5773503 - q.y / 3.0, axR = q.y * 2.0 / 3.0;      // to axial coordinates
    vec3 cube = vec3(axQ, -axQ - axR, axR), rounded = round(cube);       // nearest cell in cube coordinates
    vec3 err = abs(rounded - cube);
    if (err.x > err.y && err.x > err.z) rounded.x = -rounded.y - rounded.z;
    else if (err.y > err.z) rounded.y = -rounded.x - rounded.z;
    else rounded.z = -rounded.x - rounded.y;
    vec2 center = vec2(1.7320508 * (rounded.x + rounded.z * 0.5), 1.5 * rounded.z);
    vec2 local = abs(q - center);
    float reach = max(local.x, dot(local, vec2(0.5, 0.8660254)));        // projection on the farthest edge normal
    return vec3((0.8660254 - reach) * size, rounded.x, rounded.z);
}

// Infinite alphabet: each id draws a short path over a 3×3 lattice of nodes (2–4 moves to neighbouring nodes,
// diagonals included), sometimes with a detached dot. uv in 0–1; returns the distance to the strokes (uv units),
// 1e9 well outside the box.
float glyph(vec2 uv, float id) {
    if (any(lessThan(uv, vec2(-0.25))) || any(greaterThan(uv, vec2(1.25)))) return 1e9;
    vec2 node = floor(hash22(vec2(id, 3.7)) * 2.999);
    int moves = 2 + int(hash11(id * 1.37 + 0.5) * 2.999);
    float d = 1e9;
    for (int i = 0; i < 4; i++) {
        if (i >= moves) break;
        vec2 next = node;
        for (int attempt = 0; attempt < 8; attempt++) {               // first direction that stays on the lattice
            float a = floor(hash11(id * 7.31 + float(i) * 1.618 + float(attempt) * 0.37) * 8.0) * (TAU / 8.0);
            vec2 candidate = node + vec2(round(cos(a)), round(sin(a)));
            if (all(greaterThanEqual(candidate, vec2(0.0))) && all(lessThanEqual(candidate, vec2(2.0)))) {
                next = candidate;
                break;
            }
        }
        d = min(d, sdSegment2(uv, node * 0.5, next * 0.5));
        node = next;
    }
    if (hash11(id * 3.17 + 9.1) > 0.6) d = min(d, length(uv - floor(hash22(vec2(id, 11.3)) * 2.999) * 0.5) - 0.03);
    return d;
}
// A band of `n` glyphs between radii r0 and r1, rotated by `spin` radians, upright towards the outside.
// Returns stroke coverage.
float glyphBand(vec2 p, float r0, float r1, float n, float spin, float seed, float w) {
    float r = length(p), height = r1 - r0;
    if (r < r0 - w * 4.0 || r > r1 + w * 4.0) return 0.0;
    float turn = (atan(p.y, p.x) - spin) / TAU + 0.5;
    float slot = floor(turn * n), across = fract(turn * n);
    float slotArc = TAU * (r0 + r1) * 0.5 / n;                        // arc length of one slot at mid radius
    float box = height * 0.7;                                         // glyph box: 70% of the band, centered
    vec2 g = vec2((across - 0.5) * slotArc, r - (r0 + r1) * 0.5) / box + 0.5;
    return stroke(glyph(g, mod(slot, n) + seed * 131.0) * box, w);
}
// Progressive reveal of a circular drawing, like a pen going around: multiply your strokes by it.
float revealAround(vec2 p, float progress, float softness) {
    float turn = atan(p.y, p.x) / TAU + 0.5;
    return saturate((progress - turn) / max(softness, 1e-4));
}

// ---- seven-segment digits (bits a=1 b=2 c=4 d=8 e=16 f=32 g=64), box 0.6 × 1
int digitMask(int d) {
    const int masks[10] = int[](63, 6, 91, 79, 102, 109, 125, 7, 127, 111);
    return masks[clamp(d, 0, 9)];
}
float sdDigit(vec2 p, int digit) {
    const vec4 segments[7] = vec4[](vec4(0.07, 1.0, 0.53, 1.0), vec4(0.6, 0.93, 0.6, 0.57), vec4(0.6, 0.43, 0.6, 0.07),
                                    vec4(0.07, 0.0, 0.53, 0.0), vec4(0.0, 0.43, 0.0, 0.07), vec4(0.0, 0.93, 0.0, 0.57),
                                    vec4(0.07, 0.5, 0.53, 0.5));       // a..g as (start, end), gaps at the joints
    int mask = digitMask(digit);
    float d = 1e9;
    for (int i = 0; i < 7; i++) {
        if ((mask & (1 << i)) != 0) d = min(d, sdSegment2(p, segments[i].xy, segments[i].zw));
    }
    return d;
}
// Non-negative integer with `digits` figures, lower-left corner at origin, height h. Draw with stroke(d, h * 0.07).
float readout(vec2 p, vec2 origin, float h, float value, int digits) {
    vec2 q = (p - origin) / h;
    float cell = floor(q.x / 0.95);
    if (cell < 0.0 || cell >= float(digits) || q.y < -0.3 || q.y > 1.3) return 1e9;
    int v = int(floor(max(value, 0.0) + 0.5)), div = 1;
    for (int i = 0; i < 9; i++) { if (i >= digits - 1 - int(cell)) break; div *= 10; }
    return sdDigit(q - vec2(cell * 0.95, 0.0), (v / div) - (v / div / 10) * 10) * h;
}

// ---- natural patterns
// Thin lines where a noise field crosses `level`: cracks, veins, contour lines (needs a noise value in n).
float isoLine(float n, float level, float w) { return stroke(abs(n - level), w); }
// Stars on the sphere of directions: density (fraction of lit cells, keep below 0.015), returns brightness.
float starField(vec3 rd, float cells, float density, float seed) {
    vec3 p = rd * cells, c = floor(p);
    float h = hash13(c + seed * 31.0);
    if (h < 1.0 - density) return 0.0;
    vec3 j = normalize(c + 0.5 + (hash33(c + 1.3) - 0.5) * 0.6) * cells;
    float d = length(p - j) / cells, size = max(fxPixel * 0.6, 0.0004);
    float mag = pow(hash13(c + 7.7), 4.0) * 2.5 + 0.15;
    return mag * exp(-d * d / (size * size));
}
