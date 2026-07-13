SMEdit3
=======
version 1.3.67

[![Build](https://github.com/StarMade-Community/SMEdit3/actions/workflows/build.yml/badge.svg)](https://github.com/StarMade-Community/SMEdit3/actions/workflows/build.yml)

A Java-based GUI editor for editing entities (ships, stations, planets) for the game StarMade.

## Documentation

- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — current project structure, subsystem map, code-health issues, and the prioritized modernization roadmap.
- **[docs/STARMADE_COMPATIBILITY.md](docs/STARMADE_COMPATIBILITY.md)** — how SMEdit's frozen (~2014) format understanding has drifted from current StarMade (`.smd3`/32³ segments, per-block bit layout, `BlockStyle` shapes, data-driven block config) and a remediation checklist.

These supersede the older inline plan below (the deleted `MODERNIZATION_PLAN.md`).

## Recent Updates

### ✅ Modernization (December 2025)
- **Build System**: Migrated from Apache Ant to Gradle
- **Java Version**: Upgraded from Java 7 to Java 21
- **Project Structure**: Consolidated to standard Gradle structure with single module
- **Gradle Wrapper**: Added for consistent builds across environments

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

## Project Structure

The project now uses a standard Gradle structure with all code consolidated into a single module:

```
SMEdit3/
├── src/
│   └── main/
│       ├── java/          # All Java source code (464 files)
│       │   └── jo/
│       │       ├── log/       # Logging framework
│       │       ├── sm/        # StarMade core functionality
│       │       │   ├── data/      # Data structures
│       │       │   ├── edit/      # Main application entry
│       │       │   ├── ent/       # Entity handling
│       │       │   ├── factories/ # Plugin factories
│       │       │   ├── logic/     # Business logic
│       │       │   ├── mods/      # Plugin system
│       │       │   ├── plugins/   # Built-in plugins
│       │       │   ├── ship/      # Ship-specific code
│       │       │   └── ui/        # User interface
│       │       ├── util/      # Utilities
│       │       └── vecmath/   # Vector math library
│       └── resources/     # Resources (images, config files)
├── jo_sm/
│   └── lwjgl-2.9.1/   # LWJGL 2 library (legacy, to be migrated)
├── build.gradle       # Build configuration
└── settings.gradle    # Project settings
```

## Modernization Plan

### Completed
- ✅ Gradle build system
- ✅ Java 21 upgrade
- ✅ Consolidated to standard Gradle structure (single module)

### In Progress / Planned
- 🟡 Consolidate library folders and move to gradle lib definitions where possible
- 🟡 Add comprehensive test coverage
- 🟡 Setup CI/CD pipeline
- 🟡 Code modernization (use modern Java features)
- 🟡 Improve documentation
- 🟡 Further refactoring and code quality improvements 
- 🟡 UI style modernization (FlatLaf?)
- 🟡 Improved model importing
- 🟡 Improved 3d camera controls, and the ability to switch to a 2d mode for different axes
- 🟡 Ability to view/edit/export cross-sections
- 🟡 Modernize UI assets
- 🟡 Ability to create and set different panel layout presets / workspaces and fast switching between them
- 🟡 Rework / modernize / simplify plugin system
- 🟡 Lua or python scripting?

## Contributing

We welcome contributions! The modernization effort provides many opportunities to help:
- Migrate code to use modern Java features
- Add tests for existing functionality
- Improve documentation

## History

- **version 1.3.67** - Started GitHub codebase
- Expanded application log system
- Added log tab to the main pane
- Brand new re-coded SMEdit

## License

Apache License 2.0 - See [LICENSE](LICENSE) file for details.
