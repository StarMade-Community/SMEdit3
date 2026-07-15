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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shape-family helpers, driven by StarMade's BlockConfig ({@link
 * BlockTypeColors#BLOCK_SHAPE_BASE} / {@link BlockTypeColors#BLOCK_SHAPE_VARIANTS},
 * parsed from {@code <SourceReference>} + {@code <BlockStyle>}). A block that comes
 * in shapes has a base cube plus wedge / corner / tetra / hepta variants; this
 * covers <em>every</em> such block (crystal armor, hazard armor, …), not just the
 * standard colours in the hardcoded {@code HULL_COLOR_MAP}.
 *
 * <p>Lets the palette show one base cube per family and offer the shape as a
 * separate control, and lets Paint keep a wedge a wedge across any material.
 */
public final class BlockShapes {

    private BlockShapes() {
    }

    /** The styles a shape control offers, in display order (cube first). */
    public static final int[] STYLES = {
        BlockTypeColors.STYLE_NORMAL, BlockTypeColors.STYLE_WEDGE,
        BlockTypeColors.STYLE_CORNER, BlockTypeColors.STYLE_TETRA,
        BlockTypeColors.STYLE_HEPTA,
    };

    /** The base cube of {@code id}'s shape family, or {@code id} itself if it has none. */
    public static short baseCube(short id) {
        Short base = BlockTypeColors.BLOCK_SHAPE_BASE.get(id);
        return base != null ? base : id;
    }

    /** Whether {@code id}'s family offers shaped variants (more than just the cube). */
    public static boolean supportsShapes(short id) {
        Map<Integer, Short> variants = BlockTypeColors.BLOCK_SHAPE_VARIANTS.get(baseCube(id));
        return variants != null && variants.size() > 1;
    }

    /** True if {@code id} is a shaped variant (wedge/corner/…), i.e. not its family's base cube. */
    public static boolean isNonBaseVariant(short id) {
        Short base = BlockTypeColors.BLOCK_SHAPE_BASE.get(id);
        return base != null && base != id;
    }

    /** The {@code STYLE_*} of {@code id} (straight from BlockConfig). */
    public static int styleOf(short id) {
        return BlockTypeColors.getBlockStyle(id);
    }

    /** The styles this block's family actually has, in {@link #STYLES} order. */
    public static List<Integer> stylesOf(short id) {
        Map<Integer, Short> variants = BlockTypeColors.BLOCK_SHAPE_VARIANTS.get(baseCube(id));
        List<Integer> out = new ArrayList<>();
        if (variants == null) {
            out.add(BlockTypeColors.STYLE_NORMAL);
            return out;
        }
        for (int style : STYLES) {
            if (variants.containsKey(style)) {
                out.add(style);
            }
        }
        return out;
    }

    /**
     * The block id for {@code id}'s family in the given {@code STYLE_*}, or the
     * family's cube (or {@code id} unchanged for a non-family block).
     */
    public static short variant(short id, int style) {
        Map<Integer, Short> variants = BlockTypeColors.BLOCK_SHAPE_VARIANTS.get(baseCube(id));
        if (variants == null) {
            return id;
        }
        Short out = variants.get(style);
        if (out != null) {
            return out;
        }
        Short cube = variants.get(BlockTypeColors.STYLE_NORMAL);
        return cube != null ? cube : id;
    }
}
