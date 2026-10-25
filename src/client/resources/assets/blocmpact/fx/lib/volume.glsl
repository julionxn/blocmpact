// blocmpact:lib/volume.glsl — raymarched participating media (fog, dust, smoke, energy clouds) that sit IN the world:
// hidden by terrain, lit with your colors, mixed with Beer-Lambert.
//
// Define the density (and optionally the emitted light) before including:
//   #define FX_DENSITY(p) myDensity(p)        // p camera-relative; returns density ≥ 0
//   #define FX_LIGHT(p, d) myColor(p, d)      // optional: light per unit of density (default: white)
//   #include "blocmpact:lib/volume.glsl"
// then: vec4 v = marchVolume(rd, t0, t1, steps, absorption);  base = base * v.a + v.rgb;
// t0/t1 come from an intersection (raySphere, rayBox, two planes...) clamped to sceneT.

#ifdef FX_DENSITY
#ifndef FX_LIGHT
#define FX_LIGHT(p, d) vec3(1.0)
#endif
vec4 marchVolume(vec3 rd, float t0, float t1, int steps, float absorption) {
    if (t1 <= t0) return vec4(0.0, 0.0, 0.0, 1.0);
    float dt = (t1 - t0) / float(steps);
    float t = t0 + dt * hash12(gl_FragCoord.xy);                // random start per pixel: fine noise instead of banding
    float trans = 1.0;
    vec3 light = vec3(0.0);
    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        vec3 p = rd * t;
        float dens = max(FX_DENSITY(p), 0.0);
        if (dens > 0.0) {
            float a = 1.0 - exp(-dens * dt * absorption);
            light += trans * a * FX_LIGHT(p, dens);
            trans *= 1.0 - a;
            if (trans < 0.02) break;
        }
        t += dt;
    }
    return vec4(light, trans);
}
#endif
