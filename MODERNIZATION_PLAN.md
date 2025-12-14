# SMEdit3 Modernization Plan

This document outlines the modernization strategy for migrating SMEdit from Java 7/8 with Ant to Java 25 with Gradle.

## Project Overview

SMEdit is a Java GUI-based editor for editing entities for the game StarMade. The codebase consists of:
- **464 Java source files** in a single consolidated module
- **Current structure**: Standard Gradle layout with `src/main/java/` and `src/main/resources/`
- **Original build system**: Apache Ant with NetBeans (migrated to Gradle)
- **Original Java version**: Java 1.7 (Java 7) (upgraded to Java 25)
- **Key dependency**: LWJGL 2.9.1 for OpenGL rendering (pending migration to LWJGL 3.x)

## Completed Tasks

### ✅ Task 1: Migrate Build System from Ant to Gradle

**Status**: COMPLETED

**What was done**:
- Created root `build.gradle` with Java 25 toolchain configuration
- Created `settings.gradle` defining multi-module project structure
- Created individual `build.gradle` files for each module:
  - `jo_sm/build.gradle` - Core library with LWJGL dependencies
  - `jo_plugin/JoFileMods/build.gradle` - Plugin module
  - `SMEdit/build.gradle` - Main application with application plugin
- Added Gradle wrapper (version 9.2.1)
- Updated `.gitignore` to exclude Gradle build artifacts
- Successfully tested build: `BUILD SUCCESSFUL`
- Verified bytecode is Java 25 (major version 69)

**Build Commands** (original):
```bash
./gradlew build          # Build all modules
./gradlew clean          # Clean build artifacts  
./gradlew :SMEdit:run    # Run the application
```

**Notes**:
- Old Ant `build.xml` files were removed during consolidation
- Old NetBeans project files were removed during consolidation
- Project now uses standard Gradle structure

### ✅ Task 2: Upgrade Java Version from 7 to 25

**Status**: COMPLETED

**What was done**:
- Configured Gradle to use Java 25 toolchain
- All modules now compile with Java 25
- Build verified with `javap` showing major version 69 (Java 25)

**Benefits**:
- Access to modern Java features (records, pattern matching, sealed classes, etc.)
- Better performance and security
- Long-term support and updates

### ✅ Task 3: Consolidate to Standard Gradle Structure

**Status**: COMPLETED

**What was done**:
- Consolidated 3 modules (jo_sm, jo_plugin/JoFileMods, SMEdit) into a single module
- Migrated all source code to standard Gradle structure: `src/main/java/` and `src/main/resources/`
- Merged duplicate utility classes from different modules (URLs, GlobalConfiguration, Paths, etc.)
- Updated `build.gradle` to single-module configuration
- Updated `settings.gradle` to remove subproject references
- Removed old module source directories and NetBeans build files
- All 464 Java files now in unified structure under `src/main/java/jo/`
- Build verified: `BUILD SUCCESSFUL`

**Benefits**:
- Standard Gradle project structure that follows Java conventions
- Reduced code duplication
- Simpler build configuration
- Easier maintenance and navigation
- Clear separation between source and resources

**Build Commands** (updated):
```bash
./gradlew build          # Build the project
./gradlew clean          # Clean build artifacts  
./gradlew run            # Run the application
```

### ✅ Task 4: Migrate from LWJGL 2.9.1 to LWJGL 3.x

**Status**: COMPLETED

**What was done**:
- Updated `build.gradle` to use LWJGL 3.3.6 from Maven Central with BOM
- Added LWJGL 3.3.6 dependencies: lwjgl, lwjgl-glfw, lwjgl-opengl, lwjgl-stb
- Included native libraries for Linux, Windows, and macOS
- Removed old LWJGL 2.9.1 file dependencies from `jo_sm/lwjgl-2.9.1/`
- Created `GLUHelper.java` to replace deprecated GLU functions (gluPerspective, gluProject, gluUnProject)
- Updated `JGLCanvas.java`:
  - Replaced `Display` with GLFW window management
  - Replaced `Keyboard` polling with GLFW key callbacks
  - Replaced `Mouse` polling with GLFW mouse callbacks
  - Updated OpenGL context creation using GLFW
- Updated all OpenGL API calls to use LWJGL 3 signatures:
  - `glLight` → `glLightfv`
  - `glMaterial` → `glMaterialfv`
  - `glLightModel` → `glLightModelfv`
  - `glGetFloat` → `glGetFloatv`
  - `glGetInteger` → `glGetIntegerv`
  - `glMultMatrix` → `glMultMatrixf`
  - `glLoadMatrix` → `glLoadMatrixf`
  - `glFog` → `glFogfv`
  - Pointer functions now include type parameter (e.g., `glVertexPointer(3, GL_FLOAT, 0, buffer)`)
- Updated 9 files total:
  - `JGLCanvas.java` - Major refactoring for GLFW
  - `DrawLogic.java` - Use GLUHelper
  - `NodeDrawHandler.java` - Use GLUHelper and updated API calls
  - `LightDrawHandler.java` - Fixed glLight calls
  - `ObjDrawHandler.java` - Fixed pointer function signatures
  - `ParticleDrawHandler.java` - Fixed matrix operations
  - `JGLTextureCache.java` - No changes needed
  - `PointsDrawHandler.java` - No changes needed
  - `RectDrawHandler.java` - No changes needed
- Build verified: `BUILD SUCCESSFUL`

**Benefits**:
- Matches StarMade game's LWJGL 3 version for compatibility
- Access to modern OpenGL features and better performance
- Active development and support from LWJGL 3
- Multi-platform support (Linux, Windows, macOS) via Maven

## Pending Tasks

### 🔲 Task 4: Code Modernization - Use Diamond Operator and Type Inference

**Priority**: MEDIUM

**Description**: Replace verbose generic type declarations with diamond operator (`<>`) and use `var` where appropriate.

**Examples**:
```java
// Old (Java 7)
ArrayList<String> list = new ArrayList<String>();
Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();

// Modern (Java 25)
ArrayList<String> list = new ArrayList<>();
var map = new HashMap<String, List<Integer>>();
```

**Estimated Changes**: ~200+ locations across codebase

### 🔲 Task 5: Code Modernization - Use Try-With-Resources

**Priority**: MEDIUM

**Description**: Replace manual resource management with try-with-resources statements.

**Current State**: ~102 files use `FileInputStream`, `FileOutputStream`, `BufferedReader`, etc.

**Example**:
```java
// Old
FileInputStream fis = null;
try {
    fis = new FileInputStream(file);
    // use fis
} catch (IOException e) {
    // handle
} finally {
    if (fis != null) {
        try {
            fis.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

// Modern
try (FileInputStream fis = new FileInputStream(file)) {
    // use fis
} catch (IOException e) {
    // handle
}
```

### 🔲 Task 6: Code Modernization - Replace Raw Types with Generics

**Priority**: MEDIUM

**Description**: Add proper generic type parameters to all collection declarations.

**Example**:
```java
// Old
List list = new ArrayList();
Map map = new HashMap();

// Modern
List<String> list = new ArrayList<>();
Map<String, Object> map = new HashMap<>();
```

### 🔲 Task 7: Code Modernization - Use Modern Java APIs

**Priority**: LOW to MEDIUM

**Description**: Replace old APIs with modern equivalents where beneficial.

**Examples**:
- Use `java.nio.file.Path` instead of `java.io.File` where appropriate
- Use `Stream` API for collection operations
- Use `Optional` for null-safety
- Use modern date/time APIs (`java.time.*`) instead of `Date`
- Use `String.format()` or text blocks for complex strings
- Consider using records for simple data classes

### 🔲 Task 8: Add Comprehensive Test Coverage

**Priority**: HIGH

**Current State**: Only 1 test file found (`jo_sm/src/jo/sm/ent/cmd/TestBeans.java`)

**Requirements**:
1. Set up JUnit 5 (Jupiter) test framework
2. Add unit tests for core logic classes
3. Add integration tests for file I/O operations
4. Add tests for StarMade entity parsing/serialization
5. Aim for >70% code coverage

**Test Structure**:
```
jo_sm/src/test/java/
jo_plugin/JoFileMods/src/test/java/
SMEdit/src/test/java/
```

### 🔲 Task 9: Improve Documentation

**Priority**: MEDIUM

**Current State**: Minimal documentation

**Requirements**:
1. Update README.md with:
   - Build instructions for Gradle
   - Development setup guide
   - Architecture overview
   - Contributing guidelines
2. Add JavaDoc to public APIs
3. Document StarMade file format specifications
4. Add code comments for complex algorithms
5. Create user documentation

### 🔲 Task 10: Consolidate and Refactor Codebase Structure

**Priority**: MEDIUM

**Description**: Analyze and improve code organization.

**Areas to Review**:
1. **Module structure**: Is the 3-module split optimal?
2. **Package organization**: Are packages logically organized?
3. **Code duplication**: Identify and eliminate duplicate code
4. **Naming conventions**: Ensure consistent naming
5. **Separation of concerns**: UI vs logic vs data
6. **Dead code**: Remove unused classes/methods

**Analysis Needed**:
- Dependency graph between modules
- Class coupling and cohesion metrics
- Identify overly complex classes (>500 LOC)

### 🔲 Task 11: Setup CI/CD Pipeline

**Priority**: HIGH

**Description**: Automate build, test, and release processes.

**Requirements**:
1. GitHub Actions workflow for:
   - Build on push/PR
   - Run tests
   - Code quality checks (SpotBugs, Checkstyle)
   - Security scanning
2. Automated releases:
   - Tag-based releases
   - Generate changelog
   - Upload artifacts (JARs)
3. Code coverage reporting
4. Documentation generation and hosting

**Example Workflow**:
```yaml
name: Build and Test
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '25'
      - run: ./gradlew build test
```

### 🔲 Task 12: Add Deprecated Annotations

**Priority**: LOW

**Description**: Fix warnings about deprecated methods not having `@Deprecated` annotation.

**Current State**: 22+ warnings about deprecated methods in `Tuple3d.java` and `Tuple4d.java`

**Example**:
```java
@Deprecated
public final void clamp(float min, float max) {
    // ...
}
```

### 🔲 Task 13: Remove NetBeans Project Files (Optional)

**Priority**: LOW

**Description**: Once Gradle migration is confirmed working, remove Ant and NetBeans files.

**Files to Remove**:
- `*/build.xml`
- `*/nbproject/`
- `*/manifest.mf`

**Note**: Keep until team confirms no longer using NetBeans IDE.

## Implementation Order Recommendation

1. **Task 3** (LWJGL 3 migration) - HIGH priority, needed for game compatibility
2. **Task 8** (Add tests) - HIGH priority, enables safe refactoring
3. **Task 11** (CI/CD) - HIGH priority, automates quality checks
4. **Task 4-7** (Code modernization) - MEDIUM priority, can be done incrementally
5. **Task 9** (Documentation) - MEDIUM priority, ongoing effort
6. **Task 10** (Refactoring) - MEDIUM priority, after tests are in place
7. **Task 12** (Deprecated annotations) - LOW priority, quick wins
8. **Task 13** (Remove old files) - LOW priority, cleanup

## Migration Strategy

### Incremental Approach
- Tackle one task at a time
- Create feature branches for each major change
- Maintain backward compatibility where possible
- Extensive testing after each change

### Testing Strategy
- Add tests before refactoring (Task 8 first)
- Manual testing of UI and rendering after LWJGL migration
- Performance benchmarking to ensure no regressions

### Documentation Strategy
- Update documentation alongside code changes
- Document breaking changes and migration notes
- Maintain changelog

## Success Criteria

✅ Code compiles with Java 25
✅ Uses Gradle for building
- Uses LWJGL 3.x matching StarMade
- >70% test coverage
- CI/CD pipeline running
- Modern Java idioms used throughout
- Well-documented codebase
- Clean, maintainable code structure

## Timeline Estimate

- **Task 3** (LWJGL 3): 2-3 weeks
- **Task 8** (Tests): 3-4 weeks (ongoing)
- **Task 11** (CI/CD): 1 week
- **Tasks 4-7** (Modernization): 2-3 weeks
- **Task 9** (Documentation): 1-2 weeks (ongoing)
- **Task 10** (Refactoring): 2-3 weeks

**Total Estimate**: 2-3 months for core modernization

## Resources

- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [LWJGL 3 Migration Guide](https://www.lwjgl.org/guide)
- [Java 25 Features](https://openjdk.org/projects/jdk/25/)
- [Modern Java Best Practices](https://www.baeldung.com/java-best-practices)

## Questions for Team

1. Are we ready to completely remove NetBeans project files?
2. What StarMade file format versions do we need to support?
3. Are there any specific StarMade API changes we need to accommodate?
4. What is the target minimum Java version for users? (Can we require Java 25?)
5. Are there any existing unit tests or test data we should preserve?
6. What is the release/deployment process currently?
