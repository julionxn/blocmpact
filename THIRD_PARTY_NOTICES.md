# Third-party notices

Blocmpact is licensed under the MIT license (see `LICENSE`). Its GLSL library includes or adapts the following code
by other authors. Each piece is used under the license its author published it with, and is marked with a comment
where it appears.

## Code

### "Hash without Sine" — David Hoskins

Used in `assets/blocmpact/fx/lib/core.glsl` (`hash11`, `hash12`, `hash13`, `hash22`, `hash33`).
Source: https://www.shadertoy.com/view/4djSRW

```
Copyright (c) 2014 David Hoskins.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Distance functions, smooth minimum, normals and cosine palettes — Inigo Quilez

Used in `assets/blocmpact/fx/lib/core.glsl` (`sdSegment2`), `sdf.glsl` (`sdBox2`, `sdRoundBox2`, `sdBox`,
`sdRoundBox`, `sdCapsule`, `sdTorus`, `sdCylinderY`, `smin`, `fxNormal`) and `color.glsl` (`cosinePalette`).
Sources: https://iquilezles.org/articles/distfunctions/, https://iquilezles.org/articles/distfunctions2d/,
https://iquilezles.org/articles/smin/, https://iquilezles.org/articles/normalsSDF/,
https://iquilezles.org/articles/palettes/. The author publishes code that implements algorithms under the MIT license.

```
Copyright (c) Inigo Quilez.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Oklab — Björn Ottosson

Used in `assets/blocmpact/fx/lib/color.glsl` (`rgb2oklab`, `oklab2rgb`). Source: https://bottosson.github.io/posts/oklab/.
The author released this code into the public domain (and alternatively under the MIT license); Blocmpact uses it
under the public-domain dedication.

### ACES filmic tone mapping fit — Krzysztof Narkowicz

Used in `assets/blocmpact/fx/lib/color.glsl` (`tonemapACES`).
Source: https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/. The author offers it under CC0
(public domain) or MIT; Blocmpact uses it under CC0.

## Published techniques (no code copied)

These are written for Blocmpact from the published descriptions; they are credited for transparency.

- Dual-filter blur for the glow pyramid — Marius Bjørge, "Bandwidth-Efficient Rendering", SIGGRAPH 2015.
- Quintic interpolation for noise — Ken Perlin, "Improving Noise", SIGGRAPH 2002.
- Cellular noise — Steven Worley, "A Cellular Texture Basis Function", SIGGRAPH 1996.
- Visible-spectrum approximation (`wavelength`) — Dan Bruton, "Approximate RGB values for Visible Wavelengths", 1996.
- Hexagonal cell rounding (`hexGrid`) — the cube-coordinate method described by Amit Patel (Red Blob Games).
- Planck's law (`blackbody`) and the sRGB transfer curves of IEC 61966-2-1.
