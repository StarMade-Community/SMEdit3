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
package smc.smedit.plugins.ship.imp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user-chosen set of StarMade blocks a model import may place, in order.
 * Presets are computed from the live {@link HullCatalog} (never hardcoded), and
 * the list serializes to a simple CSV of block ids so it persists with the rest
 * of the plugin's parameters between runs.
 *
 * @author SMEdit3
 **/
public final class ConversionPalette {

    private final List<Short> blockIds;

    public ConversionPalette(List<Short> blockIds) {
        List<Short> copy = new ArrayList<>();
        if (blockIds != null) {
            for (Short id : blockIds) {
                if (id != null && id > 0 && !copy.contains(id)) {
                    copy.add(id);
                }
            }
        }
        this.blockIds = Collections.unmodifiableList(copy);
    }

    /** The candidate block ids, in order. */
    public List<Short> getBlockIds() {
        return blockIds;
    }

    public boolean isEmpty() {
        return blockIds.isEmpty();
    }

    public int size() {
        return blockIds.size();
    }

    /** Serializes to "id,id,id" for parameter persistence. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Short id : blockIds) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** Parses "id,id,id" back into a palette (ignoring unparseable entries). */
    public static ConversionPalette fromText(String text) {
        List<Short> ids = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(",")) {
                part = part.trim();
                if (part.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Short.parseShort(part));
                } catch (NumberFormatException ignore) {
                    // skip garbage
                }
            }
        }
        return new ConversionPalette(ids);
    }

    @Override
    public String toString() {
        return toText();
    }

    // ---- presets, all computed from the live install (cube shapes only) ----

    public static ConversionPalette empty() {
        return new ConversionPalette(new ArrayList<>());
    }

    public static ConversionPalette basicArmor() {
        return new ConversionPalette(HullCatalog.blocks(HullCatalog.Tier.BASIC, true));
    }

    public static ConversionPalette standardArmor() {
        return new ConversionPalette(HullCatalog.blocks(HullCatalog.Tier.STANDARD, true));
    }

    public static ConversionPalette advancedArmor() {
        return new ConversionPalette(HullCatalog.blocks(HullCatalog.Tier.ADVANCED, true));
    }

    public static ConversionPalette crystalArmor() {
        return new ConversionPalette(HullCatalog.blocks(HullCatalog.Tier.CRYSTAL, true));
    }

    public static ConversionPalette hazardArmor() {
        return new ConversionPalette(HullCatalog.blocks(HullCatalog.Tier.HAZARD, true));
    }

    public static ConversionPalette allArmor() {
        return new ConversionPalette(HullCatalog.allBlocks(true));
    }

    /**
     * The palette selected by default: Standard Armor in every color the install
     * has, falling back to any armor, then empty (single-color import) when no
     * StarMade install is configured.
     */
    public static ConversionPalette defaultPalette() {
        ConversionPalette p = standardArmor();
        if (p.isEmpty()) {
            p = allArmor();
        }
        return p;
    }
}
