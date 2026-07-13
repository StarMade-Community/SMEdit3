# SMEdit3

A Java desktop editor for **StarMade** blueprints — ships, stations, and planets.
Open a blueprint, view and edit its blocks in 2D/3D, and save it back in the
current StarMade format.

!!! note "Project status"
    SMEdit3 is being modernized. It builds and runs on **Java 21** with an OpenGL
    renderer, reads and writes the current **`.smd3`** blueprint format, and saves
    modern `header`/`logic`/`meta` metadata. See the
    [reference docs](ARCHITECTURE.md) for architecture and StarMade-format details.

## Highlights

- Opens modern StarMade blueprint folders (`.smd3` block data + v5 metadata).
- Saves blueprints in the current format so StarMade can load them.
- OpenGL rendering (uses the discrete GPU on hybrid-graphics laptops).
- Plugin-driven tools: import/export (OBJ, DAE, Binvox, Minecraft schematic),
  smoothing, hull generation, fill, rotate, mirror, painting, and more.

## Get started

- [Getting Started](getting-started.md) — install, run, and point SMEdit at your
  StarMade folder.
- [Tutorials](tutorials/index.md) — step-by-step guides.

## For contributors

- [Architecture](ARCHITECTURE.md) — code structure and modernization roadmap.
- [StarMade compatibility](STARMADE_COMPATIBILITY.md) — how SMEdit maps to the
  current StarMade blueprint format.
