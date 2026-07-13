# SMEdit3 ↔ StarMade Format Compatibility & Drift

> Status: analysis snapshot, 2026-07. Compares SMEdit3's frozen format
> understanding (circa StarMade ~v0.14, mid-2014) against a current StarMade
> source tree. Companion doc: [ARCHITECTURE.md](ARCHITECTURE.md).
>
> Line citations of the form `File.java:NN` under `src/main/java/` are SMEdit;
> citations prefixed `SM:` are the StarMade source that was compared against.

## TL;DR

SMEdit can still read a modern blueprint's **block placement and block IDs**
(the ID field width is unchanged), but it will **fail to locate/parse current
region files**, and it **corrupts hit-points, orientation, shapes, docking, and
metadata**. The fixes fall into five buckets, none individually huge:

1. Region container: `.smd2`/16³ → **`.smd3`/32³, serializer v6**.
2. Per-block bit layout above the ID changed (HP/active/orientation).
3. Block **shapes** moved from separate IDs → a `BlockStyle` config property.
4. Block **metadata is fully data-driven** from `BlockConfig.xml`; SMEdit's
   hardcoded table is partly stale and mostly incomplete (200 vs 1824 IDs).
5. **Docking** is now rail-based (in `meta.smbpm`); SMEdit drops meta on save.

The good news: the block **ID field is still 11 bits (≤2047)** and current
vanilla IDs top out at **1824**, so IDs need re-mapping in *meaning*, not
*width*. And SMEdit already loads `BlockConfig.xml`/`BlockTypes.properties` from
the install at runtime — that mechanism is the right hook to build the fix on.

---

## 1. Blueprint folder layout

A blueprint is a directory. The **filenames are unchanged**, but internal
versions advanced and one new file exists:

| File | SMEdit expects | Modern StarMade | Note |
|---|---|---|---|
| `header.smbph` | bounds + block manifest | header **dataVersion 5** | still bounds + counts, wider |
| `logic.smbpl` | controller→group map | **structureVersion 0** | control-element map |
| `meta.smbpm` | docks + native NBT tree | **metaVersion 5** | rails/AI/cargo/wireless |
| `DATA/<uid>.<x>.<y>.<z>.smd2` | 16³ region | **`.smd3`, 32³** | **extension + size changed** |
| `modmappings.smbmm` | — (absent) | **new** | mod namespace → block-id map |

SMEdit locates blueprint dirs by the presence of `header.smbph`
([BlueprintLogic.java:80](../src/main/java/smc/smedit/logic/BlueprintLogic.java)) —
that check still works. It then reads `DATA/*.smd2`, which **no longer exists**
in current blueprints (they are `.smd3`). Confirmed: the default Isanth
blueprints in the current tree ship `DATA/*.smd3`.

---

## 2. Region / segment format (`.smd2` → `.smd3`)

| Aspect | SMEdit (`DataLogic`) | Modern StarMade | Consequence |
|---|---|---|---|
| Extension | `.smd2` | `.smd3` | files not found |
| Segment edge | **16** (`Block[16][16][16]`, `DataLogic.java:156`) | **32** (`Segment.DIM_BITS=5`) | every stride/offset wrong |
| Blocks/segment | 4096 | **32768** | — |
| Serializer version | (implicit, pre-v2 layout) | **v6** (`SegmentDataIntArray`) | header mismatch |
| Per-block size | 3 bytes | 3 bytes | unchanged |
| Compression | zlib Deflate per chunk record | zlib Deflate per segment, int size-prefix | compatible in spirit, different framing |

SMEdit's `.smd2` reader uses an offset/size table `int[16][16][16][2]`, a
timestamp table `long[16][16][16]`, and fixed **5120-byte** chunk records
(`DataLogic.java:120,133,138`). The modern format changes the segment
dimension (16→32) and the serialization version, so the record framing and the
whole super-chunk stride math are wrong for `.smd3`.

**SMEdit super-chunk math to revisit:** `ShipLogic.getSuperChunkOriginFromIndex`
assumes a 256-voxel super-chunk stride (16 chunks × 16). With 32³ segments the
stride and negative-axis handling both change.

---

## 3. Per-block bit layout (the 3-byte value)

Both versions pack each block into 24 bits, **but partition the bits above the
ID differently.** SMEdit's ID extraction still works; everything above it does
not.

| Field | SMEdit (`DataLogic.java:164-169`) | Modern v6 (`SM: SegmentData`) |
|---|---|---|
| block ID | bits **0–10** (`& 0x7ff`, ≤2047) | bits **0–10** (`& 0x7FF`, ≤2047) ✅ same |
| hit points | bits **11–19** (9 bits) | bits **11–17** (7 bits) |
| active | bit **20** | bit **18** |
| orientation | bits **21–23** + bit 20 (4 bits, shares "active") | bits **19–23** (5 bits, up to 24 orientations) |

Notes:
- SMEdit's own layout matches **neither** the current v6 layout **nor** the
  legacy v2 `SegmentData3Byte` (8 HP / 1 active / 4 orient) — it froze on an
  even older split and has a quirk where orientation bit 3 and the "active" bit
  are the same bit (`DataLogic.java:167-169,293`).
- **Because the ID field is unchanged**, SMEdit reading a modern block gets the
  **correct block ID** but a scrambled HP/orientation/active. On write it would
  emit HP/orientation in the wrong bit positions → corrupt geometry.
- Orientation widened to **5 bits** to support 24-orientation shapes
  (CORNER/NORMAL24). SMEdit's 3–4 bit orientation and its hardcoded
  `CornerLogic`(8-value)/`WedgeLogic`(16-value) permutation tables can't express
  the new range.

---

## 4. Block shapes: separate IDs → `BlockStyle` property

This is the deepest model change.

- **SMEdit**: wedge/corner/hepta/tetra/penta and every hull color are
  **separate hardcoded block IDs**, grouped by the 10×9 `HULL_COLOR_MAP` table
  ([BlockTypes.java:386](../src/main/java/smc/smedit/data/BlockTypes.java)); shape
  predicates (`isHull/isWedge/isCorner/...`) and smoothing
  (`SmoothLogic`/`HullLogic`) are pure lookups into that table.
- **Modern StarMade**: shape is a per-block **`BlockStyle`** property
  (`NORMAL, WEDGE, CORNER, SPRITE, TETRA, HEPTA, NORMAL24`) read from a
  `<StyleId>` node in `BlockConfig.xml`; one logical block ID carries its shape
  via style + the 5-bit orientation.

Consequence: SMEdit's entire hardcoded shape/color ID table and the
smoothing/hull logic built on it are obsolete. A modern SMEdit must read
`BlockStyle` from config and treat shape as orientation-space, not identity.

---

## 5. Block ID meanings — partly drifted, mostly incomplete

SMEdit hardcodes ~200–330 `short` constants in `BlockTypes.java`. Modern
StarMade defines **1824 vanilla IDs** (plus mod IDs 1825–2047) in
`BlockConfig.xml` + `BlockTypes.properties`. Spot-check against the current
`BlockTypes.properties`:

| ID | SMEdit constant | Modern name | Verdict |
|---:|---|---|---|
| 1 | `CORE_ID` | `SHIP_CORE` | ✅ stable |
| 2 | `POWER_ID` | `POWER_REACTOR` | ✅ stable |
| 3 | `SHIELD_ID` | `SHIELD_CAPACITOR` | ✅ stable |
| 5 | `HULL_COLOR_GREY_ID` | `GREY_STANDARD_ARMOR` | ✅ stable |
| 8 | `THRUSTER_ID` | `THRUSTER_MODULE` | ✅ stable |
| 15 | `RADAR_JAMMING_ID` | `RADARJAMMER` | ✅ stable |
| 63 | `GLASS_ID` | `GLASS` | ✅ stable |
| **6** | `WEAPON_CONTROLLER_ID` | `CANNON_COMPUTER` | ⚠️ meaning shifted |
| **16** | `WEAPON_ID` | `CANNON_BARREL` | ⚠️ meaning shifted |
| **7** | `DOCK_ID` | `TURRET_DOCKING_UNIT` | ⚠️ docking reworked |
| **38** | `MISSILE_DUMB_CONTROLLER_ID` | `MISSILE_COMPUTER` | ⚠️ meaning shifted |
| **46** | `MISSILE_HEAT_CONTROLLER_ID` | `SHIELD_DRAIN_COMPUTER` | ❌ wrong block |
| **47** | `COCKPIT_ID` | `CAMERA` | ❌ wrong block |
| **65** | `DEATHSTAR_CORE_ID` | `PLACEHOLDER` | ❌ removed |

So it is **not** uniformly stale — many core IDs held, but weapons/missiles/
docking/camera drifted, some point at unrelated blocks, and **everything above
the old ~200 range (≈1600 blocks) is simply absent** from SMEdit's table.

**What SMEdit already does right:** `BlockTypeColors.loadBlockIcons`
([BlockTypeColors.java:687](../src/main/java/smc/smedit/ui/BlockTypeColors.java))
loads `data/config/BlockTypes.properties` + `BlockConfig.xml` from the install
at runtime, populates `BLOCK_NAMES`/`BLOCK_HITPOINTS`/`BLOCK_TEXTURE_IDS`, and
even **reflectively overwrites** the hardcoded `BlockTypes` constants
(`:716-719`) for any constant whose name matches a modern type. That runtime
load is the correct foundation — but it only helps for name-matched constants
and doesn't cover shapes, orientation, or the ~1600 unknown IDs.

---

## 6. Metadata, docking, logic

- **Docking**: SMEdit models the old docking-beam system (`DockEntry`:
  subfolder path + position + block ID, `MetaLogic.java:61-73`). Modern StarMade
  uses **rail docking**, stored in `meta.smbpm` at metaVersion 5. SMEdit's dock
  parsing won't match.
- **Lossy save**: SMEdit's `MetaLogic.make` always writes `unknown2=1`
  ([MetaLogic.java:122](../src/main/java/smc/smedit/ship/logic/MetaLogic.java)) —
  it **drops docks and the entire NBT tag tree on its own save**, even for data
  it read successfully. Any modern metadata (AI, cargo, wireless, rails) would
  be lost on round-trip.
- **Hit points on save** are not the block's real value — they come from
  `BLOCK_HITPOINTS` or default `100` (`Block.java:58-64`), and `Block` stores
  only ID + orientation (`setActive`/`setHitPoints` are no-ops).
- **`modmappings.smbmm`** (mod block-id translation) is unread — mod blocks
  (IDs 1825–2047) won't resolve to correct types without it.

---

## 7. Remediation checklist

Prioritized. Each is scoped to specific SMEdit files.

**Must-fix to read a current blueprint at all**
- [ ] Add `.smd3` support: 32³ segments, serializer v6 record framing, per-segment
      Deflate with int size-prefix. — `DataLogic`, `ShipLogic` (super-chunk stride),
      `Blueprint`/`Data` model.
- [ ] Correct the per-block bit layout (HP 11–17, active 18, orientation 19–23,
      5-bit). — `DataLogic.java:164-169` (read), `:291-294` (write), `Chunk.java`
      field-layout comments.
- [ ] Keep reading legacy `.smd2` for backward compatibility (migration path).

**Must-fix to render/edit correctly**
- [ ] Read `BlockStyle` from `BlockConfig.xml` and treat shape as a property; stop
      relying on the hardcoded shape-ID tables. — `BlockTypeColors`, `BlockTypes`,
      `SmoothLogic`, `HullLogic`, `WedgeLogic`, `CornerLogic`.
- [ ] Support 24-orientation space for corners/NORMAL24. — orientation logic.
- [ ] Make the runtime `BlockConfig.xml` load authoritative for names, HP,
      texture, shape, and controller relationships; treat the hardcoded
      `BlockTypes` constants as fallback only (or delete them). — `BlockTypeColors`,
      `BlockTypes`.

**Must-fix to avoid data loss**
- [ ] Stop downgrading meta on save (`MetaLogic.make` `unknown2=1`); round-trip
      docks + the tag tree, or at minimum preserve unread bytes verbatim.
- [ ] Persist real per-block hit points/active instead of the lookup default.
- [ ] Read/preserve `modmappings.smbmm` so mod blocks survive a round-trip.

**Should-fix**
- [ ] Model rail docking (metaVersion 5) instead of docking beams.
- [ ] Update importer/exporter block mappings (`schematic_map.xml`,
      `color_map.xml`) against current IDs.
- [ ] Add round-trip format tests using the current default blueprints (Isanth)
      as fixtures — this is the safety net for all of the above.

**Verification fixtures available in the StarMade tree**
- `blueprints-default/Isanth Type-PNR-25-{B,C,M}/` — full modern ships with
  `header.smbph` v5, `meta.smbpm` v5, `DATA/*.smd3`.
- `data/config/BlockConfig.xml` (7.2 MB) + `BlockTypes.properties` (1653 entries)
  — the authoritative block table to load at runtime.
