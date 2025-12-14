# SMEdit3 Modernization Issues

This file contains GitHub issue templates for the modernization effort. Copy and paste these into GitHub to create issues.

---

## Issue 1: Migrate Build System from Ant to Gradle ✅ COMPLETED

**Title**: Migrate build system from Ant to Gradle

**Labels**: `enhancement`, `build`, `completed`

**Body**:
```markdown
## Overview
The project currently uses Apache Ant with NetBeans project files for building. This should be migrated to Gradle for better dependency management and modern build tooling.

## Status
✅ **COMPLETED** - Gradle build system has been successfully implemented with multi-module support.

## What was done
- Created root build.gradle with Java 25 configuration
- Created settings.gradle defining all 3 modules (jo_sm, jo_plugin/JoFileMods, SMEdit)
- Created individual build.gradle files for each module
- Added Gradle wrapper (version 9.2.1)
- Successfully tested build with Java 25
- Updated .gitignore to exclude Gradle build artifacts

## Modules
1. **jo_sm** - Core library with LWJGL dependencies
2. **jo_plugin/JoFileMods** - Plugin module (depends on jo_sm)
3. **SMEdit** - Main application module (depends on jo_sm and JoFileMods)

## Build Commands
- `./gradlew build` - Build all modules
- `./gradlew clean` - Clean build artifacts
- `./gradlew :SMEdit:run` - Run the application

## Notes
- The old Ant build.xml files are still present but are no longer needed
- NetBeans project files (nbproject/) are still present for backward compatibility
- LWJGL 2.9.1 JARs are still used via file dependencies (migration to LWJGL 3 is a separate task)
```

---

## Issue 2: Upgrade Java Version from 7 to 25 ✅ COMPLETED

**Title**: Upgrade Java version from 7 to 25

**Labels**: `enhancement`, `build`, `completed`

**Body**:
```markdown
## Overview
The project currently uses Java 1.7 (Java 7). This should be upgraded to Java 25 to access modern language features and improvements.

## Status
✅ **COMPLETED** - All modules now compile with Java 25.

## What was done
- Configured Gradle to use Java 25 toolchain
- All modules successfully compile with Java 25
- Build verified with `javap` showing major version 69 (Java 25)

## Benefits
- Access to modern Java features:
  - Records (Java 14+)
  - Pattern matching (Java 16+)
  - Sealed classes (Java 17+)
  - Switch expressions (Java 14+)
  - Text blocks (Java 15+)
  - And many more features from Java 8-25
- Better performance and security
- Long-term support and updates

## Next Steps
Now that the codebase compiles with Java 25, we can gradually adopt modern Java features through incremental code modernization tasks.
```

---

## Issue 3: Migrate from LWJGL 2.9.1 to LWJGL 3.x

**Title**: Migrate from LWJGL 2.9.1 to LWJGL 3.x

**Labels**: `enhancement`, `dependencies`, `breaking-change`, `high-priority`

**Body**:
```markdown
## Overview
The project currently uses LWJGL 2.9.1 for OpenGL rendering. StarMade game has already been upgraded to use LWJGL 3.x, so the editor should be updated to match.

## Current State
- Code uses LWJGL 2.9.1 APIs from `lwjgl-2.9.1/jar/` directory
- LWJGL 2.x uses different APIs than 3.x (breaking changes)
- Dependencies are included as file JARs rather than Maven dependencies

## Why This Matters
- **Game Compatibility**: StarMade now uses LWJGL 3, so the editor should match
- **Modern OpenGL**: LWJGL 3 supports modern OpenGL features
- **Better Performance**: LWJGL 3 has performance improvements
- **Active Development**: LWJGL 2 is no longer maintained

## Changes Required

### 1. Update Dependencies in `jo_sm/build.gradle`
```gradle
dependencies {
    def lwjglVersion = '3.3.6'
    def lwjglNatives = 'natives-linux' // or natives-windows, natives-macos
    
    implementation platform("org.lwjgl:lwjgl-bom:$lwjglVersion")
    implementation "org.lwjgl:lwjgl"
    implementation "org.lwjgl:lwjgl-glfw"
    implementation "org.lwjgl:lwjgl-opengl"
    implementation "org.lwjgl:lwjgl-stb"
    
    runtimeOnly "org.lwjgl:lwjgl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-opengl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-stb::$lwjglNatives"
}
```

### 2. Update API Calls

| LWJGL 2.x | LWJGL 3.x |
|-----------|-----------|
| `org.lwjgl.opengl.Display` | GLFW window management |
| `org.lwjgl.input.Keyboard` | GLFW input callbacks |
| `org.lwjgl.input.Mouse` | GLFW mouse callbacks |
| `org.lwjgl.util.glu.GLU` | Use modern equivalents or include GLU separately |

### 3. Affected Files
- `jo_sm/src/jo/util/lwjgl/win/JGLCanvas.java` - Main canvas integration
- `jo_sm/src/jo/util/lwjgl/win/DrawLogic.java` - Drawing logic
- `jo_sm/src/jo/util/lwjgl/win/NodeDrawHandler.java` - Node drawing
- Any other files using LWJGL APIs (search for `org.lwjgl` imports)

### 4. Key Differences
- **Window Management**: LWJGL 3 uses GLFW instead of Display
- **Context Creation**: Different approach to OpenGL context
- **Input Handling**: Callback-based instead of polling in many cases
- **Threading Model**: LWJGL 3 requires window operations on main thread

## Testing Requirements
- [ ] Verify window creation and rendering
- [ ] Test keyboard input handling
- [ ] Test mouse input handling
- [ ] Verify OpenGL rendering works correctly
- [ ] Test on multiple platforms (Windows, Linux, macOS)
- [ ] Performance testing to ensure no regressions

## Resources
- [LWJGL 3 Migration Guide](https://www.lwjgl.org/guide)
- [LWJGL 3 Getting Started](https://www.lwjgl.org/guide)
- [GLFW Documentation](https://www.glfw.org/documentation.html)

## Estimated Effort
Medium to Large (2-3 weeks) - API changes are extensive and require careful testing
```

---

## Issue 4: Code Modernization - Use Diamond Operator and Type Inference

**Title**: Code Modernization: Use diamond operator and type inference

**Labels**: `enhancement`, `code-quality`, `good-first-issue`

**Body**:
```markdown
## Overview
Replace verbose generic type declarations with diamond operator (`<>`) and use `var` where appropriate.

## Examples

### Before (Java 7)
```java
ArrayList<String> list = new ArrayList<String>();
Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();
```

### After (Modern Java)
```java
ArrayList<String> list = new ArrayList<>();
var map = new HashMap<String, List<Integer>>();
```

## Estimated Changes
~200+ locations across the codebase

## Benefits
- More concise and readable code
- Less redundant type information
- Easier to maintain

## Implementation Notes
- Use diamond operator for all generic instantiations
- Use `var` for local variables where type is obvious from right-hand side
- Maintain explicit types for method parameters and return types
- Keep explicit types where they improve readability

## Tasks
- [ ] Update jo_sm module
- [ ] Update jo_plugin/JoFileMods module
- [ ] Update SMEdit module
- [ ] Run tests to ensure no regressions
```

---

## Issue 5: Code Modernization - Use Try-With-Resources

**Title**: Code Modernization: Replace manual resource management with try-with-resources

**Labels**: `enhancement`, `code-quality`, `bug-fix`

**Body**:
```markdown
## Overview
Replace manual resource management with try-with-resources statements to prevent resource leaks.

## Current State
~102 files use `FileInputStream`, `FileOutputStream`, `BufferedReader`, etc. with manual resource management.

## Example

### Before
```java
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
```

### After
```java
try (FileInputStream fis = new FileInputStream(file)) {
    // use fis
} catch (IOException e) {
    // handle
}
```

## Benefits
- Prevents resource leaks
- More concise code
- Automatic resource cleanup
- Better exception handling

## Implementation Plan
1. Identify all resource types that need updating
2. Update one module at a time
3. Test file I/O operations thoroughly
4. Look for nested resources that can be combined

## Resources to Update
- File streams (FileInputStream, FileOutputStream)
- Buffered readers/writers
- Database connections (if any)
- Network sockets (if any)
- Any class implementing AutoCloseable

## Tasks
- [ ] Audit all resource usage
- [ ] Update jo_sm module
- [ ] Update jo_plugin/JoFileMods module
- [ ] Update SMEdit module
- [ ] Test file operations thoroughly
```

---

## Issue 6: Code Modernization - Replace Raw Types with Generics

**Title**: Code Modernization: Replace raw types with proper generic types

**Labels**: `enhancement`, `code-quality`, `type-safety`

**Body**:
```markdown
## Overview
Add proper generic type parameters to all collection declarations to improve type safety.

## Example

### Before
```java
List list = new ArrayList();
Map map = new HashMap();
Iterator iterator = list.iterator();
```

### After
```java
List<String> list = new ArrayList<>();
Map<String, Object> map = new HashMap<>();
Iterator<String> iterator = list.iterator();
```

## Benefits
- Type safety at compile time
- Eliminates unchecked cast warnings
- Better IDE support (autocomplete, refactoring)
- Prevents ClassCastException at runtime

## Common Patterns to Fix
- Collections without type parameters
- Raw Iterator usage
- Generic method calls without type specification
- Unchecked casts that can be eliminated with proper generics

## Tasks
- [ ] Fix unchecked warnings in build output
- [ ] Add generic types to all collection declarations
- [ ] Update method signatures to use generic types
- [ ] Remove unnecessary casts
- [ ] Enable strict compiler warnings
```

---

## Issue 7: Code Modernization - Use Modern Java APIs

**Title**: Code Modernization: Migrate to modern Java APIs

**Labels**: `enhancement`, `code-quality`

**Body**:
```markdown
## Overview
Replace old APIs with modern equivalents where beneficial.

## Modernization Opportunities

### 1. File I/O - Use `java.nio.file.Path`
```java
// Old
File file = new File("/path/to/file");
// Modern
Path path = Paths.get("/path/to/file");
```

### 2. Collections - Use Stream API
```java
// Old
for (String item : list) {
    if (item.startsWith("test")) {
        results.add(item.toUpperCase());
    }
}
// Modern
var results = list.stream()
    .filter(item -> item.startsWith("test"))
    .map(String::toUpperCase)
    .toList();
```

### 3. Null Safety - Use Optional
```java
// Old
public String getValue() {
    return value != null ? value : "default";
}
// Modern
public Optional<String> getValue() {
    return Optional.ofNullable(value);
}
```

### 4. Date/Time - Use `java.time.*`
```java
// Old
Date now = new Date();
// Modern
LocalDateTime now = LocalDateTime.now();
```

### 5. String Formatting - Use Text Blocks
```java
// Old
String json = "{\n" +
              "  \"key\": \"value\"\n" +
              "}";
// Modern
String json = """
    {
      "key": "value"
    }
    """;
```

### 6. Data Classes - Use Records
```java
// Old
public class Point {
    private final int x;
    private final int y;
    // constructor, getters, equals, hashCode, toString
}
// Modern
public record Point(int x, int y) {}
```

## Tasks
- [ ] Identify candidates for Path API
- [ ] Find opportunities for Stream API
- [ ] Consider Optional for null-safety
- [ ] Replace Date with java.time
- [ ] Use text blocks for multi-line strings
- [ ] Create records for simple data classes
```

---

## Issue 8: Add Comprehensive Test Coverage

**Title**: Add comprehensive test coverage

**Labels**: `enhancement`, `testing`, `high-priority`

**Body**:
```markdown
## Overview
The project has minimal test coverage. We need comprehensive unit and integration tests.

## Current State
- Only 1 test file found: `jo_sm/src/jo/sm/ent/cmd/TestBeans.java`
- No test framework configured
- No CI running tests

## Requirements

### 1. Set up JUnit 5 (Jupiter)
Add to root build.gradle:
```gradle
subprojects {
    dependencies {
        testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }
    
    test {
        useJUnitPlatform()
    }
}
```

### 2. Test Structure
```
jo_sm/src/test/java/
jo_plugin/JoFileMods/src/test/java/
SMEdit/src/test/java/
```

### 3. Areas to Test

#### Core Logic Tests (jo_sm)
- [ ] Vector math operations
- [ ] Entity parsing/serialization
- [ ] File format readers/writers
- [ ] Data structure operations
- [ ] Utility classes

#### Plugin Tests (jo_plugin/JoFileMods)
- [ ] Plugin loading
- [ ] Filter operations
- [ ] Modification logic
- [ ] Data transformations

#### Application Tests (SMEdit)
- [ ] Configuration management
- [ ] Update checking
- [ ] Path handling

### 4. Test Types
- **Unit Tests**: Test individual classes in isolation
- **Integration Tests**: Test module interactions
- **File I/O Tests**: Test with sample StarMade files
- **Regression Tests**: Prevent fixed bugs from recurring

### 5. Code Coverage Goals
- Target: >70% code coverage
- Use JaCoCo for coverage reporting
- Add coverage badge to README

### 6. Test Data
- [ ] Create sample StarMade entity files for testing
- [ ] Document test data format
- [ ] Include edge cases and error conditions

## Benefits
- Catch bugs before they reach users
- Enable safe refactoring
- Document expected behavior
- Improve code quality

## Implementation Plan
1. Set up JUnit 5 and JaCoCo
2. Write tests for core utility classes first
3. Add tests for file I/O operations
4. Gradually increase coverage
5. Make tests part of CI pipeline

## Tasks
- [ ] Configure JUnit 5
- [ ] Configure JaCoCo for coverage
- [ ] Create test directory structure
- [ ] Write tests for jo_sm module
- [ ] Write tests for jo_plugin module
- [ ] Write tests for SMEdit module
- [ ] Achieve >70% coverage
- [ ] Document testing approach
```

---

## Issue 9: Improve Documentation

**Title**: Improve project documentation

**Labels**: `documentation`, `enhancement`

**Body**:
```markdown
## Overview
The project needs comprehensive documentation for developers and users.

## Current State
- Minimal README.md
- Limited code comments
- No architecture documentation
- No user guide

## Requirements

### 1. README.md Updates
- [ ] Project overview and description
- [ ] Build instructions for Gradle
- [ ] Development setup guide
- [ ] System requirements (Java 25)
- [ ] How to run the application
- [ ] Architecture overview diagram
- [ ] Contributing guidelines
- [ ] License information (already present)

### 2. Code Documentation
- [ ] Add JavaDoc to public APIs
- [ ] Document complex algorithms
- [ ] Explain design decisions
- [ ] Add inline comments for non-obvious code

### 3. Technical Documentation
- [ ] StarMade file format specifications
- [ ] Module architecture and dependencies
- [ ] Plugin system documentation
- [ ] LWJGL integration guide
- [ ] Build and release process

### 4. User Documentation
- [ ] Installation guide
- [ ] User manual / how-to guides
- [ ] Screenshots of the application
- [ ] Troubleshooting guide
- [ ] FAQ

### 5. Developer Documentation
- [ ] Development environment setup
- [ ] Code style guide
- [ ] Git workflow and branching strategy
- [ ] Testing strategy
- [ ] Release process

## Example README Structure
```markdown
# SMEdit3

Java-based editor for StarMade game entities.

## Features
- Edit StarMade ships, stations, and planets
- Plugin system for extensions
- OpenGL-based 3D rendering

## Requirements
- Java 25 or higher
- 4GB RAM recommended

## Building
\`\`\`bash
./gradlew build
\`\`\`

## Running
\`\`\`bash
./gradlew :SMEdit:run
\`\`\`

## Architecture
[Diagram showing module relationships]

## Contributing
See CONTRIBUTING.md

## License
Apache 2.0
\`\`\`

## Tasks
- [ ] Update README.md
- [ ] Add JavaDoc to public classes
- [ ] Create CONTRIBUTING.md
- [ ] Document StarMade file formats
- [ ] Create user guide
- [ ] Add architecture diagrams
- [ ] Set up documentation hosting (GitHub Pages?)
```

---

## Issue 10: Consolidate and Refactor Codebase Structure

**Title**: Consolidate and refactor codebase structure

**Labels**: `enhancement`, `refactoring`, `architecture`

**Body**:
```markdown
## Overview
Analyze and improve code organization for better maintainability.

## Areas to Review

### 1. Module Structure
- **Current**: 3 modules (jo_sm, jo_plugin/JoFileMods, SMEdit)
- **Question**: Is this split optimal?
- **Analysis needed**: 
  - Module dependency graph
  - Cyclic dependencies?
  - Module boundaries clear?

### 2. Package Organization
- **Review**: Are packages logically organized?
- **Consider**: Group by feature vs by layer
- **Example structure**:
  ```
  jo.sm.entity      - Entity data structures
  jo.sm.io          - File I/O
  jo.sm.render      - OpenGL rendering
  jo.sm.ui          - User interface
  jo.sm.util        - Utilities
  ```

### 3. Code Duplication
- [ ] Identify duplicate code
- [ ] Extract common utilities
- [ ] Create shared base classes
- [ ] Use composition over inheritance

### 4. Naming Conventions
- [ ] Ensure consistent naming
- [ ] Follow Java naming conventions
- [ ] Clear, descriptive names
- [ ] Avoid abbreviations unless common

### 5. Separation of Concerns
- [ ] Separate UI from business logic
- [ ] Separate data models from logic
- [ ] Use dependency injection where appropriate
- [ ] Apply SOLID principles

### 6. Code Complexity
- [ ] Identify classes >500 LOC
- [ ] Find methods >50 LOC
- [ ] Calculate cyclomatic complexity
- [ ] Refactor complex code

### 7. Dead Code
- [ ] Find unused classes
- [ ] Find unused methods
- [ ] Remove commented-out code
- [ ] Remove debug code

## Analysis Tools
- Use Gradle plugins for code metrics
- Consider SonarQube for analysis
- Generate dependency graphs
- Use IDE refactoring tools

## Metrics to Track
- Lines of code per class
- Method complexity
- Package coupling
- Test coverage
- Code duplication percentage

## Tasks
- [ ] Generate dependency analysis
- [ ] Identify overly complex classes
- [ ] Find and eliminate duplicated code
- [ ] Review and improve package structure
- [ ] Document architecture decisions
- [ ] Create refactoring plan
```

---

## Issue 11: Setup CI/CD Pipeline

**Title**: Setup CI/CD pipeline with GitHub Actions

**Labels**: `enhancement`, `ci-cd`, `automation`, `high-priority`

**Body**:
```markdown
## Overview
Automate build, test, and release processes using GitHub Actions.

## Requirements

### 1. Build and Test Workflow

Create `.github/workflows/build.yml`:
```yaml
name: Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-results
        path: '**/build/test-results/'
    
    - name: Generate coverage report
      run: ./gradlew jacocoTestReport
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v4
      with:
        files: '**/build/reports/jacoco/test/jacocoTestReport.xml'
```

### 2. Code Quality Checks

Create `.github/workflows/code-quality.yml`:
```yaml
name: Code Quality

on: [push, pull_request]

jobs:
  checkstyle:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
    - run: ./gradlew checkstyleMain
  
  spotbugs:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
    - run: ./gradlew spotbugsMain
```

### 3. Release Workflow

Create `.github/workflows/release.yml`:
```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
    
    - name: Build distribution
      run: ./gradlew build distZip distTar
    
    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        files: |
          SMEdit/build/distributions/*.zip
          SMEdit/build/distributions/*.tar
        generate_release_notes: true
```

### 4. Documentation Generation

Add JavaDoc generation:
```yaml
- name: Generate JavaDoc
  run: ./gradlew javadoc

- name: Deploy to GitHub Pages
  uses: peaceiris/actions-gh-pages@v3
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    publish_dir: ./build/docs/javadoc
```

## Additional Features

### Code Coverage Badge
- [ ] Set up Codecov integration
- [ ] Add coverage badge to README

### Dependency Checking
- [ ] Add Dependabot configuration
- [ ] Automated dependency updates
- [ ] Security vulnerability scanning

### Matrix Testing
- [ ] Test on multiple OS (Linux, Windows, macOS)
- [ ] Test with different Java versions

### Caching
- [ ] Cache Gradle dependencies
- [ ] Cache build outputs
- [ ] Speed up build times

## Tasks
- [ ] Create build workflow
- [ ] Create code quality workflow
- [ ] Create release workflow
- [ ] Set up code coverage reporting
- [ ] Add status badges to README
- [ ] Configure Dependabot
- [ ] Set up security scanning
- [ ] Document CI/CD process
```

---

## Issue 12: Add @Deprecated Annotations

**Title**: Add @Deprecated annotations to deprecated methods

**Labels**: `enhancement`, `code-quality`, `good-first-issue`

**Body**:
```markdown
## Overview
Fix compiler warnings about deprecated methods not having `@Deprecated` annotation.

## Current State
22+ warnings in compilation output:
```
warning: [dep-ann] deprecated item is not annotated with @Deprecated
```

## Affected Files
- `jo_sm/src/jo/vecmath/Tuple3d.java`
- `jo_sm/src/jo/vecmath/Tuple4d.java`

## Example Fix

### Before
```java
/**
 * @deprecated Use clamp(double min, double max) instead
 */
public final void clamp(float min, float max) {
    // ...
}
```

### After
```java
/**
 * @deprecated Use clamp(double min, double max) instead
 */
@Deprecated
public final void clamp(float min, float max) {
    // ...
}
```

## Benefits
- Eliminates compiler warnings
- Makes deprecation explicit at both compile-time and runtime
- Better IDE support for deprecated API usage
- Follows Java best practices

## Tasks
- [ ] Add @Deprecated to all methods with @deprecated JavaDoc
- [ ] Verify build completes without deprecation warnings
- [ ] Consider if deprecated methods should be removed entirely
```

---

## Issue 13: Remove NetBeans Project Files (Optional)

**Title**: Remove obsolete NetBeans and Ant build files

**Labels**: `cleanup`, `low-priority`

**Body**:
```markdown
## Overview
Now that Gradle build is working, we can optionally remove the old NetBeans and Ant build files.

## Files to Remove
- `*/build.xml` (Ant build scripts)
- `*/nbproject/` (NetBeans project files)
- `*/manifest.mf` (NetBeans manifest files)

## Before Removing
⚠️ **Wait for team confirmation that:**
- [ ] Nobody is using NetBeans IDE
- [ ] Gradle build is fully tested and working
- [ ] All team members have migrated to Gradle

## Considerations
- **Keep if**: Team still uses NetBeans IDE
- **Remove if**: Everyone has moved to IntelliJ/Eclipse/VS Code
- **Alternative**: Keep but mark as deprecated in README

## Benefits of Removal
- Cleaner repository
- Single source of truth for build configuration
- Less maintenance burden
- Smaller repository size

## Risks
- May inconvenience team members still using NetBeans
- Some IDE features may rely on these files

## Recommendation
Mark this as **low priority** and decide later once team has fully adopted Gradle.
```

---

## Summary

Total issues: 13
- ✅ Completed: 2 (Gradle migration, Java 25 upgrade)
- 🔴 High Priority: 3 (LWJGL 3 migration, Testing, CI/CD)
- 🟡 Medium Priority: 6 (Code modernization, Documentation, Refactoring)
- 🟢 Low Priority: 2 (Deprecated annotations, File cleanup)

**Recommended Implementation Order:**
1. Issue 3 (LWJGL 3) - HIGH
2. Issue 8 (Tests) - HIGH
3. Issue 11 (CI/CD) - HIGH
4. Issues 4-7 (Modernization) - MEDIUM
5. Issue 9 (Documentation) - MEDIUM
6. Issue 10 (Refactoring) - MEDIUM
7. Issue 12 (Annotations) - LOW
8. Issue 13 (Cleanup) - LOW
