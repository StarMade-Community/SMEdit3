/**
 * Copyright 2014
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package smc.smedit.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;

/**
 * Runtime-derived block groupings — the modern replacement for the hand-maintained
 * constants and tables that used to live in {@code BlockTypes}.
 *
 * <p>Nothing here is hardcoded to a specific StarMade version. Everything is
 * derived from the live install's data, loaded once by
 * {@link BlockTypeColors#loadBlockIcons()}:
 * <ul>
 *   <li><b>Controller links</b> ({@link #CONTROLLING}) — from {@code BlockConfig.xml}
 *       {@code <Controlling>} elements: a computer/controller block to the module(s)
 *       it drives.</li>
 *   <li><b>Hull / armour families</b> — derived from the regular
 *       {@code {COLOR}_{TIER}[_{SHAPE}]} naming in {@code BlockTypes.properties}, so
 *       recolour / tier-swap work across every colour, tier and shape the install
 *       ships (no frozen colour table).</li>
 *   <li><b>Shape classification</b> — read from {@code BlockConfig.xml}
 *       {@code <BlockStyle>} (via {@link BlockTypeColors#BLOCK_STYLE}), covering every
 *       shaped block (armour, glass, decorative …), not just standard-colour hull.</li>
 *   <li><b>Display names</b> ({@link #BLOCK_NAMES}).</li>
 * </ul>
 *
 * <p>Concrete block references elsewhere use the generated {@link Blocks} enum
 * (e.g. {@code Blocks.SHIP_CORE.getId()}); this class covers the <em>groupings</em>
 * over blocks.
 */
public final class BlockGroups {

    private BlockGroups() {
    }

    // ---- SMEdit-internal render sentinels (NOT StarMade blocks) --------------
    // Face-selection highlight markers drawn by the render panels; kept well above
    // the real block-id range so they never collide with an install's blocks.
    public static final short SPECIAL = 8350;
    public static final short SPECIAL_SELECT_XP = SPECIAL + 1;
    public static final short SPECIAL_SELECT_XM = SPECIAL + 2;
    public static final short SPECIAL_SELECT_YP = SPECIAL + 3;
    public static final short SPECIAL_SELECT_YM = SPECIAL + 4;
    public static final short SPECIAL_SELECT_ZP = SPECIAL + 5;
    public static final short SPECIAL_SELECT_ZM = SPECIAL + 6;

    // ---- Config-derived data (populated by BlockTypeColors.loadBlockIcons) ---
    /** Block id → human display name (e.g. "Cannon Computer"). */
    public static final Map<Short, String> BLOCK_NAMES = new HashMap<>();
    /** Controller/computer block id → the module block id(s) it controls. */
    public static final Map<Short, short[]> CONTROLLING = new HashMap<>();
    /** Block ids that are directly controlled by the ship core (top-level systems). */
    public static final Set<Short> CONTROLLED_BY_CORE = new HashSet<>();

    // ---- Hull / armor family index (built lazily from block names) ----------
    // Tiers and shape suffixes are structural naming conventions (they do not drift
    // between versions the way numeric ids do); the color set is discovered from
    // the data. A family key is (colourIndex, tierIndex, shapeIndex).
    private static final String[] TIERS = {"HULL", "STANDARD_ARMOR", "ADVANCED_ARMOR", /*"CRYSTAL_ARMOR"*/}; //Crystal armor should be grouped with glass instead
    private static final int TIER_STANDARD = 1;
    private static final int TIER_ADVANCED = 2;
    // shape index 0 = plain cube; suffixes for the rest.
    private static final String[] SHAPE_SUFFIX = {
        "", "_WEDGE", "_CORNER", "_HEPTA", "_TETRA",
        "_QUARTER_SLAB", "_HALF_SLAB", "_THREE_QUARTER_SLAB"
    };
    private static final int SHAPE_CUBE = 0;

    private static boolean armorBuilt;
    private static String[] colors = new String[0];
    private static final Map<Long, Short> FAMILY_TO_ID = new HashMap<>();
    private static final Map<Short, int[]> ID_TO_FAMILY = new HashMap<>();

    private static long famKey(int color, int tier, int shape) {
        return ((long) color << 16) | ((long) tier << 8) | shape;
    }

    /** Ensures the config is loaded and the hull-family index is built (idempotent). */
    private static void ensureLoaded() {
        BlockTypeColors.loadBlockIcons();
        if (armorBuilt) {
            return;
        }
        buildArmorIndex();
    }

    private static synchronized void buildArmorIndex() {
        if (armorBuilt) {
            return;
        }
        Properties names = BlockTypeColors.mBlockTypes;
        if (names == null) {
            return; // no install loaded yet; try again on next call
        }
        // Discover the colour set: any prefix that has a "<COLOR>_STANDARD_ARMOR" block.
        java.util.List<String> found = new java.util.ArrayList<>();
        for (Object key : names.keySet()) {
            String k = (String) key;
            if (k.endsWith("_STANDARD_ARMOR")) {
                found.add(k.substring(0, k.length() - "_STANDARD_ARMOR".length()));
            }
        }
        java.util.Collections.sort(found);
        colors = found.toArray(new String[0]);

        for (int ci = 0; ci < colors.length; ci++) {
            for (int ti = 0; ti < TIERS.length; ti++) {
                for (int si = 0; si < SHAPE_SUFFIX.length; si++) {
                    String prop = colors[ci] + "_" + TIERS[ti] + SHAPE_SUFFIX[si];
                    String idStr = names.getProperty(prop);
                    if (idStr == null) {
                        continue;
                    }
                    short id;
                    try {
                        id = Short.parseShort(idStr.trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    FAMILY_TO_ID.put(famKey(ci, ti, si), id);
                    ID_TO_FAMILY.put(id, new int[]{ci, ti, si});
                }
            }
        }
        armorBuilt = true;
    }

    // ---- Shape classification (config-driven, spans all tiers & materials) ---

    private static int styleOf(short id) {
        ensureLoaded();
        Integer s = BlockTypeColors.BLOCK_STYLE.get(id);
        return s == null ? BlockTypeColors.STYLE_NORMAL : s;
    }

    public static boolean isWedge(short id) {
        return styleOf(id) == BlockTypeColors.STYLE_WEDGE;
    }

    public static boolean isCorner(short id) {
        return styleOf(id) == BlockTypeColors.STYLE_CORNER;
    }

    /** A "hepta" block (a cube with a tetra corner cut off; formerly called "penta"). */
    public static boolean isHepta(short id) {
        return styleOf(id) == BlockTypeColors.STYLE_HEPTA;
    }

    /** @deprecated legacy name for {@link #isHepta(short)}. */
    @Deprecated
    public static boolean isPenta(short id) {
        return isHepta(id);
    }

    public static boolean isTetra(short id) {
        return styleOf(id) == BlockTypeColors.STYLE_TETRA;
    }

    // ---- Hull / armour queries ----------------------------------------------

    /** True if {@code id} is any hull/armour family block (any colour, tier or shape). */
    public static boolean isArmor(short id) {
        ensureLoaded();
        return ID_TO_FAMILY.containsKey(id);
    }

    /** Legacy alias for {@link #isArmor(short)}. */
    public static boolean isAnyHull(short id) {
        return isArmor(id);
    }

    /** True if {@code id} is a full-cube armour block (any tier). */
    public static boolean isHull(short id) {
        ensureLoaded();
        int[] f = ID_TO_FAMILY.get(id);
        return f != null && f[2] == SHAPE_CUBE;
    }

    // "Power*" predicates: an advanced-armour block of the given shape. Retained for
    // call-site compatibility; the plain shape predicates already span all tiers.
    private static boolean isTierShape(short id, int tier, int shape) {
        ensureLoaded();
        int[] f = ID_TO_FAMILY.get(id);
        return f != null && f[1] == tier && f[2] == shape;
    }

    public static boolean isPowerHull(short id) {
        return isTierShape(id, TIER_ADVANCED, SHAPE_CUBE);
    }

    public static boolean isPowerWedge(short id) {
        return isTierShape(id, TIER_ADVANCED, 1);
    }

    public static boolean isPowerCorner(short id) {
        return isTierShape(id, TIER_ADVANCED, 2);
    }

    public static boolean isPowerPenta(short id) {
        return isTierShape(id, TIER_ADVANCED, 3);
    }

    public static boolean isPowerTetra(short id) {
        return isTierShape(id, TIER_ADVANCED, 4);
    }

    public static boolean isAnyWedge(short id) {
        return isWedge(id);
    }

    public static boolean isAnyCorner(short id) {
        return isCorner(id);
    }

    public static boolean isAnyPenta(short id) {
        return isHepta(id);
    }

    public static boolean isAnyTetra(short id) {
        return isTetra(id);
    }

    /**
     * The canonical "colour" representative for {@code id}'s colour — the
     * standard-armour cube of the same colour — or {@code -1} if {@code id} is not an
     * armour block. Mirrors the old {@code getColor}.
     */
    public static short getColor(short id) {
        ensureLoaded();
        int[] f = ID_TO_FAMILY.get(id);
        if (f == null) {
            return -1;
        }
        Short rep = FAMILY_TO_ID.get(famKey(f[0], TIER_STANDARD, SHAPE_CUBE));
        return rep == null ? -1 : rep;
    }

    /**
     * Recolours {@code blockID} to the colour of {@code colorID} (a colour
     * representative from {@link #getColor}), preserving its tier and shape. Returns
     * {@code blockID} unchanged if it is not an armour block or the target colour is
     * unknown. Mirrors the old {@code getColoredBlock}.
     */
    public static short getColoredBlock(short blockID, short colorID) {
        ensureLoaded();
        int[] bf = ID_TO_FAMILY.get(blockID);
        int[] cf = ID_TO_FAMILY.get(colorID);
        if (bf == null || cf == null) {
            return blockID;
        }
        Short out = FAMILY_TO_ID.get(famKey(cf[0], bf[1], bf[2]));
        return out == null ? blockID : out;
    }

    /** The advanced-armour equivalent of a standard-armour block, or {@code -1}. */
    public static short getPoweredBlock(short blockID) {
        ensureLoaded();
        int[] f = ID_TO_FAMILY.get(blockID);
        if (f == null || f[1] != TIER_STANDARD) {
            return -1;
        }
        Short out = FAMILY_TO_ID.get(famKey(f[0], TIER_ADVANCED, f[2]));
        return out == null ? -1 : out;
    }

    /** The standard-armour equivalent of an advanced-armour block, or {@code -1}. */
    public static short getUnPoweredBlock(short blockID) {
        ensureLoaded();
        int[] f = ID_TO_FAMILY.get(blockID);
        if (f == null || f[1] != TIER_ADVANCED) {
            return -1;
        }
        Short out = FAMILY_TO_ID.get(famKey(f[0], TIER_STANDARD, f[2]));
        return out == null ? -1 : out;
    }

    /**
     * Returns a copy of {@code oldBlock} recoloured to {@code color} (a colour
     * representative), preserving orientation; unchanged if not an armour block.
     */
    public static Block colorize(Block oldBlock, short color) {
        if (!isArmor(oldBlock.getBlockID())) {
            return oldBlock;
        }
        short newID = getColoredBlock(oldBlock.getBlockID(), getColor(color));
        Block newBlock = new Block(newID);
        newBlock.setOrientation(oldBlock.getOrientation());
        return newBlock;
    }

    // ---- Controller queries --------------------------------------------------

    /** True if {@code id} is a controller/computer that drives module blocks. */
    public static boolean isController(short id) {
        ensureLoaded();
        return CONTROLLING.containsKey(id);
    }

    /**
     * The primary module block id that {@code controllerID} controls, or {@code -1}.
     * Mirrors the old {@code CONTROLLER_IDS.get(...)}.
     */
    public static short controlledBlock(short controllerID) {
        ensureLoaded();
        short[] c = CONTROLLING.get(controllerID);
        return (c == null || c.length == 0) ? -1 : c[0];
    }
}
