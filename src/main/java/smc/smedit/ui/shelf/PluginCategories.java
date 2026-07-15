/**
 * Copyright 2014 SMEdit https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package smc.smedit.ui.shelf;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.ui.logic.ShipSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sorts the editor's block plugins ("operations") into named categories and
 * exposes them for the UI. Each operation is a one-shot command (Select All,
 * Fill, Import, Export, a macro …); this class decides which bucket it belongs
 * in so the context bar can surface the operations for the active tool and the
 * <em>Operations</em> menu can list the rest.
 *
 * <p>Categorisation is data-driven (see {@link #categoryOf}): a {@code Group/Name}
 * prefix in the tool name wins (Import, Export, Shape, Macro), view-only tools go
 * to <em>View</em>, then a small keyword map yields the finer buckets (Select,
 * Clipboard, Transform, Build, Surface, Paint, Info), and finally the plugin's
 * subtype is the fallback. New or external plugins are placed automatically.
 *
 * <p>This logic used to live in the removed {@code ToolShelf} dockable; it now
 * feeds the tool context bar and the Operations menu instead.
 */
public final class PluginCategories {

    private static final Logger log = Logger.getLogger(PluginCategories.class.getName());

    private PluginCategories() {
    }

    /**
     * Preferred left-to-right / top-to-bottom category order; unlisted categories
     * are appended, sorted.
     */
    private static final List<String> CATEGORY_ORDER = Arrays.asList("Select", "Clipboard", "Transform", "Build", "Surface", "Paint", "Shape", "Generate", "Import", "Export", "Macro", "Info", "Edit", "Modify", "View", "File", "General");

    /**
     * Keyword (lower-case, substring) → category, tested in order.
     */
    private static final String[][] KEYWORD_CATEGORIES = {{"select", "Select"}, {"copy", "Clipboard"}, {"cut", "Clipboard"}, {"paste", "Clipboard"}, {"delete", "Clipboard"}, {"move", "Transform"}, {"rotate", "Transform"}, {"scale", "Transform"}, {"reflect", "Transform"}, {"symmetr", "Transform"}, {"duplicate", "Transform"}, {"mirror", "Transform"}, {"fill", "Build"}, {"deck", "Build"}, {"hollow", "Build"}, {"hull", "Build"}, {"smooth", "Surface"}, {"soften", "Surface"}, {"harden", "Surface"}, {"replace", "Paint"}, {"stripe", "Paint"}, {"ombre", "Paint"}, {"text", "Paint"}, {"image", "Paint"}, {"report", "Info"}, {"propert", "Info"},};

    /**
     * The operations valid for the current model, grouped by category and ordered
     * by {@link #CATEGORY_ORDER}. Operations within a category are sorted by their
     * short label. Never {@code null}; empty if no model / no plugins.
     */
    public static Map<String, List<IBlocksPlugin>> byCategory() {
        List<IBlocksPlugin> plugins;
        try {
            // subtype -1 = every subtype; type filtering keeps ship vs planet tools apart.
            plugins = StarMadeLogic.getBlocksPlugins(currentType(), -1);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Could not list operations", t);
            plugins = Collections.emptyList();
        }

        Map<String, List<IBlocksPlugin>> byCategory = new LinkedHashMap<>();
        for (IBlocksPlugin plugin : plugins) {
            byCategory.computeIfAbsent(categoryOf(plugin), k -> new ArrayList<>()).add(plugin);
        }

        Map<String, List<IBlocksPlugin>> ordered = new LinkedHashMap<>();
        for (String category : orderCategories(byCategory.keySet())) {
            List<IBlocksPlugin> tools = byCategory.get(category);
            tools.sort((a, b) -> shortLabel(a.getName()).compareToIgnoreCase(shortLabel(b.getName())));
            ordered.put(category, tools);
        }
        return ordered;
    }

    /**
     * Classification of the current model, or {@code TYPE_ALL} if none is loaded.
     */
    private static int currentType() {
        ShipSpec spec = StarMadeLogic.getInstance().getCurrentModel();
        return spec != null ? spec.getClassification() : IBlocksPlugin.TYPE_ALL;
    }

    /**
     * The category an operation belongs in. Precedence: a {@code Group/Name}
     * prefix, then view-only tools, then a keyword match, then the plugin subtype.
     */
    public static String categoryOf(IBlocksPlugin plugin) {
        String name = plugin.getName();
        if (name == null) {
            name = "";
        }
        int slash = name.indexOf('/');
        if (slash > 0) {
            return name.substring(0, slash).trim();
        }
        Set<Integer> subtypes = subtypesOf(plugin);
        if (subtypes.size() == 1 && subtypes.contains(IBlocksPlugin.SUBTYPE_VIEW)) {
            return "View";
        }
        String lower = name.toLowerCase();
        for (String[] entry : KEYWORD_CATEGORIES) {
            if (lower.contains(entry[0])) {
                return entry[1];
            }
        }
        return subtypeCategory(subtypes);
    }

    /**
     * Distinct subtype ids declared by the plugin, resilient to bad plugins.
     */
    private static Set<Integer> subtypesOf(IBlocksPlugin plugin) {
        Set<Integer> subtypes = new LinkedHashSet<>();
        try {
            int[][] classifications = plugin.getClassifications();
            if (classifications != null) {
                for (int[] classification : classifications) {
                    if (classification != null && classification.length > 1) {
                        subtypes.add(classification[1]);
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.FINE, "plugin getClassifications() failed", e);
        }
        return subtypes;
    }

    /**
     * Fallback category from a tool's subtype(s); universal tools go to General.
     */
    private static String subtypeCategory(Set<Integer> subtypes) {
        if (subtypes.size() >= 4) {
            return "General";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_MODIFY)) {
            return "Modify";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_PAINT)) {
            return "Paint";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_GENERATE)) {
            return "Generate";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_EDIT)) {
            return "Edit";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_FILE)) {
            return "File";
        }
        if (subtypes.contains(IBlocksPlugin.SUBTYPE_VIEW)) {
            return "View";
        }
        return "General";
    }

    /**
     * Orders the present categories by {@link #CATEGORY_ORDER}, extras appended sorted.
     */
    private static List<String> orderCategories(Set<String> present) {
        List<String> ordered = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            if (present.contains(category)) {
                ordered.add(category);
            }
        }
        List<String> extras = new ArrayList<>();
        for (String category : present) {
            if (!CATEGORY_ORDER.contains(category)) {
                extras.add(category);
            }
        }
        Collections.sort(extras);
        ordered.addAll(extras);
        return ordered;
    }

    /**
     * Button/menu label: the part after any {@code Group/} prefix, minus a trailing ellipsis.
     */
    public static String shortLabel(String name) {
        if (name == null) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        String label = (slash >= 0 ? name.substring(slash + 1) : name).trim();
        label = label.replace("…", "");
        while (label.endsWith(".")) {
            label = label.substring(0, label.length() - 1);
        }
        return label.trim();
    }

    /**
     * Tooltip: the tool's description, falling back to its full name.
     */
    public static String tooltip(IBlocksPlugin plugin) {
        String description = plugin.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        return plugin.getName();
    }
}
