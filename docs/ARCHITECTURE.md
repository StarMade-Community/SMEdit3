# SMEdit3 — Architecture & Code-Health Map

> Status: 2026-07. Reflects the codebase at branch `master` (post Ant→Gradle /
> Java 21 consolidation). Companion doc:
> [STARMADE_COMPATIBILITY.md](STARMADE_COMPATIBILITY.md).
>
> **Progress:** Phases 0, 2, 3, and 4 of the roadmap (§8) are done — the editor
> builds and runs on Java 21, opens **and saves** the modern `.smd3` format, the
> in-tree plugins are wired into the menus, block colors are sampled from the
> game's textures, and there is a JUnit suite + CI. The main deferred item is the
> `smc.smedit.vecmath` trim (Phase 1), kept as-is for now because it is pure
> LOC cleanup with no functional payoff (see §5.4).

SMEdit3 is a legacy Java **Swing + LWJGL 2** desktop editor for StarMade
blueprints (ships, stations, planets). It reads/writes StarMade's on-disk
blueprint folders and provides a 3D voxel editor plus a plugin-driven set of
transforms (smooth, hull, fill, import/export, etc.).

- **~94,000 LOC across 464 Java files**, single Gradle module, Java 21 toolchain.
- All source under `src/main/java/smc/smedit/` — packages `smc.smedit`,
  `smc.smedit.util`, `smc.smedit.vecmath`, `smc.smedit.log`. Renamed from the
  legacy `jo.*` namespace in **Phase 0** (see §8).

---

## 1. Package map

| Package | Files | Role |
|---|---:|---|
| `smc.smedit.data` | 22 | Domain model: `StarMade` app-bean, `SparseMatrix<Block>` block store, `BlockTypes` (hardcoded IDs), hand-rolled `Vector3b/f/i/s` |
| `smc.smedit.ship.data` | 11 | Blueprint file-format model: `Blueprint`, `Header`, `Chunk`, `Data`, `Meta`, `Block`, `DockEntry` |
| `smc.smedit.ship.logic` | 9 | Serialization + geometry: `ShipLogic`, `HeaderLogic`, `DataLogic`, `SmoothLogic`, `HullLogic`, `WedgeLogic`, `CornerLogic` |
| `smc.smedit.ent` | 17 | StarMade entity serialization + native `Tag`/`TagType` format; 7 standalone CLI `main()` tools in `ent.cmd` |
| `smc.smedit.logic` | 11+ | App logic: `StarMadeLogic` (app-state singleton), `BlueprintLogic`, `IOLogic`, `RenderLogic`, `RenderPolyLogic` |
| `smc.smedit.ui` + `smc.smedit.ui.act` | ~90 | Swing UI: `RenderFrame` (main window), render panels, ~50 `AbstractAction`s, JavaBeans property-editor framework for plugin dialogs |
| `smc.smedit.ui.lwjgl` | 5 | Hardware render panel bridging to `smc.smedit.util.jgl` |
| `smc.smedit.plugins` | 121 | 45 `IBlocksPlugin` transforms (import/export/edit/select/paint/terrain), registered by `BuiltinPlugins` |
| `smc.smedit.factories` | 18 | 4 data-driven plugin factories (definition XMLs packaged as resources) |
| `smc.smedit.mods` | 5 | Plugin interfaces (`IBlocksPlugin`, `IStarMadePluginFactory`, …) |
| `smc.smedit.util.jgl` | 34 | Bespoke OpenGL-agnostic scene-graph library |
| `smc.smedit.util.lwjgl` | 13 | LWJGL 2 canvas/render thread — the only code touching `org.lwjgl.*` |
| `smc.smedit.util` | ~10 | `Paths`, `GlobalConfiguration`, `Update`/`HttpClient` (self-update), `OptionScreen` |
| `smc.smedit.vecmath` | 41 | **Vendored copy of Sun `javax.vecmath` (Java 3D 1.2)** — ~30k LOC (Phase-1 dead-file trim done; deeper trim deferred, §5.4) |
| `smc.smedit.log` | 5 | Custom `java.util.logging` handlers (routes logs to the Swing log tab) |

---

## 2. Application lifecycle

The boot path was simplified in **Phase 0**. The old flow downloaded
`jo_sm.jar` from a remote server at startup and reflectively ran a `Boot` class
inside it; that remote-download bootstrap was removed and the in-tree editor is
now launched directly:

1. `smc.smedit.SMEdit.main()` → `GpuOffload.preferDiscreteGpu()` (Linux dGPU
   re-exec, see §3) → `GlobalConfiguration.createDirectories()` → opens
   `OptionScreen`.
2. `smc.smedit.util.OptionScreen` — Swing config dialog (memory, texture pack,
   StarMade game folder), persisted to `~/.josm`. Its **"Start SMEdit"** button
   launches the editor directly.
3. `smc.smedit.ui.RenderFrame` — the actual main editor window. `RenderFrame.main`
   → `preLoad()` (`StarMadeLogic.setBaseDir()`: resolves the install, points LWJGL
   at the install's natives via `org.lwjgl.librarypath`, registers the built-in
   plugins and discovers any external plugin jars) →
   `startup()` builds the UI. The renderer defaults to OpenGL, with a software
   fallback (see §3). Auto-loads a hardcoded default blueprint `"Omen-Navy-Class"`
   ([RenderFrame.java:141](../src/main/java/smc/smedit/ui/RenderFrame.java)).

**Path conventions** (`smc.smedit.util.Paths`): everything hangs off
`<starmade.home>/third-party/SMEdit/` (`Plugins/`, `Logs/`, `Cache/`,
`Settings/`, `resources/`, `Screenshots/`); config file is `~/.josm`.

**StarMade-dir discovery (Phase 0):** `validateCurrentDirectory()` resolves the
install in order — saved `starmade.home` → current dir → a bounded list of
common install locations → a **folder picker**
([StarMadeDirChooser](../src/main/java/smc/smedit/ui/StarMadeDirChooser.java),
with a "use anyway" override). The old unbounded recursive `$HOME` scan was
removed. Validation is now consistent (`StarMade.jar` present, dropping the
extra `CrashAndBugReport.jar` requirement that modern StarMade may not ship). The
OptionScreen also has a **Browse…** button for manual selection. (Minor
follow-up: the resolver still exists in two places — `Paths` and
`StarMadeLogic`/`RenderFrame.preLoad` — worth consolidating into one.)

---

## 3. Rendering — two independent paths

**OpenGL is now the default renderer** (Phase 0). `RenderFrame` probes whether
the LWJGL natives can load and falls back to the software renderer if not;
`-software`/`-noopengl` force software, `-opengl` forces hardware.

- **Hardware path (default)**: `LWJGLRenderPanel` → `LWJGLRenderLogic` → builds a
  scene graph in `smc.smedit.util.jgl` → rendered by
  `smc.smedit.util.lwjgl.win.JGLCanvas`, which spawns its own "Render Thread" and
  is the only code touching `org.lwjgl.opengl.GL11`/`Display`/`Keyboard`/`Mouse`.
  `jgl` also feeds the OBJ/DAE exporters.
- **Software path (fallback)**: `AWTRenderPanel` → `RenderPolyLogic` /
  `RenderLogic` → isometric polygons via plain Java2D + `smc.smedit.vecmath`
  matrices. No OpenGL.

**LWJGL versioning (important):** the Java classes come from **LWJGL 2.9.3** on
Maven Central (matching StarMade). The **native** libraries are loaded at
runtime from the **StarMade install's** `native/<os>` folder (via
`org.lwjgl.librarypath`, set in `StarMadeLogic#setBaseDir`) — because StarMade
rebuilds its LWJGL natives for Java 9+, whereas the stock LWJGL 2.x natives
(including Maven's `lwjgl-platform`) still link the removed libjawt
`SUNWprivate_1.1` symbol and fail to load on Java 21. So the editor's GL
renderer always matches the game's, and no natives are vendored.

**Discrete-GPU offload:** on Linux hybrid-graphics ("Optimus") systems the
editor re-execs itself onto the dedicated GPU at startup
([GpuOffload.java](../src/main/java/smc/smedit/util/GpuOffload.java)) — NVIDIA
via `__NV_PRIME_RENDER_OFFLOAD`/`__GLX_VENDOR_LIBRARY_NAME=nvidia`, AMD via
`DRI_PRIME=1` — because those GLX vars must be set before the GL context is
created (a running JVM can't change its own environment for native libs). It
detects GPUs from `/sys/bus/pci/devices` (no external commands). macOS switches
automatically; Windows uses the driver profile. Opt out with `-igpu` or
`SMEDIT_NO_GPU_OFFLOAD=1`. Verified: this routes LWJGL onto the NVIDIA driver
(`GL_RENDERER: NVIDIA …`) instead of the Intel iGPU.

Maintaining two renderers roughly doubles the rendering surface area
(consolidation is a later cleanup).

---

## 4. Plugin system

- **Interfaces** (`smc.smedit.mods`): `IStarMadePlugin` (metadata, `newParameterBean()`,
  `getClassifications()` → untyped `int[][]` type/subtype pairs),
  `IBlocksPlugin` (adds `modify(SparseMatrix<Block>, params, StarMade, callback)`),
  `IStarMadePluginFactory` (`getPlugins()`).
- **✅ In-tree plugins are now registered (Phase 2).** A new
  [`smc.smedit.plugins.BuiltinPlugins`](../src/main/java/smc/smedit/plugins/BuiltinPlugins.java)`.register()`
  instantiates all **45 built-in `IBlocksPlugin`s + 4 factories** directly from
  the application classpath (via method-reference `Supplier`s, so the compiler
  verifies each has a no-arg constructor), each isolated in `try/catch` so one
  bad plugin can't stop the rest. It is called from `StarMadeLogic.setBaseDir`
  **after** the base dir is set (the factories parse the block config in their
  constructors) and is idempotent. This replaces the old model where the plugins
  lived in a separate `JoFileMods.jar` **downloaded at runtime**.
- **External discovery still supported** (`StarMadeLogic.discoverPlugins`): also
  scans `<home>/Plugins/*.jar` for `MANIFEST.MF` `BlocksPlugins`/`PluginFactories`
  attributes and loads them via `URLClassLoader`, so third-party plugin jars keep
  working alongside the built-ins.
- **Factory data files are now packaged.** The three data-driven factories
  (material/vegetation/view-filter) parse an XML that used to live under
  `src/main/java` (never packaged); `build.gradle` now ships `**/*.xml`/`**/*.dae`
  from the source tree as classpath resources, and each factory degrades
  gracefully (empty, no throw) if its definitions file is missing.
- **Remaining modernization (optional, not MVP):** the discovery is still
  stringly-typed — `int[][]` classifications rather than enums, `Class.newInstance()`,
  manifest attributes rather than `ServiceLoader`/annotations. Fine for a release;
  a nice later cleanup.

**Plugin inventory** (45 `IBlocksPlugin` + 4 factories): macro record/run;
selection ops (all/none/copy/cut/paste/delete); ship edit (harden/smooth/soften);
fill/deck; hull; move/rotate/scale/reflect; paint (stripes/ombre/text/image);
terrain generators (dome/causeway/volcano); **importers** OBJ, VRML, Binvox,
Minecraft `.schematic`; **exporters** OBJ, DAE, Images. Each plugin ships as a
triad: `XxxPlugin` + `XxxParameters` + `XxxParametersBeanInfo` (the last drives
an auto-generated Swing dialog).

---

## 5. Code-health issues (ranked)

1. ✅ **RESOLVED (Phase 0) — entry point.** `mainClass`/manifest now point at the
   real `smc.smedit.SMEdit` (renamed from `jo.sm.edit.SMEdit`); the remote
   `jo_sm.jar`/`Boot` download was removed and `RenderFrame` is launched in-tree.
   `./gradlew run` works on the Java 21 toolchain.
2. ✅ **RESOLVED (Phase 0) — `sys_paths` hack + native loading.** The reflective
   `ClassLoader.sys_paths` mutation (threw on Java 9+) was removed from
   [StarMadeLogic.java](../src/main/java/smc/smedit/logic/StarMadeLogic.java);
   LWJGL natives now load from the StarMade install via `org.lwjgl.librarypath`
   (read lazily by LWJGL, so runtime-settable). Verified: LWJGL 2.9.3 + the
   install's Java-9+-compatible natives create a GL 4.6 context on Java 21.
3. ✅ **RESOLVED (Phase 2) — plugin registration.** The in-tree plugins are now
   wired into the menus via `BuiltinPlugins.register()` (see §4). The stringly-typed
   discovery (`int[][]`, `Class.newInstance()`, manifest attrs) remains as an
   optional later cleanup, but is no longer a functional gap.
4. **~30k LOC of vendored `javax.vecmath`** (`smc.smedit.vecmath`, 41 files after the
   Phase-1 dead-file trim). **Deferred:** re-analysis showed no zero-risk deletions
   remain — every class is cross-referenced, and the double-precision cluster is
   reachable from the externally-used float classes, so trimming it means the same
   cross-type-method surgery that previously produced 182 compile errors. It's pure
   LOC cleanup with no functional payoff, so it's parked until after the initial
   release.
5. 🟡 **Swing EDT — top-level path fixed.** `SMEdit.main` now marshals all Swing
   startup (look-and-feel, folder chooser, `OptionScreen`) onto the EDT via
   `SwingUtilities.invokeLater` (`GpuOffload` stays first on the main thread, before
   any AWT init); `RenderFrame.startup()` is reached only from `OptionScreen`'s
   EDT button handler, so the frame is now built/shown on the EDT. Remaining: the
   heavy `preLoad()` still runs on the EDT (a brief freeze, not a race), and the
   separate LWJGL render thread coexists by design.
6. 🟡 **Logging — critical paths use the framework; bulk sweep pending.** Config/
   startup errors now go through `java.util.logging` (routed to the log tab/file by
   `smc.smedit.log`), and 11 redundant `printStackTrace()`-after-`log.log` calls
   were removed. A wider sweep of the remaining ~260 `System.out/err` prints +
   `printStackTrace()` in non-critical paths is a follow-up.
7. 🟡 **Silent catches — config I/O fixed.** The empty catch blocks in `Paths`
   (settings read/write) and `OptionScreen`, plus the unchecked `mkdirs()` in
   `GlobalConfiguration.createDirectories`, now log a warning. Other empty catches
   in non-critical utilities remain a follow-up.
8. **Duplication & dead code.** `SparseMatrixNew`/`SparseMatrixOld` (vs the used
   `SparseMatrix`), `*Action`/`*Action1` menu-vs-toolbar duplicate pairs,
   `ent.cmd` CLI tools bundled into the GUI jar, ~294 commented-out statements.
9. ✅ **RESOLVED (Phase 0) — LWJGL dependency.** The vendored LWJGL 2.9.1 SDK
   (`jo_sm/lwjgl-2.9.1/`) was deleted; the Java classes now come from **LWJGL
   2.9.3** on Maven Central (matching StarMade), natives from the install. An
   LWJGL 3 migration remains an optional future improvement, no longer a
   Java-21 blocker.
10. **Pervasive legacy provenance** — 207 files carry a misspelled
    `@Auther Jo Jaquinta`; scattered deprecated APIs (`new Integer/Double`,
    `Class.newInstance`, `java.util.Date`). Cosmetic, but signals a tree that
    never had a lint/format pass.
11. ✅ **RESOLVED (Phase 0) — remote-code-download.** The startup path no longer
    fetches or executes `jo_sm.jar`/`Boot`, and the `jo_sm.jar`/`JoFileMods.jar`
    download in `Paths.validateCurrentDirectory()` was removed. (The `Update`
    self-updater class remains in-tree but unused — dead code to delete in a
    later phase.)
12. ✅ **RESOLVED (Phase 4) — tests + CI.** A JUnit 5 suite now covers the format
    round-trips (`.smd3` read/write, v5 header/logic/meta), blueprint loading,
    block-color approximation, and plugin registration; GitHub Actions builds the
    jar on every push (`.github/workflows/build.yml`) and publishes the docs site
    (`docs.yml`). See §6.

---

## 6. Test coverage & CI ✅

Addressed in Phase 4. `src/test/java` holds a JUnit 5 suite focused on the
highest-risk core — the format bit-packing/chunk-offset code — plus the new
subsystems:

- `ship/logic/Smd3LogicTest`, `Smbp5LogicTest` — `.smd3` and v5 header/logic/meta
  round-trips (including a real Isanth fixture, guarded by `assumeTrue`).
- `logic/BlueprintLoadTest` — end-to-end load of a modern blueprint directory.
- `ui/BlockColorApproxTest` — texture color sampling + on-disk cache invalidation.
- `plugins/BuiltinPluginsTest` — plugin/factory registration and menu queries.

CI runs `./gradlew build` on every push via `.github/workflows/build.yml`. The
install-dependent tests self-skip when no StarMade install is present, so CI
stays green while local runs exercise the full path.

---

## 7. Build & run

- `build.gradle`: `java` + `application` plugins, Java 21 toolchain, group
  `smc.smedit`, version `3.0.0`. **Dependencies** = `org.lwjgl.lwjgl:lwjgl:2.9.3`
  + `:lwjgl_util:2.9.3` from Maven Central (Java classes only; the LWJGL natives
  are loaded at runtime from the StarMade install — see §3), `com.formdev:flatlaf`
  (look-and-feel), and JUnit 5 for tests. A `sourceSets` rule also packages the
  plugin/factory data files (`**/*.xml`, `**/*.dae`) that live under
  `src/main/java` so they resolve as classpath resources at runtime.
- `mainClass` / manifest → `smc.smedit.SMEdit` (the real entry class after the
  Phase-0 rename). `./gradlew run` launches the editor on the Java 21 toolchain.
- Build/run needs a JDK 21 (there may be no `java` on `PATH`; e.g. IntelliJ's
  managed `~/.jdks/temurin-21.*` — set `JAVA_HOME`).
- Some runtime resources (the default `Omen-Navy-Class` blueprint, texture
  packs) are still expected under `<starmade.home>/third-party/SMEdit/` rather
  than bundled — a follow-up now that the remote-download bootstrap is gone.

---

## 8. Suggested modernization roadmap

Ordered so each phase unblocks the next. Items marked 🔴 are prerequisites to a
working build.

**Phase 0 — make it build & run (🔴 blockers) — ✅ DONE**
- ✅ Renamed `jo.* → smc.smedit.*` (464 files, "promote" mapping; entry class is
  now `smc.smedit.SMEdit`, matching `build.gradle`).
- ✅ Dropped the remote `Boot`/`jo_sm.jar` download + the `Update` auto-updater;
  `RenderFrame` is launched in-tree from `OptionScreen`'s "Start SMEdit" button.
- ✅ Removed the `sys_paths` reflection hack; LWJGL natives load from the install
  via `org.lwjgl.librarypath`.
- ✅ LWJGL 2.9.1 → 2.9.3 (Maven) and **OpenGL is now the default renderer** (with
  software fallback), rendering on the **discrete GPU** on Linux hybrid systems
  (`GpuOffload`). `./gradlew run` works on the Java 21 toolchain.
- ✅ StarMade-dir discovery: removed the recursive `$HOME` scan (saved → cwd →
  common locations → folder picker), relaxed validation to `StarMade.jar` only,
  and added a **Browse…** button + folder chooser for manual selection.

**Phase 1 — dependencies & structure (partial)**
- ✅ LWJGL: vendored 2.9.1 replaced by 2.9.3 from Maven Central + install natives
  (done with the Phase-0 OpenGL work). A future LWJGL 3 migration (GLFW
  window/input, affecting `JGLCanvas`/`DrawLogic`/`NodeDrawHandler`) is optional.
- ⏸️ **Deferred: shrink vendored `smc.smedit.vecmath`.** Step 1 done (deleted 21
  dead files, 85→41 files); steps 2-3 (strip double overloads / rewrite
  `TransformInteger`) are parked — re-analysis found no zero-risk deletions left
  and the remaining trim is high-risk cross-type surgery with no functional value
  (see §5.4). Not a release blocker.
- Follow-up: delete remaining dead code (`SparseMatrix{New,Old}`, `*Action1`
  dupes, commented blocks, the unused `Update`/`getDownloadCaches` remnants);
  move `ent.cmd` tools out of the GUI jar.

**Phase 2 — plugin system rework — ✅ DONE**
- ✅ Built-in plugins are compiled into the app and registered directly via
  `BuiltinPlugins.register()` (see §4), replacing the download-a-jar model;
  external plugin jars are still discovered. Factory data files are now packaged.
- Optional later polish: migrate manifest discovery to `ServiceLoader`/annotations,
  replace `int[][]` classifications with enums, decouple from concrete
  `SparseMatrix<Block>`.

**Phase 3 — correctness & format — ✅ DONE** (see [STARMADE_COMPATIBILITY.md](STARMADE_COMPATIBILITY.md))
- ✅ Reads **and writes** the current `.smd3` / 32³ / v6 segment format with the
  corrected per-block bit layout, plus v5 `header`/`meta` and v0 `logic`.
- ✅ Block metadata is driven from `BlockConfig.xml` at runtime, and render colors
  are sampled from the game's block textures (cached to disk — see the
  compatibility doc).
- ✅ Logic connections (round-trip): SMEdit reads the modern `logic.smbpl`
  control map correctly (the reader previously mis-parsed the `-1026` disk marker
  and returned empty) and preserves it on save. Loading a ship records its control
  map against the exact grid instance; `saveBlueprint` writes it back only when the
  model is still that grid, so an edited/imported/new grid safely falls back to an
  empty map (a wrong map is worse than an empty one). Verified by a byte-exact
  round-trip (`LogicLogicTest`) and an end-to-end load→save fixture test
  (`BlueprintSaveTest`), both using the bundled `blueprints/` fixtures.
- Remaining: *synthesizing* a control map for edited/new ships (vs. preserving a
  loaded one) still needs the coordinate-space confirmation done in-game; and the
  meta has no `SEG_MANAGER` tag yet.

**Phase 4 — quality & UX (largely done)**
- ✅ JUnit test set (round-trip format tests + subsystem coverage) + GitHub Actions
  CI and docs publishing.
- ✅ FlatLaf dark look-and-feel.
- 🟡 Stability pass (partial): top-level Swing startup moved onto the EDT; config/
  startup I/O errors now logged (no longer swallowed); redundant `printStackTrace`
  removed. Remaining: the bulk `System.out/err`→logger sweep and the non-critical
  empty catches. Further README items (improved camera/2D modes, cross-sections,
  workspace layouts, scripting) are post-release.
