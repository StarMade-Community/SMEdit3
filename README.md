SMEdit3
=======
version 1.3.67

A Java-based GUI editor for editing entities (ships, stations, planets) for the game StarMade.

## Recent Updates

### ✅ Modernization (December 2025)
- **Build System**: Migrated from Apache Ant to Gradle
- **Java Version**: Upgraded from Java 7 to Java 25
- **Multi-module Build**: Properly configured 3-module Gradle project
- **Gradle Wrapper**: Added for consistent builds across environments

## Requirements

- **Java 25** or higher
- 4GB RAM recommended

## Building

```bash
./gradlew build
```

This will build all three modules:
- `jo_sm` - Core library with LWJGL support
- `jo_plugin/JoFileMods` - Plugin system
- `SMEdit` - Main application

## Running

```bash
./gradlew :SMEdit:run
```

## Project Structure

```
SMEdit3/
├── jo_sm/              # Core library (322 Java files)
│   ├── src/           # Source code
│   └── build.gradle   # Module build configuration
├── jo_plugin/
│   └── JoFileMods/    # Plugin module (139 Java files)
│       ├── src/       # Plugin source code
│       └── build.gradle
├── SMEdit/            # Main application (10 Java files)
│   ├── src/           # Application source
│   └── build.gradle   # Application build configuration
├── build.gradle       # Root build configuration
└── settings.gradle    # Multi-module settings
```

## Modernization Plan

We are actively modernizing this codebase. See [MODERNIZATION_PLAN.md](MODERNIZATION_PLAN.md) for the complete modernization strategy.

### Completed
- ✅ Gradle build system
- ✅ Java 25 upgrade

### In Progress / Planned
- 🔴 Migrate to LWJGL 3.x (HIGH - StarMade now uses LWJGL 3)
- 🔴 Add comprehensive test coverage (HIGH)
- 🔴 Setup CI/CD pipeline (HIGH)
- 🟡 Code modernization (use modern Java features)
- 🟡 Improve documentation
- 🟡 Refactor and consolidate codebase

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
