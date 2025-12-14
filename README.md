SMEdit3
=======
version 1.3.67

A Java-based GUI editor for editing entities (ships, stations, planets) for the game StarMade.

## Recent Updates

### ✅ Modernization (December 2025)
- **Build System**: Migrated from Apache Ant to Gradle
- **Java Version**: Upgraded from Java 7 to Java 25
- **Project Structure**: Consolidated to standard Gradle structure with single module
- **Gradle Wrapper**: Added for consistent builds across environments

## Requirements

- **Java 25** or higher
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

We are actively modernizing this codebase. See [MODERNIZATION_PLAN.md](MODERNIZATION_PLAN.md) for the complete modernization strategy.

### Completed
- ✅ Gradle build system
- ✅ Java 25 upgrade
- ✅ Consolidated to standard Gradle structure (single module)

### In Progress / Planned
- 🔴 Migrate to LWJGL 3.x (HIGH - StarMade now uses LWJGL 3)
- 🔴 Add comprehensive test coverage (HIGH)
- 🔴 Setup CI/CD pipeline (HIGH)
- 🟡 Code modernization (use modern Java features)
- 🟡 Improve documentation
- 🟡 Further refactoring and code quality improvements

Issue templates are available in [.github/ISSUE_TEMPLATES/modernization_issues.md](.github/ISSUE_TEMPLATES/modernization_issues.md)

## Contributing

We welcome contributions! The modernization effort provides many opportunities to help:
- Migrate code to use modern Java features
- Add tests for existing functionality
- Update LWJGL 2 code to LWJGL 3
- Improve documentation

## History

- **version 1.3.67** - Started GitHub codebase
- Expanded application log system
- Added log tab to the main pane
- Brand new re-coded SMEdit

## License

Apache License 2.0 - See [LICENSE](LICENSE) file for details.
