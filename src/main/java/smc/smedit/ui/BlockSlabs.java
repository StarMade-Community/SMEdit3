/**
 * Copyright 2014 SMEdit https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
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
 */
package smc.smedit.ui;

import java.util.Map;

/**
 * Slab-variant helpers. A full block (e.g. Grey Basic Armor) has three partial-
 * height slab variants — ¾, ½, ¼ — listed in its BlockConfig {@code <SlabIds>}
 * ({@link BlockTypeColors#BLOCK_SLAB_IDS}); each variant's own {@code <Slab>}
 * level ({@link BlockTypeColors#getBlockSlab}) is 1=¾, 2=½, 3=¼.
 *
 * <p>Like {@link BlockShapes}, this lets the brush palette show only the full
 * block and offer the slab as a control, instead of a swatch per slab. Slabs are
 * a form of the <em>cube</em>, so they are mutually exclusive with the wedge/
 * corner/… shapes.
 */
public final class BlockSlabs {

    /** Slab levels offered, coarsest first (¾, ½, ¼); 0 = full block (no slab). */
    public static final int LEVEL_THREE_QUARTER = 1;
    public static final int LEVEL_HALF = 2;
    public static final int LEVEL_QUARTER = 3;
    public static final int[] LEVELS = {LEVEL_THREE_QUARTER, LEVEL_HALF, LEVEL_QUARTER};

    private BlockSlabs() {
    }

    /** Whether {@code fullId} has slab variants. */
    public static boolean hasSlabs(short fullId) {
        short[] a = BlockTypeColors.BLOCK_SLAB_IDS.get(fullId);
        return a != null && a.length > 0;
    }

    /** The slab level of {@code id} (0 = full block). */
    public static int levelOf(short id) {
        return BlockTypeColors.getBlockSlab(id);
    }

    /** True if {@code id} is itself a slab variant. */
    public static boolean isSlab(short id) {
        return BlockTypeColors.getBlockSlab(id) > 0;
    }

    /** The slab variant of {@code fullId} at {@code level}, or {@code fullId} for level 0 / none. */
    public static short slabVariant(short fullId, int level) {
        if (level <= 0) {
            return fullId;
        }
        short[] a = BlockTypeColors.BLOCK_SLAB_IDS.get(fullId);
        if (a != null) {
            for (short s : a) {
                if (BlockTypeColors.getBlockSlab(s) == level) {
                    return s;
                }
            }
        }
        return fullId;
    }

    /** The full block a slab variant belongs to, or {@code slabId} if it isn't a slab. */
    public static short baseOf(short slabId) {
        if (BlockTypeColors.getBlockSlab(slabId) == 0) {
            return slabId;
        }
        for (Map.Entry<Short, short[]> e : BlockTypeColors.BLOCK_SLAB_IDS.entrySet()) {
            for (short s : e.getValue()) {
                if (s == slabId) {
                    return e.getKey();
                }
            }
        }
        return slabId;
    }

    /** A short label for a slab level (¾ / ½ / ¼). */
    public static String label(int level) {
        switch (level) {
            case LEVEL_THREE_QUARTER: return "¾";
            case LEVEL_HALF: return "½";
            case LEVEL_QUARTER: return "¼";
            default: return "Full";
        }
    }
}
