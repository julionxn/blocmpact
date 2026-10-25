// blocmpact:internal/glow_reduce — one step down the glow pyramid: the dual-filter downsample described by Marius
// Bjørge ("Bandwidth-Efficient Rendering", SIGGRAPH 2015): the center four times plus four diagonal taps, each a
// bilinear read of 2×2 source pixels. On the first step it reads only the light that the effects ADDED on top of the
// untouched scene, so the sun or snow never glow; pixels a pass invented (alpha 0) or a thresholdBloom request use a
// plain bright-pass instead.
uniform vec2 SrcTexel;
uniform float ExtractAdded, ThresholdBloom;

vec3 addedLight(vec2 uv) {
    vec4 fx = texture(DiffuseSampler, uv);
    vec3 base = texture(SceneSampler, uv).rgb;
    vec3 gain = max(fx.rgb - base, 0.0);                                   // channels that got brighter
    float lifted = max(lum(fx.rgb) - lum(base), 0.0) / max(lum(fx.rgb), 0.001);
    vec3 added = max(gain, fx.rgb * lifted);                               // tinted light: the share that explains the gain
    float peak = max(fx.r, max(fx.g, fx.b));
    vec3 bright = fx.rgb * smoothstep(0.72, 1.0, peak);
    return mix(added * 0.6, bright * 0.5, saturate(max(ThresholdBloom, 1.0 - fx.a)));
}

vec3 source(vec2 uv) { return ExtractAdded > 0.5 ? addedLight(uv) : texture(DiffuseSampler, uv).rgb; }

void main() {
    vec3 sum = source(texCoord) * 4.0;
    sum += source(texCoord + vec2(-SrcTexel.x, -SrcTexel.y)) + source(texCoord + vec2(SrcTexel.x, -SrcTexel.y));
    sum += source(texCoord + vec2(-SrcTexel.x, SrcTexel.y)) + source(texCoord + vec2(SrcTexel.x, SrcTexel.y));
    fragColor = vec4(sum * 0.125, 1.0);
}
