# SMEdit3 — Architecture & Code-Health Map

> Status: analysis snapshot, 2026-07. Reflects the codebase at branch `master`
> (post Ant→Gradle / Java 21 consolidation). Companion doc:
> [STARMADE_COMPATIBILITY.md](STARMADE_COMPATIBILITY.md).

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
| `smc.smedit.plugins` | 121 | 45 `IBlocksPlugin` transforms (import/export/edit/select/paint/terrain) |
| `smc.smedit.factories` | 18 | 4 data-driven plugin factories |
| `smc.smedit.mods` | 5 | Plugin interfaces (`IBlocksPlugin`, `IStarMadePluginFactory`, …) |
| `smc.smedit.util.jgl` | 34 | Bespoke OpenGL-agnostic scene-graph library |
| `smc.smedit.util.lwjgl` | 13 | LWJGL 2 canvas/render thread — the only code touching `org.lwjgl.*` |
| `smc.smedit.util` | ~10 | `Paths`, `GlobalConfiguration`, `Update`/`HttpClient` (self-update), `OptionScreen` |
| `smc.smedit.vecmath` | 85 | **Vendored copy of Sun `javax.vecmath` (Java 3D 1.2)** — ~30k LOC |
| `smc.smedit.log` | 5 | Custom `java.util.logging` handlers (routes logs to the Swing log tab) |

---

## 2. Application lifecycle

The boot path was simplified in **Phase 0**. The old flow downloaded
`jo_sm.jar` from a remote server at startup and reflectively ran a `Boot` class
inside it; that remote-download bootstrap was removed and the in-tree editor is
now launched directly:

1. `smc.smedit.SMEdit.main()` → `GlobalConfiguration.createDirectories()` →
   opens `OptionScreen`.
2. `smc.smedit.util.OptionScreen` — Swing config dialog (memory, texture pack,
   StarMade game folder), persisted to `~/.josm`. Its **"Start SMEdit"** button
   launches the editor directly.
3. `smc.smedit.ui.RenderFrame` — the actual main editor window. `RenderFrame.main`
   → `preLoad()` (`StarMadeLogic.setBaseDir()`: resolves the install, points LWJGL
   at the install's natives via `org.lwjgl.librarypath`, discovers plugins) →
   `startup()` builds the UI. The renderer defaults to OpenGL, with a software
   fallback (see §3). Auto-loads a hardcoded default blueprint `"Omen-Navy-Class"`
   ([RenderFrame.java:141](../src/main/java/smc/smedit/ui/RenderFrame.java)).

**Path conventions** (`smc.smedit.util.Paths`): everything hangs off
`<starmade.home>/third-party/SMEdit/` (`Plugins/`, `Logs/`, `Cache/`,
`Settings/`, `resources/`, `Screenshots/`); config file is `~/.josm`. Note
`validateCurrentDirectory()` will **recursively scan the entire user home
directory** for a StarMade install (on first run, when `starmade.home` is unset)
— slow and surprising; an open Phase-0 follow-up.

> ⚠️ There are **two parallel, inconsistent install-discovery mechanisms**: the
> `smc.smedit.data.StarMade` singleton (`getBaseDir()`, validates on `StarMade.jar`)
> and `smc.smedit.util.Paths` (`~/.josm`, validates on `StarMade.jar` **and**
> `CrashAndBugReport.jar`). These should be unified.

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

Maintaining two renderers roughly doubles the rendering surface area
(consolidation is a later cleanup).

---

## 4. Plugin system (the primary modernization target)

- **Interfaces** (`smc.smedit.mods`): `IStarMadePlugin` (metadata, `newParameterBean()`,
  `getClassifications()` → untyped `int[][]` type/subtype pairs),
  `IBlocksPlugin` (adds `modify(SparseMatrix<Block>, params, StarMade, callback)`),
  `IStarMadePluginFactory` (`getPlugins()`).
- **Discovery** (`StarMadeLogic.discoverPlugins`): scans `<home>/Plugins/*.jar`,
  reads two comma-separated `MANIFEST.MF` attributes — `BlocksPlugins` and
  `PluginFactories` — loads those classes via `URLClassLoader`, instantiates
  via the deprecated `Class.newInstance()`. Not `ServiceLoader`, not
  annotations, not a hardcoded registry.
- **⚠️ The in-tree plugins are orphaned.** The 121 plugin + 18 factory files
  compile, but **nothing registers them** in this build — there is no
  `MANIFEST.MF` with `BlocksPlugins`/`PluginFactories`, and `build.gradle`
  declares only `Main-Class`. Historically they were compiled into a separate
  `JoFileMods.jar` **downloaded at runtime** and picked up by the manifest
  scanner. In the current single-module build they are dead weight unless that
  jar is fetched.

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
3. **Orphaned, stringly-typed plugin system** (see §4) — the central rework.
4. **~30k LOC of vendored `javax.vecmath`** (`smc.smedit.vecmath`, 85 files — the 7
   largest files in the repo). Replaceable wholesale by a JOML/vecmath Maven dep.
5. **Swing EDT violations.** Only 2 `invokeLater` in ~94k LOC;
   `RenderFrame.startup()` builds and `setVisible(true)`s the main frame off the
   EDT, alongside a separate LWJGL render thread. Latent UI/render races.
6. **Logging bypassed.** ~330 `System.out/err` prints + 73 `printStackTrace()`
   across 58 files despite the `smc.smedit.log` framework existing.
7. **~40 empty/silent catch blocks** across 17 files — systemic error
   swallowing, especially in config I/O (`Paths`, `OptionScreen`).
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
12. **No tests, no CI.** Zero JUnit; nothing beyond LWJGL on the classpath.

---

## 6. Zero test coverage / no CI

There is no test source set, no JUnit dependency, and no CI configuration. Given
the format-parsing core (bit-packing, chunk offsets), this is the highest-risk
gap for a modernization: format changes cannot be validated without a harness.

---

## 7. Build & run

- `build.gradle`: `java` + `application` plugins, Java 21 toolchain, group
  `smc.smedit`, version `3.0.0`. **Dependencies** = `org.lwjgl.lwjgl:lwjgl:2.9.3`
  + `:lwjgl_util:2.9.3` from Maven Central (Java classes only; the LWJGL natives
  are loaded at runtime from the StarMade install — see §3).
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
  software fallback). `./gradlew run` works on the Java 21 toolchain.
- ⏳ Follow-up: first-run StarMade-dir discovery still recursively scans `$HOME`
  and requires both `StarMade.jar` **and** `CrashAndBugReport.jar` (the latter
  may be gone in modern StarMade) — tighten this next.

**Phase 1 — dependencies & structure**
- ✅ LWJGL: vendored 2.9.1 replaced by 2.9.3 from Maven Central + install natives
  (done with the Phase-0 OpenGL work). A future LWJGL 3 migration (GLFW
  window/input, affecting `JGLCanvas`/`DrawLogic`/`NodeDrawHandler`) is optional.
- Replace vendored `smc.smedit.vecmath` with JOML (or `org.jogamp.vecmath`);
  delete ~30k LOC. (Do this behind a thin adapter to limit blast radius.)
- Delete dead code (`SparseMatrix{New,Old}`, `*Action1` dupes, commented blocks,
  the unused `Update`/`getDownloadCaches` remnants); move `ent.cmd` tools out of
  the GUI jar.

**Phase 2 — plugin system rework**
- Compile plugins into the app (or discover on the module path) instead of the
  download-a-jar model; migrate manifest-attribute discovery to `ServiceLoader`
  or annotations; replace `int[][]` classifications with enums; decouple from
  concrete `SparseMatrix<Block>`.

**Phase 3 — correctness & format** (see [STARMADE_COMPATIBILITY.md](STARMADE_COMPATIBILITY.md))
- Support the current `.smd3` / 32³ / v6 segment format and the corrected
  per-block bit layout; drive block metadata from `BlockConfig.xml` at runtime.

**Phase 4 — quality & UX**
- Add a JUnit test set (start with round-trip format tests) + CI.
- Route logging through `smc.smedit.log`; fix EDT usage; fix silent catches.
- README items: FlatLaf, improved camera/2D modes, cross-sections, workspace
  layouts, scripting.
