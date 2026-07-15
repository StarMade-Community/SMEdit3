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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import smc.smedit.data.BlockTypes;

/**
 * Shared block-icon lookups, sourced from the live StarMade install: a block's
 * display name ({@link BlockTypes#BLOCK_NAMES}), its StarMade build icon
 * ({@link BlockTypeColors#getBuildIconImage}), and — as a fallback when no icon
 * is available — a swatch of its approximated fill colour
 * ({@link BlockTypeColors#getFillColor}).
 *
 * <p>This is the one place block icons are produced, so the block-palette editor,
 * the selection/view filters and the brush palette all render blocks identically.
 * Icons are cached by (block id, size), so repeated paints don't rescale.
 */
public final class BlockIcons {

    /** Scaled icons cached by (block id, size) so lists/strips/grids don't rescale each paint. */
    private static final Map<Long, Icon> ICON_CACHE = new HashMap<>();

    private BlockIcons() {
    }

    /** A block's approximated fill colour (grey when unknown). */
    public static Color color(Short id) {
        return id == null ? Color.GRAY : BlockTypeColors.getFillColor(id);
    }

    /** A block's display name, falling back to {@code "Block #id"}. */
    public static String name(Short id) {
        if (id == null) {
            return "";
        }
        String n = BlockTypes.BLOCK_NAMES.get(id);
        return n != null ? n : ("Block #" + id);
    }

    /**
     * A {@code size}×{@code size} icon for a block: its StarMade build icon when
     * available, otherwise a swatch of its approximated colour.
     */
    public static Icon icon(Short id, int size) {
        if (id == null) {
            return new SwatchIcon(Color.GRAY, size);
        }
        long key = ((long) id << 20) | (size & 0xfffff);
        Icon cached = ICON_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Icon icon;
        BufferedImage bi = BlockTypeColors.getBuildIconImage(id);
        if (bi != null) {
            Image scaled = bi.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaled);
        } else {
            Color c = color(id);
            icon = new SwatchIcon(c != null ? c : Color.GRAY, size);
        }
        ICON_CACHE.put(key, icon);
        return icon;
    }

    /** A small filled square, used when a block has no build icon. */
    public static final class SwatchIcon implements Icon {
        private final Color color;
        private final int size;

        public SwatchIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, size, size);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, size, size);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
