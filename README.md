SMEdit3
=======

[![Build](https://github.com/StarMade-Community/SMEdit3/actions/workflows/build.yml/badge.svg)](https://github.com/StarMade-Community/SMEdit3/actions/workflows/build.yml)

A Java-based GUI editor for editing entities (ships, stations, planets) for the game StarMade.
Original SMEdit by bobbybighoof, tambry, and csnewman.

## Requirements

- **Java 21** or higher
- 4GB RAM recommended

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run
```

## Roadmap

### Completed
- ✅ Gradle build system
- ✅ Java 21 upgrade
- ✅ Consolidated to standard Gradle structure (single module)
- ✅ Consolidated library folders into Gradle/Maven dependencies (LWJGL 2.9.3, FlatLaf, JUnit); removed the vendored LWJGL SDK and other dead code
- ✅ Builds and runs on Java 21 — **OpenGL renderer by default** (software fallback), with automatic discrete-GPU offload on hybrid-graphics laptops
- ✅ StarMade install auto-discovery + manual folder picker
- ✅ Reads **and writes** the current `.smd3` blueprint format (32³ / v6) plus v5 header/meta and v0 logic — opens and saves modern blueprints
- ✅ Preserves a ship's logic/control-element connections on round-trip save
- ✅ Block colors sampled from the game's own textures and cached to disk
- ✅ Reworked plugin system — the in-tree tools (edit/import/export/generate/select) are registered and surfaced in the menus
- ✅ UI style modernization — FlatLaf dark theme
- ✅ Test suite + CI — JUnit coverage of the format/save/plugin core (with bundled blueprint fixtures), GitHub Actions build, and a published docs site
- ✅ Improved documentation — MkDocs site plus architecture and StarMade-compatibility references

### In Progress / Planned
- 🟡 Further code modernization & refactoring (route remaining prints through the logging framework, EDT hardening, deprecated-API cleanup)
- 🟡 Simplify the plugin discovery model (ServiceLoader/annotations, typed classifications)
- 🟡 Improved model importing
- 🟡 Improved 3d camera controls, and the ability to switch to a 2d mode for different axes
- 🟡 Ability to view/edit/export cross-sections
- 🟡 Modernize UI assets
- 🟡 Ability to create and set different panel layout presets / workspaces and fast switching between them
- 🟡 Lua or python scripting?

## License

Apache License 2.0 - See [LICENSE](LICENSE) file for details.
