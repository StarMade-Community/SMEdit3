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
package smc.smedit.ui.act.plugin;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import smc.smedit.plugins.ship.imp.ConversionPalette;
import smc.smedit.plugins.ship.imp.HullCatalog;

/**
 * The inline editor shown for the model-import block palette: a tier-preset
 * dropdown, a scrollable strip of color swatches for the current selection, and
 * an "Edit…" button that opens the full {@link PaletteEditorDialog}. Presets are
 * pulled live from {@link HullCatalog}, so they reflect the installed StarMade
 * version's tiers and colors.
 *
 * @author SMEdit3
 **/
@SuppressWarnings("serial")
public class PalettePropertyPanel extends JPanel {

    private final PalettePropertyEditor editor;
    private final JComboBox<PresetItem> presets;
    private final SwatchStrip strip;
    /** Suppresses the combo's action listener while we set it programmatically. */
    private boolean updating;

    public PalettePropertyPanel(PalettePropertyEditor editor) {
        this.editor = editor;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        presets = new JComboBox<>(buildPresets().toArray(new PresetItem[0]));
        presets.addActionListener(e -> {
            if (!updating) {
                onPresetChosen();
            }
        });
        Dimension pref = presets.getPreferredSize();
        presets.setMaximumSize(new Dimension(180, pref.height));

        strip = new SwatchStrip();
        JScrollPane scroll = new JScrollPane(strip,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(220, 32));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JButton edit = new JButton("Edit…");
        edit.addActionListener(e -> onEdit());

        add(presets);
        add(Box.createHorizontalStrut(4));
        add(scroll);
        add(Box.createHorizontalStrut(4));
        add(edit);

        refresh(currentIds());
        selectMatchingPreset();
    }

    private List<Short> currentIds() {
        Object v = editor.getValue();
        return ConversionPalette.fromText(v == null ? "" : v.toString()).getBlockIds();
    }

    private void setIds(List<Short> ids) {
        editor.setValue(new ConversionPalette(ids).toText());
        refresh(ids);
    }

    private void refresh(List<Short> ids) {
        strip.setIds(ids);
    }

    private void onPresetChosen() {
        PresetItem item = (PresetItem) presets.getSelectedItem();
        if (item == null) {
            return;
        }
        if (item.ids == null) { // "Custom…" entry
            onEdit();
        } else {
            setIds(item.ids);
        }
    }

    private void onEdit() {
        List<Short> result = PaletteEditorDialog.edit(SwingUtilities.getWindowAncestor(this), currentIds());
        if (result != null) {
            setIds(result);
        }
        selectMatchingPreset();
    }

    /** Highlights the preset matching the current selection, or "Custom…". */
    private void selectMatchingPreset() {
        Set<Short> current = new HashSet<>(currentIds());
        updating = true;
        try {
            PresetItem custom = null;
            for (int i = 0; i < presets.getItemCount(); i++) {
                PresetItem item = presets.getItemAt(i);
                if (item.ids == null) {
                    custom = item;
                    continue;
                }
                if (new HashSet<>(item.ids).equals(current) && !current.isEmpty()) {
                    presets.setSelectedItem(item);
                    return;
                }
            }
            if (custom != null) {
                presets.setSelectedItem(custom);
            }
        } finally {
            updating = false;
        }
    }

    private static List<PresetItem> buildPresets() {
        List<PresetItem> items = new ArrayList<>();
        for (HullCatalog.Tier tier : HullCatalog.presentTiers()) {
            items.add(new PresetItem(tier.getLabel(), HullCatalog.blocks(tier, true)));
        }
        List<Short> all = HullCatalog.allBlocks(true);
        if (!all.isEmpty()) {
            items.add(new PresetItem("All Armor", all));
        }
        items.add(new PresetItem("Custom…", null));
        return items;
    }

    /** One entry in the preset dropdown ({@code ids == null} means "Custom…"). */
    private static final class PresetItem {
        final String label;
        final List<Short> ids;

        PresetItem(String label, List<Short> ids) {
            this.label = label;
            this.ids = ids;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Horizontal row of block build icons (one per palette block), with tooltips. */
    private static final class SwatchStrip extends JComponent {
        private static final int SW = 22;
        private List<Short> ids = new ArrayList<>();

        SwatchStrip() {
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        void setIds(List<Short> ids) {
            this.ids = new ArrayList<>(ids);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(1, ids.size() * SW), SW);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int i = 0; i < ids.size(); i++) {
                Icon icon = PaletteBlocks.icon(ids.get(i), SW - 2);
                icon.paintIcon(this, g, i * SW + 1, 1);
            }
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int idx = e.getX() / SW;
            if (idx >= 0 && idx < ids.size()) {
                return PaletteBlocks.name(ids.get(idx));
            }
            return null;
        }
    }
}
