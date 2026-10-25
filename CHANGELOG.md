# Changelog

## 1.0.0+26.3

First public release, for Minecraft 26.3 (Fabric Loader ≥ 0.19.5, Fabric API ≥ 0.162.0, Java 25).

- Effect timelines with variable speed and a shared seed: every player sees the same effect from a single packet.
- Level 0: shaped particle emission, a configurable `blocmpact:fx` particle (also additive) and positional, head, edge and speed-of-sound delayed sounds.
- Level 1: HUD on a fixed-size canvas, with markers anchored to the world.
- Level 2: world geometry through Minecraft's render API (billboards, beams, ribbons, rings, discs, spheres, cylinders, boxes).
- Level 3 (OpenGL): full-screen shader passes, named and persistent targets, frame history, bloom of added light, compositor (grading, vignette, flash, shake, zoom, grain, held frames) and impact frames; GLSL library in `blocmpact:lib/*`.
- Environment: fog, sky, sun/moon/stars, clouds (color, height, holes), world light, rain and thunder.
- Camera shake, roll, FOV and offset; cutscenes made of shots with blends, fades and letterbox.
- Entities: colored outline and part poses for any model.
- Data-driven effects in resource packs (JSON + expressions + GLSL), reloaded with F3+T; `/blocmpact` (server) and `/blocmpactc` (local) commands; accessibility options.
- Works with view bobbing: shader effects, world geometry and HUD markers stay aligned with the world while walking, and cutscene cameras do not inherit the walking bob.
