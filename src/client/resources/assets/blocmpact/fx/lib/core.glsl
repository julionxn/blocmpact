// blocmpact:lib/core.glsl — prepended to every program, right after "#version 150".
// Engine inputs, free parameters for effects, and the small toolkit every pass needs.
// Optional modules: #include "blocmpact:lib/<module>.glsl" with noise, sdf, color, light, screen, shapes or volume.

uniform sampler2D DiffuseSampler;   // previous pass (the scene copy for the first one)
uniform sampler2D DepthSampler;     // Minecraft's depth buffer
uniform sampler2D SceneSampler;     // the untouched scene
uniform mat4 InvProj, InvView, ViewProj, Proj, View;
uniform vec2 OutSize;               // size of the target being written, in pixels
uniform vec2 ScreenSize;            // full resolution
uniform float DepthZeroToOne, ReverseZ, GameTime;
uniform vec3 CameraPos;             // camera position wrapped every 8192 blocks: for world-anchored noise only
uniform vec4 P0, P1, P2, P3, P4, P5, P6, P7;   // free parameters: each effect decides what they mean
uniform vec4 Pts[32];               // free list of vec4 (points, data)
uniform vec3 Target;                // free: usually the effect's position, camera-relative
in vec2 texCoord;
out vec4 fragColor;

#define PI 3.14159265359
#define TAU 6.28318530718

// ---- small helpers
float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec2 saturate(vec2 x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }          // Rec. 709 luma weights
float remap(float x, float a, float b, float c, float d) { return c + (d - c) * ((x - a) / (b - a)); }
// 0 before in0, rises to 1 at in1, holds, falls back to 0 between out0 and out1.
float window(float t, float in0, float in1, float out0, float out1) {
    float rise = saturate((t - in0) / max(in1 - in0, 0.001));
    float fall = saturate((out1 - t) / max(out1 - out0, 0.001));
    return rise * fall;
}
mat2 rot2(float a) { float s = sin(a), c = cos(a); return mat2(c, s, -s, c); }
float aspect() { return OutSize.x / OutSize.y; }
// uv centered on 0 with square pixels (x scaled by the aspect ratio)
vec2 centered(vec2 uv) { return vec2((uv.x - 0.5) * aspect(), uv.y - 0.5); }

// ---- hashes: "Hash without Sine" by David Hoskins (MIT, see THIRD_PARTY_NOTICES.md). Sine-free, so identical on
// every GPU.
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// ---- depth, positions and rays. The eye is the origin: every position is camera-relative.
float ndcDepth(float d) { return DepthZeroToOne > 0.5 ? d : d * 2.0 - 1.0; }
bool skyAt(float d) { return abs(d - (ReverseZ > 0.5 ? 0.0 : 1.0)) < 0.00001; }   // nothing was drawn there
vec3 unproject(vec2 uv, float depth) {
    vec4 eye = InvProj * vec4(uv * 2.0 - 1.0, ndcDepth(depth), 1.0);
    return mat3(InvView) * (eye.xyz / eye.w);                // the view is rotation only
}
// Unit view ray through uv (a depth between near and far keeps the unprojection precise).
vec3 viewDir(vec2 uv) { return normalize(unproject(uv, ReverseZ > 0.5 ? 0.25 : 0.75)); }
float sceneDepth(vec2 uv) { return texture(DepthSampler, uv).r; }
const float SKY_DISTANCE = 1e5;                              // where the sky sits for distance tests
vec3 scenePosAt(vec2 uv) {
    float d = sceneDepth(uv);
    if (skyAt(d)) return viewDir(uv) * SKY_DISTANCE;
    return unproject(uv, d);
}
float sceneDistAt(vec2 uv) { return length(scenePosAt(uv)); }
// Camera-relative point → (uv, w); w ≤ 0 means behind the camera.
vec3 project(vec3 p) {
    vec4 clip = ViewProj * vec4(p, 1.0);
    if (clip.w <= 0.0001) return vec3(-10.0, -10.0, -1.0);
    return vec3(0.5 + 0.5 * clip.xy / clip.w, clip.w);
}
// Normal of the scene facing the camera, from the reconstructed positions (the shorter neighbour difference on each
// axis, so silhouettes do not smear). Good enough for lighting tricks and decals.
vec3 sceneNormalAt(vec2 uv) {
    vec2 px = 1.0 / OutSize;
    vec3 c = scenePosAt(uv);
    vec3 r = scenePosAt(uv + vec2(px.x, 0.0)) - c, l = c - scenePosAt(uv - vec2(px.x, 0.0));
    vec3 u = scenePosAt(uv + vec2(0.0, px.y)) - c, d = c - scenePosAt(uv - vec2(0.0, px.y));
    vec3 n = cross(dot(r, r) < dot(l, l) ? r : l, dot(u, u) < dot(d, d) ? u : d);
    return dot(n, n) < 1e-12 ? vec3(0.0, 1.0, 0.0) : normalize(n);
}

// ---- pixel footprint. fxPixel: angle covered by one pixel (radians). fxAA: anti-aliasing width of the shape being
// drawn, in that shape's own units (decalAA and planeUV set it for their coordinates). Call beginPixel(texCoord) first.
float fxPixel = 0.001, fxAA = 0.001;
void beginPixel(vec2 uv) {
    float f = abs(Proj[1][1]);                               // cot(fov / 2)
    float y = (uv.y * 2.0 - 1.0) / f;                        // tangent of the angle above the view axis
    fxPixel = 2.0 / (f * OutSize.y) / (1.0 + y * y);
    fxAA = fxPixel;
}

// ---- closed-form intersections (ro = ray origin, rd = unit direction). Misses return -1.
// Sphere: closest approach along the ray, then half the chord. (tNear, tFar).
vec2 raySphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 toC = c - ro;
    float along = dot(toC, rd);
    float miss2 = dot(toC, toC) - along * along;
    if (miss2 > r * r) return vec2(-1.0);
    float halfChord = sqrt(r * r - miss2);
    return vec2(along - halfChord, along + halfChord);
}
// Infinite vertical cylinder: the same construction in the xz plane (rd.xz is not unit length).
vec2 rayCylinderY(vec3 ro, vec3 rd, vec3 c, float r) {
    vec2 flat_ = rd.xz;
    float speed2 = dot(flat_, flat_);
    if (speed2 < 1e-8) return vec2(-1.0);
    float along = dot(c.xz - ro.xz, flat_) / speed2;
    vec2 gap = ro.xz + flat_ * along - c.xz;
    float miss2 = dot(gap, gap);
    if (miss2 > r * r) return vec2(-1.0);
    float halfChord = sqrt((r * r - miss2) / speed2);
    return vec2(along - halfChord, along + halfChord);
}
float rayPlane(vec3 ro, vec3 rd, vec3 c, vec3 n) {
    float facing = dot(rd, n);
    if (abs(facing) < 1e-6) return -1.0;
    return dot(n, c - ro) / facing;
}
// Axis-aligned box (slabs): (tEnter, tExit); tEnter > tExit is a miss.
vec2 rayBox(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax) {
    vec3 inv = 1.0 / rd;
    vec3 tA = (bmin - ro) * inv, tB = (bmax - ro) * inv;
    vec3 enter = min(tA, tB), exit_ = max(tA, tB);
    return vec2(max(max(enter.x, enter.y), enter.z), min(min(exit_.x, exit_.y), exit_.z));
}
// Distance from the ray to a point; t = ray parameter of the closest approach (never behind the eye).
float rayPointDist(vec3 ro, vec3 rd, vec3 p, out float t) {
    t = max(dot(p - ro, rd), 0.0);
    return distance(ro + rd * t, p);
}
// Distance from the ray to segment a–b; t = ray parameter of the closest approach.
float raySegmentDist(vec3 ro, vec3 rd, vec3 a, vec3 b, out float t) {
    vec3 seg = b - a, w = ro - a;
    float segLen2 = max(dot(seg, seg), 1e-8), cosTerm = dot(seg, rd);
    float parallel = segLen2 - cosTerm * cosTerm;                  // zero when the ray runs along the segment
    float s = parallel > 1e-6 * segLen2 ? (dot(seg, w) - cosTerm * dot(rd, w)) / parallel : 0.0;
    s = saturate(s);
    t = max(dot(a + seg * s - ro, rd), 0.0);
    vec3 onRay = ro + rd * t;
    s = saturate(dot(onRay - a, seg) / segLen2);                   // re-project in case t was clamped
    vec3 onSeg = a + seg * s;
    t = max(dot(onSeg - ro, rd), 0.0);
    return distance(ro + rd * t, onSeg);
}
// Distance to segment a–b in 2D (formula from Inigo Quilez, MIT; see THIRD_PARTY_NOTICES.md).
float sdSegment2(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-12), 0.0, 1.0);
    return length(pa - ba * h);
}

// ---- light profiles (d = distance to the shape, w = width)
float softGlow(float d, float w) { float q = d / max(w, 1e-6); return 1.0 / (1.0 + q * q); }   // Lorentzian: hard core, long tail
float gauss(float d, float w) { float q = d / max(w, 1e-6); return exp(-q * q); }
// Anti-aliased stroke of half-width w: 1 inside, 0 outside, a linear ramp of 2 × fxAA across the border.
float stroke(float d, float w) { return saturate(0.5 + (w - d) / (2.0 * max(fxAA, 1e-5))); }
float ring(float d, float radius, float w) { return stroke(abs(d - radius), w); }

// ---- the default tonemap: identity up to 0.7, then a rational shoulder that approaches 1. Channels above 1 lend part
// of their excess to the other two first, so very hot light turns white instead of clipping to a pure hue.
// Other curves live in color.glsl. Passes write RGBA8: compress whatever HDR you built before writing.
vec3 tonemap(vec3 c) {
    c = max(c, 0.0);
    c += max(max(c.r, max(c.g, c.b)) - 1.0, 0.0) * 0.4;           // spill of the hottest channel
    vec3 k = max(c, 0.7);                                          // evaluate the shoulder only from the knee up
    vec3 shoulder = 0.7 + 0.3 * (k - 0.7) / (k - 0.4);             // = 0.7 + 0.3·x / (0.3 + x) with x = c − 0.7
    return mix(c, shoulder, step(0.7, c));
}
