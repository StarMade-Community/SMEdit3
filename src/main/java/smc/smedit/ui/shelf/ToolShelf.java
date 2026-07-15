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
import smc.smedit.ui.RenderPanel;
import smc.smedit.ui.act.plugin.BlocksPluginAction;
import smc.smedit.ui.logic.ShipSpec;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Maya-shelf-style tool palette: the editor's block plugins ("functions") are
 * grouped into horizontally-scrollable tabs, one icon button per tool. This
 * replaces the old "Plugins" toolbar popup and the plugin items injected into
 * the menu bar.
 *
 * <p>The panel is hosted in a dockable so it can be moved / floated / hidden
 * like the other editor panels. It rebuilds itself whenever the current model
 * changes (a different ship/station exposes a different set of tools), listening
 * on the {@code currentModel} property of the {@link smc.smedit.data.StarMade}
 * singleton.
 *
 * <h2>Categorisation</h2>
 * Each tool is sorted into a tab by {@link #categoryOf}: a {@code Group/Name}
 * prefix in the tool name wins (Import, Export, Shape, Macro), view-only tools
 * go to <em>View</em>, then a small keyword map yields the finer buckets
 * (Select, Clipboard, Transform, Build, Surface, Paint, Info), and finally the
 * tool's plugin subtype is the fallback. Everything is data-driven, so new or
 * external plugins are placed and iconified automatically.
 */
@SuppressWarnings("serial")
public class ToolShelf extends JPanel {

    private static final Logger log = Logger.getLogger(ToolShelf.class.getName());

    /**
     * Glyph size (px) for the shelf buttons.
     */
    private static final int ICON_SIZE = 22;

    /**
     * Preferred left-to-right tab order; unlisted categories are appended, sorted.
     */
    private static final List<String> CATEGORY_ORDER = Arrays.asList("Select", "Clipboard", "Transform", "Build", "Surface", "Paint", "Shape", "Generate", "Import", "Export", "Macro", "Info", "Edit", "Modify", "View", "File", "General");

    /**
     * Keyword (lower-case, substring) → category, tested in order.
     */
    private static final String[][] KEYWORD_CATEGORIES = {{"select", "Select"}, {"copy", "Clipboard"}, {"cut", "Clipboard"}, {"paste", "Clipboard"}, {"delete", "Clipboard"}, {"move", "Transform"}, {"rotate", "Transform"}, {"scale", "Transform"}, {"reflect", "Transform"}, {"symmetr", "Transform"}, {"duplicate", "Transform"}, {"mirror", "Transform"}, {"fill", "Build"}, {"deck", "Build"}, {"hollow", "Build"}, {"hull", "Build"}, {"smooth", "Surface"}, {"soften", "Surface"}, {"harden", "Surface"}, {"replace", "Paint"}, {"stripe", "Paint"}, {"ombre", "Paint"}, {"text", "Paint"}, {"image", "Paint"}, {"report", "Info"}, {"propert", "Info"},};

    private final transient RenderPanel client;
    private final JTabbedPane tabs;

    public ToolShelf(RenderPanel client) {
        super(new BorderLayout());
        this.client = client;
        tabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setFocusable(false);
        add(tabs, BorderLayout.CENTER);
        rebuild();

        // A different model may expose a different tool set; rebuild when it changes.
        StarMadeLogic.getInstance().addPropertyChangeListener("currentModel", e -> SwingUtilities.invokeLater(this::rebuild));
    }

    /**
     * Classification of the current model, or {@code TYPE_ALL} if none is loaded.
     */
    private static int currentType() {
        ShipSpec spec = StarMadeLogic.getInstance().getCurrentModel();
        return spec != null ? spec.getClassification() : IBlocksPlugin.TYPE_ALL;
    }

    /**
     * The shelf tab a tool belongs in. Precedence: a {@code Group/Name} prefix,
     * then view-only tools, then a keyword match, then the plugin subtype.
     */
    static String categoryOf(IBlocksPlugin plugin) {
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

    // ------------------------------------------------------------------
    // Categorisation
    // ------------------------------------------------------------------

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
     * Button label: the part after any {@code Group/} prefix, minus a trailing ellipsis.
     */
    private static String shortLabel(String name) {
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
    private static String tooltip(IBlocksPlugin plugin) {
        String description = plugin.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        return plugin.getName();
    }

    /**
     * Rebuilds every tab from the plugins valid for the current model type,
     * preserving the selected tab (by title) across the rebuild. Safe to call
     * from the EDT at any time.
     */
    public final void rebuild() {
        String selected = tabs.getSelectedIndex() >= 0 ? tabs.getTitleAt(tabs.getSelectedIndex()) : null;
        tabs.removeAll();

        List<IBlocksPlugin> plugins;
        try {
            // subtype -1 = every subtype; type filtering keeps ship vs planet tools apart.
            plugins = StarMadeLogic.getBlocksPlugins(currentType(), -1);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Could not list tools for the shelf", t);
            plugins = Collections.emptyList();
        }

        Map<String, List<IBlocksPlugin>> byCategory = new LinkedHashMap<>();
        for (IBlocksPlugin plugin : plugins) {
            byCategory.computeIfAbsent(categoryOf(plugin), k -> new ArrayList<>()).add(plugin);
        }

        for (String category : orderCategories(byCategory.keySet())) {
            List<IBlocksPlugin> tools = byCategory.get(category);
            tools.sort((a, b) -> shortLabel(a.getName()).compareToIgnoreCase(shortLabel(b.getName())));
            tabs.addTab(category, buildStrip(category, tools));
        }
        if (tabs.getTabCount() == 0) {
            JLabel empty = new JLabel("No tools available for this model.", SwingConstants.CENTER);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            tabs.addTab("Tools", empty);
        }

        if (selected != null) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (selected.equals(tabs.getTitleAt(i))) {
                    tabs.setSelectedIndex(i);
                    break;
                }
            }
        }
        revalidate();
        repaint();
    }

    /**
     * A single tab: one horizontally-scrolling row of tool buttons.
     */
    private JComponent buildStrip(String category, List<IBlocksPlugin> tools) {
        JToolBar strip = new JToolBar();
        strip.setFloatable(false);
        strip.setBorderPainted(false);
        strip.setOpaque(false);
        for (IBlocksPlugin plugin : tools) {
            strip.add(makeButton(category, plugin));
        }

        JScrollPane scroll = new JScrollPane(strip, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        // Let the mouse wheel scroll the row sideways (there is no vertical axis).
        scroll.addMouseWheelListener(e -> {
            JScrollBar bar = scroll.getHorizontalScrollBar();
            bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
        });
        return scroll;
    }

    /**
     * An icon-over-label button that runs the plugin via {@link BlocksPluginAction}.
     */
    private JButton makeButton(String category, IBlocksPlugin plugin) {
        JButton button = new JButton(new BlocksPluginAction(client, plugin));
        button.setText(shortLabel(plugin.getName()));
        button.setIcon(ShelfIcons.iconFor(plugin.getName(), category, ICON_SIZE));
        button.setToolTipText(tooltip(plugin));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(2);
        button.setFocusable(false);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 10.0f));
        button.setMargin(new Insets(4, 6, 4, 6));
        return button;
    }
}
