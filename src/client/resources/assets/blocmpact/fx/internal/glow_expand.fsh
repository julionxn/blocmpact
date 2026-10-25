// blocmpact:internal/glow_expand — one step up the glow pyramid: Bjørge's dual-filter upsample (four taps two
// half-texels out along the axes, four diagonal ones at half the distance and double weight) of the smaller level,
// added to the reduced level of this size with weight `Spread` (wider glow as it grows).
uniform vec2 SrcTexel;
uniform float Spread;
uniform sampler2D LevelSampler;      // the reduced level of the size being written

void main() {
    vec2 h = SrcTexel * 0.5;
    vec3 axes = texture(DiffuseSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb + texture(DiffuseSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb
              + texture(DiffuseSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb + texture(DiffuseSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb;
    vec3 diagonals = texture(DiffuseSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(DiffuseSampler, texCoord + vec2(h.x, h.y)).rgb
                   + texture(DiffuseSampler, texCoord + vec2(-h.x, -h.y)).rgb + texture(DiffuseSampler, texCoord + vec2(h.x, -h.y)).rgb;
    vec3 wider = (axes + diagonals * 2.0) / 12.0;
    fragColor = vec4(texture(LevelSampler, texCoord).rgb + wider * Spread, 1.0);
}
