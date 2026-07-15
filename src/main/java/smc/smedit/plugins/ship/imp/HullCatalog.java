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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import smc.smedit.logic.utils.ShortUtils;
import smc.smedit.ui.BlockTypeColors;

/**
 * Classifies the hull/armor blocks of the <em>configured StarMade install</em>
 * into tiers and shapes — with no hardcoded ids, names, or colors.
 *
 * <p>SMEdit already reads {@code data/config/BlockTypes.properties} (type→id)
 * and {@code data/config/BlockConfig.xml} at runtime via
 * {@link BlockTypeColors#loadBlockIcons()}. This class simply walks that live
 * type→id table ({@link BlockTypeColors#mBlockTypes}) and buckets each hull type
 * by parsing its type string: the tier is the suffix (a {@code *_HULL} type is
 * Basic Armor; {@code *_STANDARD_ARMOR}, {@code *_ADVANCED_ARMOR},
 * {@code *_CRYSTAL_ARMOR}, {@code *_HAZARD_ARMOR} name their tiers) and the shape
 * is whatever trails it ({@code _WEDGE}, {@code _CORNER}, ... — absent means a
 * full cube). New colors and tiers therefore appear automatically as the install
 * changes; nothing here needs editing.
 *
 * @author SMEdit3
 **/
public final class HullCatalog {

    /** A hull tier, most-basic first, with the type token that identifies it. */
    public enum Tier {
        BASIC("Basic Armor", "_HULL"),
        STANDARD("Standard Armor", "_STANDARD_ARMOR"),
        ADVANCED("Advanced Armor", "_ADVANCED_ARMOR"),
        CRYSTAL("Crystal Armor", "_CRYSTAL_ARMOR"),
        HAZARD("Hazard Armor", "_HAZARD_ARMOR");

        private final String label;
        private final String token;

        Tier(String label, String token) {
            this.label = label;
            this.token = token;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Recognized shape suffixes (anything else after a tier token = not a hull). */
    private static final String[] SHAPE_SUFFIXES = {
        "_WEDGE", "_CORNER", "_HEPTA", "_TETRA",
        "_QUARTER_SLAB", "_HALF_SLAB", "_THREE_QUARTER_SLAB"
    };

    // Lazily built classification: tier -> cube ids, tier -> all ids.
    private static Map<Tier, List<Short>> cubeByTier;
    private static Map<Tier, List<Short>> allByTier;

    private HullCatalog() {
    }

    private static synchronized void ensure() {
        if (cubeByTier != null) {
            return;
        }
        cubeByTier = new EnumMap<>(Tier.class);
        allByTier = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            cubeByTier.put(t, new ArrayList<>());
            allByTier.put(t, new ArrayList<>());
        }
        BlockTypeColors.loadBlockIcons(); // best-effort: populates mBlockTypes from the install
        if (BlockTypeColors.mBlockTypes == null) {
            return;
        }
        for (String type : BlockTypeColors.mBlockTypes.stringPropertyNames()) {
            classify(type.toUpperCase());
        }
    }

    private static void classify(String type) {
        for (Tier tier : Tier.values()) {
            int idx = type.indexOf(tier.token);
            if (idx <= 0) {
                continue; // token absent, or nothing before it to be the color
            }
            String tail = type.substring(idx + tier.token.length());
            if (!isCubeOrKnownShape(tail)) {
                continue;
            }
            short id = ShortUtils.parseShort(BlockTypeColors.mBlockTypes.getProperty(type));
            if (id <= 0 || BlockTypeColors.isDeprecated(id) || !BlockTypeColors.isObtainable(id)) {
                return; // skip unknown, deprecated, or non-craftable/non-purchasable blocks
            }
            allByTier.get(tier).add(id);
            if (tail.isEmpty()) {
                cubeByTier.get(tier).add(id);
            }
            return; // a type belongs to exactly one tier
        }
    }

    private static boolean isCubeOrKnownShape(String tail) {
        if (tail.isEmpty()) {
            return true; // full cube
        }
        for (String s : SHAPE_SUFFIXES) {
            if (tail.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** Tiers that actually have at least one block in the current install. */
    public static List<Tier> presentTiers() {
        ensure();
        List<Tier> present = new ArrayList<>();
        for (Tier t : Tier.values()) {
            if (!allByTier.get(t).isEmpty()) {
                present.add(t);
            }
        }
        return present;
    }

    /** Block ids for a tier; {@code cubeOnly} excludes wedge/corner/slab shapes. */
    public static List<Short> blocks(Tier tier, boolean cubeOnly) {
        ensure();
        Map<Tier, List<Short>> src = cubeOnly ? cubeByTier : allByTier;
        return new ArrayList<>(src.get(tier));
    }

    /** Block ids across every hull tier. */
    public static List<Short> allBlocks(boolean cubeOnly) {
        ensure();
        List<Short> out = new ArrayList<>();
        Map<Tier, List<Short>> src = cubeOnly ? cubeByTier : allByTier;
        for (Tier t : Tier.values()) {
            out.addAll(src.get(t));
        }
        return out;
    }
}
