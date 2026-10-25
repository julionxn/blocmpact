// blocmpact:internal/finish — the shared compositor: one full-resolution pass that applies, in a fixed order,
// zoom → shake → channel split and detail → zoom blur → adaptation → glow → grading → vignette → flash → fade → grain
// → impact frame. Every input arrives neutral unless an effect asked for something (see ScreenFx).
#include "blocmpact:lib/color.glsl"

uniform sampler2D ReducedSceneSampler;   // the scene at the effect resolution (for detail transfer)
uniform sampler2D GlowSampler;           // glow of the light the effects added
uniform sampler2D FloodSampler;          // the smallest glow level: how much added light covers the screen
uniform vec2 EffectSize;
uniform float DetailTransfer;
uniform vec2 Shake;                      // amount, frequency
uniform vec3 Aberration;                 // amount, center uv
uniform vec3 ZoomBlur;                   // amount, center uv
uniform vec3 Zoom;                       // scale, center uv
uniform vec2 Glow;                       // strength, adaptation
uniform vec4 Grade1;                     // exposure, saturation, contrast, pivot
uniform vec4 Grade2;                     // gamma, hue shift, keep color, keep threshold
uniform vec3 Tint, Lift, Gray;
uniform vec4 Vignette;                   // rgb, amount
uniform vec2 VignetteShape;              // inner, outer
uniform vec4 Flash;                      // rgb, amount (above 1 it burns towards white)
uniform vec4 Fade;                       // rgb, amount
uniform vec2 Grain;                      // amount, refreshes per second
uniform vec4 Impact;                     // mode (-1 none), focus uv, seconds into the drawing
uniform vec4 ImpactPaper, ImpactInk, ImpactHigh;
uniform vec4 ImpactParams;               // bias, edges, cell, glitch
uniform vec2 ImpactParams2;              // spin, invert

// The effect image at uv. When the passes ran below full resolution, the scene's fine detail (full minus reduced
// scene) is added back where the pixel still shows the real scene (alpha 1), scaled down where the effect darkened
// it (never amplified: added light carries no scene texture).
vec3 effectAt(vec2 uv) {
    vec4 fx = texture(DiffuseSampler, uv);
    if (DetailTransfer < 0.5) return fx.rgb;
    vec3 reduced = texture(ReducedSceneSampler, uv).rgb;
    float keep = clamp((lum(fx.rgb) + 0.02) / (lum(reduced) + 0.02), 0.0, 1.0) * fx.a;
    return max(fx.rgb + (texture(SceneSampler, uv).rgb - reduced) * keep, 0.0);
}

// Lens-like channel split around Aberration.yz, growing with the square of the distance.
vec3 splitChannels(vec2 uv) {
    if (Aberration.x <= 0.0) return effectAt(uv);
    vec2 r = uv - Aberration.yz, ra = r * vec2(aspect(), 1.0);
    vec2 push = r * dot(ra, ra) * Aberration.x * 0.07;
    return vec3(effectAt(uv + push).r, effectAt(uv + push * 0.3).g, effectAt(uv - push * 0.7).b);
}

// Smooth, non-repeating camera jitter in −1…1 from two sine pairs per axis.
vec2 shakeOffset(float t) {
    return vec2(sin(t * 5.13 + 0.7) + 0.6 * sin(t * 11.7 + 2.1), sin(t * 6.71 + 4.2) + 0.6 * sin(t * 13.3 + 1.3)) / 1.6;
}

// Radial smear towards ZoomBlur.yz: image and glow averaged along the way, nearer samples weighing more.
void zoomSmear(vec2 uv, out vec3 image, out vec3 light) {
    image = vec3(0.0);
    light = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 12; i++) {
        float k = float(i) / 11.0, w = (1.0 - k) * (1.0 - k) + 0.15;
        vec2 s = mix(uv, ZoomBlur.yz, k * ZoomBlur.x * 0.11);
        image += splitChannels(s) * w;
        light += texture(GlowSampler, s).rgb * w;
        total += w;
    }
    image /= total;
    light /= total;
}

// Adaptation: when the added light covers much of the screen, everything is scaled down a little.
float floodGain() {
    if (Glow.y <= 0.0 || Glow.x <= 0.0) return 1.0;
    float cover = 0.0;
    for (int j = 0; j < 3; j++) {
        for (int i = 0; i < 3; i++) cover += lum(texture(FloodSampler, (vec2(float(i), float(j)) + 0.5) / 3.0).rgb);
    }
    cover = cover / 9.0 * Glow.x;
    return mix(1.0, 1.0 / (1.0 + max(cover - 0.04, 0.0) * 2.8), Glow.y);
}

// Exposure, tint and lift, desaturation towards a gray tint (highlights may keep their color), contrast, gamma, hue.
vec3 grade(vec3 col) {
    vec3 hot = col;
    col = col * Grade1.x * Tint + Lift;
    col = mix(vec3(lum(col)) * Gray, col, Grade1.y);
    float keep = Grade2.z * saturate((lum(hot) - Grade2.w + 0.12) / 0.3);
    col = mix(col, hot * Grade1.x * Tint, keep);
    col = (col - Grade1.w) * Grade1.z + Grade1.w;
    col = pow(max(col, 0.0), vec3(1.0 / max(Grade2.x, 0.01)));
    if (Grade2.y != 0.0) col = hueShift(saturate(col), Grade2.y);
    return clamp(col, 0.0, 1.6);
}

// ---- impact frame helpers
// Luminance over a 4×4-pixel footprint (four bilinear reads): block textures are too noisy at one pixel.
float footprintLum(vec2 uv, vec2 px) {
    return 0.25 * (lum(texture(DiffuseSampler, uv + px * vec2(1.0, 1.0)).rgb) + lum(texture(DiffuseSampler, uv + px * vec2(-1.0, 1.0)).rgb)
                 + lum(texture(DiffuseSampler, uv + px * vec2(1.0, -1.0)).rgb) + lum(texture(DiffuseSampler, uv - px).rgb));
}
// Sobel edges of that luminance: where the ink goes.
float inkEdges(vec2 uv, vec2 px) {
    vec2 o = px * 1.25;
    float tl = footprintLum(uv + vec2(-o.x, o.y), px), tc = footprintLum(uv + vec2(0.0, o.y), px), tr = footprintLum(uv + o, px);
    float ml = footprintLum(uv - vec2(o.x, 0.0), px), mr = footprintLum(uv + vec2(o.x, 0.0), px);
    float bl = footprintLum(uv - o, px), bc = footprintLum(uv - vec2(0.0, o.y), px), br = footprintLum(uv + vec2(o.x, -o.y), px);
    float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    float gy = (tl + 2.0 * tc + tr) - (bl + 2.0 * bc + br);
    return smoothstep(0.22, 0.6, length(vec2(gx, gy)));
}
// Mean luminance of the surroundings: a 3×3 grid of reads spaced 3% of the screen height.
float surroundingsLum(vec2 uv) {
    vec2 spacing = vec2(0.03 / aspect(), 0.03);
    float sum = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) sum += lum(texture(DiffuseSampler, uv + vec2(float(i), float(j)) * spacing).rgb);
    }
    return sum / 9.0;
}

vec3 impactFrame(vec3 col, vec2 uv) {
    int mode = int(Impact.x + 0.5);
    vec2 px = 1.0 / EffectSize, suv = uv;
    if (mode == 6) {                                                      // glitch: shifted bands before the masks
        float band = floor(uv.y * 24.0), tick = floor(Impact.w * 36.0);
        suv.x += (hash12(vec2(band, tick)) - 0.5) * 0.14 * ImpactParams.w * step(0.58, hash12(vec2(band, 91.0)));
    }
    float l = footprintLum(suv, px), mean = surroundingsLum(suv), bias = ImpactParams.x;
    float ink = inkEdges(suv, px) * ImpactParams.y;
    float lit = step(mean - 0.03 - bias, l);                              // brighter than its surroundings
    float core = step(mean + 0.2 - bias, l);                              // much brighter: the hottest areas
    float paperMask = saturate(max(lit, core) - ink);                     // 1 = paper, 0 = ink
    vec3 paper = ImpactPaper.rgb, inkC = ImpactInk.rgb, high = ImpactHigh.rgb;
    if (ImpactParams2.y > 0.5) { vec3 swap = paper; paper = inkC; inkC = swap; }
    if (mode == 0) return mix(mix(inkC, paper, paperMask), high, core * (1.0 - ink));                 // TWO_TONE
    if (mode == 1) return mix(inkC, paper, max(lit, core));                                          // SILHOUETTE
    if (mode == 2) return mix(paper, inkC, ink);                                                     // LINE_ART
    if (mode == 3) {                                                                                 // HALFTONE
        vec2 g = rot2(0.785398) * gl_FragCoord.xy / max(ImpactParams.z, 2.0);
        float dots = 1.0 - step(sqrt(saturate(1.0 - l)) * 0.6, length(fract(g) - 0.5));
        return mix(paper, inkC, max(ink, dots * (1.0 - core)));
    }
    if (mode == 4) {                                                                                 // POSTERIZE
        float levels = max(ImpactParams.z, 2.0);
        float q = floor(saturate(l + bias) * levels) / (levels - 1.0);
        return mix(mix(mix(inkC, paper, saturate(q)), high, core), inkC, ink);
    }
    if (mode == 5) return 1.0 - col;                                                                 // NEGATIVE
    if (mode == 6) {                                                                                 // GLITCH
        float off = 0.012 * ImpactParams.w;
        float right = lum(texture(DiffuseSampler, suv + vec2(off, 0.0)).rgb), left = lum(texture(DiffuseSampler, suv - vec2(off, 0.0)).rgb);
        return mix(inkC, paper, vec3(step(mean, right), paperMask, step(mean, left)));
    }
    if (mode == 7) {                                                                                 // RAINBOW
        vec2 d = (uv - Impact.yz) * vec2(aspect(), 1.0);
        float h = atan(d.y, d.x) / TAU + length(d) * 1.5 - Impact.w * 6.0 * ImpactParams2.x;
        return mix(inkC, mix(hueWheel(h), high, core), paperMask);
    }
    return mix(col, inkC, ink);                                                                      // INK_OUTLINE
}

void main() {
    vec2 uv = texCoord;
    if (Zoom.x != 1.0) uv = Zoom.yz + (uv - Zoom.yz) / Zoom.x;
    if (Shake.x > 0.0) uv += shakeOffset(GameTime * Shake.y) * Shake.x * 0.025;
    vec3 col, light;
    if (ZoomBlur.x > 0.001) zoomSmear(uv, col, light);
    else {
        col = splitChannels(uv);
        light = texture(GlowSampler, uv).rgb;
    }
    float gain = floodGain();
    col *= gain;
    if (Glow.x > 0.0) col = 1.0 - (1.0 - min(col, 1.0)) * exp(-light * Glow.x * gain);   // exponential screen
    col = grade(col);
    if (Vignette.a > 0.0) {
        float v = saturate(smoothstep(VignetteShape.x, VignetteShape.y, length(centered(texCoord))) * Vignette.a);
        col = col * mix(vec3(1.0), Vignette.rgb, v) + Vignette.rgb * v * 0.12;
    }
    col += Flash.rgb * min(Flash.a, 1.0);
    col = mix(col, vec3(1.0), smoothstep(1.0, 2.2, Flash.a) * 0.75);
    col = mix(col, Fade.rgb, saturate(Fade.a));
    if (Grain.x > 0.0) {
        float frame = floor(GameTime * Grain.y);
        float n = hash12(gl_FragCoord.xy * 0.731 + frame * vec2(19.17, 7.43)) + hash12(gl_FragCoord.yx + frame * 3.1) - 1.0;
        col += n * 0.15 * Grain.x;
    }
    if (Impact.x >= 0.0) col = impactFrame(col, uv);
    fragColor = vec4(saturate(col), 1.0);
}
