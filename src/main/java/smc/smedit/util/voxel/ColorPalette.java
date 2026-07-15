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
package smc.smedit.util.voxel;

import java.awt.Color;
import java.util.List;

import smc.smedit.ui.BlockTypeColors;

/**
 * The set of StarMade blocks a mesh import is allowed to place, plus the
 * nearest-color matching used to pick one for each source color.
 *
 * <p>Each candidate block's match color is its representative fill color
 * ({@link BlockTypeColors#getFillColor}), which is sampled once from the
 * installed StarMade textures — so this palette reflects whatever blocks the
 * configured install actually has, with no hardcoded colors. Matching is a
 * plain nearest-neighbour search in RGB space (the same squared-distance metric
 * the legacy importer used, inlined here to keep this a leaf class).
 *
 * @author SMEdit3
 **/
public final class ColorPalette {

    private final short[] ids;
    private final int[] reds;
    private final int[] greens;
    private final int[] blues;

    private ColorPalette(short[] ids, int[] reds, int[] greens, int[] blues) {
        this.ids = ids;
        this.reds = reds;
        this.greens = greens;
        this.blues = blues;
    }

    /**
     * Builds a palette from a list of block ids, resolving each id's match color
     * from {@link BlockTypeColors#getFillColor}. Duplicate ids are collapsed.
     */
    public static ColorPalette fromBlockIds(List<Short> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return new ColorPalette(new short[0], new int[0], new int[0], new int[0]);
        }
        int n = blockIds.size();
        short[] ids = new short[n];
        int[] reds = new int[n];
        int[] greens = new int[n];
        int[] blues = new int[n];
        int count = 0;
        for (Short raw : blockIds) {
            if (raw == null) {
                continue;
            }
            short id = raw;
            boolean dup = false;
            for (int j = 0; j < count; j++) {
                if (ids[j] == id) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            Color c = BlockTypeColors.getFillColor(id);
            ids[count] = id;
            reds[count] = c.getRed();
            greens[count] = c.getGreen();
            blues[count] = c.getBlue();
            count++;
        }
        if (count == n) {
            return new ColorPalette(ids, reds, greens, blues);
        }
        // Trim the arrays if duplicates/nulls were skipped.
        return new ColorPalette(
                java.util.Arrays.copyOf(ids, count),
                java.util.Arrays.copyOf(reds, count),
                java.util.Arrays.copyOf(greens, count),
                java.util.Arrays.copyOf(blues, count));
    }

    public boolean isEmpty() {
        return ids.length == 0;
    }

    public int size() {
        return ids.length;
    }

    /**
     * Returns the palette block whose color is closest to {@code rgb} (0xRRGGBB),
     * or the {@code fallback} id when the palette is empty.
     */
    public short nearest(int rgb, short fallback) {
        if (ids.length == 0) {
            return fallback;
        }
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < ids.length; i++) {
            int dr = r - reds[i];
            int dg = g - greens[i];
            int db = b - blues[i];
            long dist = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return ids[best];
    }
}
