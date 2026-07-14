# Opening a blueprint

This guide loads an existing StarMade blueprint and views its blocks.

## 1. Start SMEdit

Launch SMEdit and confirm your StarMade folder on the options screen (see
[Getting Started](../getting-started.md)), then click **Start SMEdit**.

## 2. Open a blueprint

Use **File → Open** and choose a blueprint. SMEdit understands:

- A **blueprint folder** — the directory containing `header.smbph`,
  `logic.smbpl`, `meta.smbpm`, and a `DATA/` folder of `.smd3` region files.
- A single region file (`.smd2` / `.smd3`) or an exported `.sment` archive.

<!-- SCREENSHOT: File > Open dialog with a blueprint selected -->
![Open dialog](../images/open-blueprint.png)

## 3. View and navigate

The ship's blocks render in the main view. Drag to rotate the camera; use the
mouse wheel to zoom.

<!-- SCREENSHOT: a loaded ship in the 3D view -->
![Loaded ship](../images/loaded-ship.png)

!!! info "Block colors"
    SMEdit derives each block's color by **averaging its actual StarMade
    texture** (resolved via `data/config/BlockConfig.xml` and the `Default`
    texture pack). The sampled colors are cached to disk
    (`third-party/SMEdit/Cache/block-colors.properties`) and rebuilt
    automatically when your StarMade install changes. If colors look off for a
    newly-loaded ship, make sure your StarMade folder is set correctly on the
    options screen; deleting the cache file forces a rebuild.

## 4. Save

**File → Save** writes the blueprint back in the current StarMade format
(`.smd3` block data plus v5 `header`/`meta` and v0 `logic`), so the game can
load it.

!!! info "Logic connections"
    When you open a blueprint and save it back **unedited**, SMEdit now preserves
    the ship's control map (weapon-computer→module wiring, logic links) exactly —
    verified byte-for-byte against real StarMade saves. If you edit the blocks
    (import, plugin transforms, a new ship), it falls back to a valid but **empty**
    logic map for safety, so systems load unlinked. A minimal `meta` is still
    written. See the [compatibility notes](../STARMADE_COMPATIBILITY.md).
