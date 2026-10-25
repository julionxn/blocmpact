# Blocmpact

Style-neutral visual effects API for Minecraft 26.3 (Fabric, Java 25). It ships no effects and no look of its own:
it gives you generic building blocks to make any special effect quickly, from Java or from data-driven JSON + GLSL.

[Español más abajo](#español)

- **Timelines** with variable speed and a shared seed: every player sees the same effect from a single packet.
- **Level 0**: shaped particle emission, a configurable particle (`blocmpact:fx`, also additive), positional, head, edge and distance-delayed sounds.
- **Level 1**: HUD on a fixed-size canvas, with markers anchored to the world.
- **Level 2**: world geometry through Minecraft's render API (billboards, beams, ribbons, rings, spheres, cylinders).
- **Level 3** (OpenGL): full-screen shader passes, named and persistent targets, frame history, bloom of added light, compositor (grading, vignette, flash, shake, zoom, grain, held frames) and impact frames.
- **Environment**: fog, sky, sun/moon/stars, clouds (color, height, holes), world light, rain and thunder.
- **Camera and cutscenes**, **entities** (outline and poses of any model), **JSON effects** with expressions, commands, F3+T reload and accessibility options.

## Requirements

Minecraft 26.3, Fabric Loader ≥ 0.19.5, [Fabric API](https://modrinth.com/mod/fabric-api), Java 25.
Level 3 (full-screen shaders) needs *Options → Video → Graphics API → Prefer OpenGL*; with Vulkan everything else
keeps working and the game says so once in chat.

## Installing

**Players**: put Blocmpact and Fabric API in `mods/`. When a server mod uses Blocmpact, install it on the server and on
every client. Client-only installs can play local and data-driven effects with `/blocmpactc`.

**Mod developers**: depend on it from the Modrinth Maven.

```groovy
repositories {
    maven { url = "https://api.modrinth.com/maven"; content { includeGroup "maven.modrinth" } }
}
dependencies {
    implementation "maven.modrinth:blocmpact:1.0.0+26.3"
}
```

```json
"depends": { "blocmpact": ">=1.0.0" }
```

## Documentation

**[julionxn.github.io/blocmpact](https://julionxn.github.io/blocmpact/)**: concepts, every layer, JSON
format, GLSL library, recipes and examples tested in game. The site is plain HTML in [`docs/`](docs), served by GitHub
Pages.

## Building

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build                 # jar in build/libs
./gradlew publishToMavenLocal   # local dependency: me.julionxn:blocmpact:1.0.0+26.3
python tools/validate_fx.py <glslang> src/client/resources/assets   # validate shaders
```

Releases: bump `version` in `gradle.properties`, add its section to [CHANGELOG.md](CHANGELOG.md) and push the tag
`v<version>`; the release workflow uploads it to Modrinth and GitHub.

## License

[MIT](LICENSE).

---

## Español

API de efectos visuales para Minecraft 26.3 (Fabric, Java 25), sin estilo propio: piezas genéricas para crear rápido
cualquier efecto especial, desde Java o con JSON + GLSL.

- **Línea de tiempo** con velocidad variable y semilla compartida: todos los jugadores ven lo mismo con un solo paquete.
- **Nivel 0**: partículas por formas y una partícula configurable (`blocmpact:fx`, también aditiva), sonido posicional, en la cabeza, en el borde de una zona o retrasado por la distancia.
- **Nivel 1**: HUD en un lienzo de tamaño fijo, con marcadores anclados al mundo.
- **Nivel 2**: geometría en el mundo con la API oficial (billboards, haces, cintas, anillos, esferas, cilindros).
- **Nivel 3** (OpenGL): pasadas de shader a pantalla completa, objetivos con nombre y persistentes, historial de frames, bloom de la luz añadida, compositor (grading, viñeta, destello, temblor, zoom, grano, dibujos retenidos) e impact frames.
- **Entorno**: niebla, cielo, sol/luna/estrellas, nubes (color, altura, agujeros), luz del mundo, lluvia y tormenta.
- **Cámara y cinemáticas**, **entidades** (contorno y poses de cualquier modelo), **efectos por JSON** con expresiones, comandos, recarga con F3+T y opciones de accesibilidad.

**Requisitos**: Minecraft 26.3, Fabric Loader ≥ 0.19.5, Fabric API y Java 25. El nivel 3 necesita
*Opciones → Vídeo → Graphics API → Prefer OpenGL*.

**Instalación**: Blocmpact y Fabric API en `mods/`. Si un mod de servidor usa Blocmpact, instálalo en el servidor y en
todos los clientes. Para depender de él desde tu mod, usa el Maven de Modrinth (ver arriba) o `publishToMavenLocal`.

**Documentación**: [julionxn.github.io/blocmpact](https://julionxn.github.io/blocmpact/) (en inglés; HTML en `docs/`, publicado con GitHub Pages).

**Licencia**: [MIT](LICENSE).
