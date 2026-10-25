// blocmpact:lib/color.glsl — color spaces, ramps, blend modes and tonemapping curves. #include "blocmpact:lib/color.glsl"
// No palette is imposed: these are tools to build your own. Third-party formulas are credited where they appear and
// listed in THIRD_PARTY_NOTICES.md.

// ---- spaces
// sRGB transfer curves (IEC 61966-2-1), exact piecewise form.
vec3 toLinear(vec3 c) {
    c = max(c, 0.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}
vec3 toGamma(vec3 c) {
    c = max(c, 0.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}
// HSV (hue in turns): max/min form, and back with the k-based formula (hue sector per channel).
vec3 rgb2hsv(vec3 c) {
    float hi = max(c.r, max(c.g, c.b)), lo = min(c.r, min(c.g, c.b)), span = hi - lo;
    float h = 0.0;
    if (span > 1e-6) {
        if (hi == c.r) h = mod((c.g - c.b) / span, 6.0);
        else if (hi == c.g) h = (c.b - c.r) / span + 2.0;
        else h = (c.r - c.g) / span + 4.0;
    }
    return vec3(h / 6.0, hi > 1e-6 ? span / hi : 0.0, hi);
}
vec3 hsv2rgb(vec3 c) {
    vec3 k = mod(vec3(5.0, 3.0, 1.0) + c.x * 6.0, 6.0);
    return c.z - c.z * c.y * clamp(min(k, 4.0 - k), 0.0, 1.0);
}
// Oklab (Björn Ottosson, public domain / MIT): perceptual space, for even gradients between two colors.
vec3 rgb2oklab(vec3 c) {
    vec3 l = toLinear(c);
    vec3 lms = vec3(0.4122214708 * l.r + 0.5363325363 * l.g + 0.0514459929 * l.b,
                    0.2119034982 * l.r + 0.6806995451 * l.g + 0.1073969566 * l.b,
                    0.0883024619 * l.r + 0.2817188376 * l.g + 0.6299787005 * l.b);
    lms = pow(max(lms, 0.0), vec3(1.0 / 3.0));
    return vec3(0.2104542553 * lms.x + 0.7936177850 * lms.y - 0.0040720468 * lms.z,
                1.9779984951 * lms.x - 2.4285922050 * lms.y + 0.4505937099 * lms.z,
                0.0259040371 * lms.x + 0.7827717662 * lms.y - 0.8086757660 * lms.z);
}
vec3 oklab2rgb(vec3 c) {
    vec3 lms = vec3(c.x + 0.3963377774 * c.y + 0.2158037573 * c.z, c.x - 0.1055613458 * c.y - 0.0638541728 * c.z,
                    c.x - 0.0894841775 * c.y - 1.2914855480 * c.z);
    lms = lms * lms * lms;
    vec3 l = vec3(4.0767416621 * lms.x - 3.3077115913 * lms.y + 0.2309699292 * lms.z,
                  -1.2684380046 * lms.x + 2.6097574011 * lms.y - 0.3413193965 * lms.z,
                  -0.0041960863 * lms.x - 0.7034186147 * lms.y + 1.7076147010 * lms.z);
    return toGamma(l);
}
vec3 mixOklab(vec3 a, vec3 b, float k) { return oklab2rgb(mix(rgb2oklab(a), rgb2oklab(b), k)); }

// ---- adjustments
vec3 hueShift(vec3 c, float turns) { vec3 h = rgb2hsv(c); h.x = fract(h.x + turns); return hsv2rgb(h); }
vec3 saturation(vec3 c, float s) { return mix(vec3(lum(c)), c, s); }
vec3 contrast(vec3 c, float k, float pivot) { return (c - pivot) * k + pivot; }
vec3 posterize(vec3 c, float levels) { return floor(c * levels + 0.5) / levels; }

// ---- ramps
// Incandescence from Planck's law sampled at three wavelengths: dark red → orange → yellow → warm white as x goes
// 0 → 1 (900 K → 5200 K). Energy grows with x, up to 2 at x = 1 (so the hottest part blooms).
vec3 blackbody(float x) {
    x = saturate(x);
    float kelvin = mix(900.0, 5200.0, x);
    vec3 micrometers = vec3(0.610, 0.550, 0.465);                   // representative red, green and blue
    vec3 radiance = 1.0 / (pow(micrometers, vec3(5.0)) * (exp(14388.0 / (micrometers * kelvin)) - 1.0));
    vec3 chroma = radiance / max(radiance.r, max(radiance.g, radiance.b));
    return chroma * (0.15 + 1.85 * x * sqrt(x));
}
// Visible spectrum for a wavelength in nanometers (380–780), after Dan Bruton's piecewise approximation (1996).
vec3 wavelength(float nm) {
    vec3 c;
    if (nm < 440.0) c = vec3(-(nm - 440.0) / 60.0, 0.0, 1.0);
    else if (nm < 490.0) c = vec3(0.0, (nm - 440.0) / 50.0, 1.0);
    else if (nm < 510.0) c = vec3(0.0, 1.0, -(nm - 510.0) / 20.0);
    else if (nm < 580.0) c = vec3((nm - 510.0) / 70.0, 1.0, 0.0);
    else if (nm < 645.0) c = vec3(1.0, -(nm - 645.0) / 65.0, 0.0);
    else c = vec3(1.0, 0.0, 0.0);
    float f = nm < 420.0 ? 0.3 + 0.7 * (nm - 380.0) / 40.0 : nm > 700.0 ? 0.3 + 0.7 * (780.0 - nm) / 80.0 : 1.0;
    return saturate(c) * saturate(f);
}
// Smooth hue wheel (0–1 → full saturation rainbow).
vec3 hueWheel(float h) { return hsv2rgb(vec3(fract(h), 1.0, 1.0)); }
// Cosine palette (Inigo Quilez): any smooth palette from four vec3 (a + b*cos(TAU*(c*t + d))).
vec3 cosinePalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) { return a + b * cos(TAU * (c * t + d)); }
// Gradient through up to 4 stops (positions in .w, ascending).
vec3 gradient4(float t, vec4 s0, vec4 s1, vec4 s2, vec4 s3) {
    vec3 c = s0.rgb;
    c = mix(c, s1.rgb, smoothstep(s0.w, s1.w, t));
    c = mix(c, s2.rgb, smoothstep(s1.w, s2.w, t));
    c = mix(c, s3.rgb, smoothstep(s2.w, s3.w, t));
    return c;
}

// ---- blend modes (base, layer)
vec3 blendScreen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - b); }
vec3 blendOverlay(vec3 a, vec3 b) { return mix(2.0 * a * b, 1.0 - 2.0 * (1.0 - a) * (1.0 - b), step(0.5, a)); }
vec3 blendSoftLight(vec3 a, vec3 b) { return mix(2.0 * a * b + a * a * (1.0 - 2.0 * b), sqrt(max(a, 0.0)) * (2.0 * b - 1.0) + 2.0 * a * (1.0 - b), step(0.5, b)); }
vec3 blendMultiply(vec3 a, vec3 b) { return a * b; }
vec3 blendDodge(vec3 a, vec3 b) { return a / max(1.0 - b, 1e-3); }
// Light that tints what it touches: the base is multiplied by (1 + light), so dark stays dark and lit surfaces take the color.
vec3 illuminate(vec3 base, vec3 light) { return base + base * light; }

// ---- tonemapping curves (HDR → 0–1). tonemap() in core.glsl is the default soft shoulder.
vec3 tonemapReinhard(vec3 c) { return c / (1.0 + c); }
vec3 tonemapExp(vec3 c, float exposure) { return 1.0 - exp(-c * exposure); }
// Krzysztof Narkowicz's fit of the ACES filmic curve (CC0 / MIT).
vec3 tonemapACES(vec3 x) { return saturate((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)); }
// Inverse of tonemapExp: to add light in linear space on top of an already tonemapped pass.
vec3 untonemapExp(vec3 c, float exposure) { return -log(max(1.0 - c, 1e-4)) / exposure; }
