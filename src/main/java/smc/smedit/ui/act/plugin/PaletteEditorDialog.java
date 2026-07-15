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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import smc.smedit.data.BlockTypes;
import smc.smedit.plugins.ship.imp.HullCatalog;
import smc.smedit.ui.BlockTypeColors;

/**
 * Modal picker for building a model-import block palette. The left "Available"
 * list is the live install's blocks ({@link BlockTypes#BLOCK_NAMES}), filterable
 * by hull tier ({@link HullCatalog}) and a name search; the right "Chosen" list
 * is the ordered palette. Everything renders with a color swatch so the user can
 * see exactly which colors the import may use.
 *
 * @author SMEdit3
 **/
@SuppressWarnings("serial")
public class PaletteEditorDialog extends JDialog {

    private List<Short> result;

    private final List<Short> allAvailable = new ArrayList<>();
    private final Map<Short, HullCatalog.Tier> tierOf = new HashMap<>();

    private final DefaultListModel<Short> availableModel = new DefaultListModel<>();
    private final DefaultListModel<Short> chosenModel = new DefaultListModel<>();
    private final JList<Short> availableList = new JList<>(availableModel);
    private final JList<Short> chosenList = new JList<>(chosenModel);
    private final JTextField search = new JTextField(14);
    private final JComboBox<FilterItem> tierFilter = new JComboBox<>();

    /**
     * Shows the dialog and returns the chosen block ids in order, or {@code null}
     * if the user cancelled.
     */
    public static List<Short> edit(Window parent, List<Short> initial) {
        PaletteEditorDialog dlg = new PaletteEditorDialog(parent, initial);
        dlg.setVisible(true);
        return dlg.result;
    }

    private PaletteEditorDialog(Window parent, List<Short> initial) {
        super(parent, "Edit Block Palette", ModalityType.APPLICATION_MODAL);
        buildSources();
        BlockCellRenderer renderer = new BlockCellRenderer();
        availableList.setCellRenderer(renderer);
        chosenList.setCellRenderer(renderer);

        for (Short id : initial) {
            if (!chosenModel.contains(id)) {
                chosenModel.addElement(id);
            }
        }
        buildTierFilter();
        applyFilter();

        setLayout(new BorderLayout(8, 8));
        add(buildCenter(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        tierFilter.addActionListener(e -> applyFilter());

        setSize(560, 460);
        setLocationRelativeTo(parent);
    }

    private void buildSources() {
        // Every non-deprecated block the install knows about. (Already-chosen ids
        // still render in the Chosen list even if excluded here.)
        java.util.Set<Short> seen = new java.util.HashSet<>();
        for (Short id : BlockTypes.BLOCK_NAMES.keySet()) {
            if (id != null && id > 0 && !BlockTypeColors.isDeprecated(id) && seen.add(id)) {
                allAvailable.add(id);
            }
        }
        allAvailable.sort(Comparator.comparing(PaletteBlocks::name, String.CASE_INSENSITIVE_ORDER));
        for (HullCatalog.Tier tier : HullCatalog.Tier.values()) {
            for (Short id : HullCatalog.blocks(tier, false)) {
                tierOf.put(id, tier);
            }
        }
    }

    private void buildTierFilter() {
        tierFilter.addItem(new FilterItem("All blocks", null, false));
        for (HullCatalog.Tier tier : HullCatalog.presentTiers()) {
            tierFilter.addItem(new FilterItem(tier.getLabel(), tier, false));
        }
        tierFilter.addItem(new FilterItem("Other (non-hull)", null, true));
    }

    private JPanel buildCenter() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        JPanel filter = new JPanel();
        filter.setLayout(new BoxLayout(filter, BoxLayout.X_AXIS));
        filter.add(new JLabel("Find:"));
        filter.add(Box.createHorizontalStrut(4));
        filter.add(search);
        filter.add(Box.createHorizontalStrut(6));
        filter.add(tierFilter);
        left.add(labeled("Available", filter, availableList), BorderLayout.CENTER);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.add(Box.createVerticalGlue());
        mid.add(button("Add →", e -> addSelected()));
        mid.add(Box.createVerticalStrut(6));
        mid.add(button("← Remove", e -> removeSelected()));
        mid.add(Box.createVerticalStrut(18));
        mid.add(button("Up", e -> move(-1)));
        mid.add(Box.createVerticalStrut(6));
        mid.add(button("Down", e -> move(1)));
        mid.add(Box.createVerticalGlue());

        JPanel right = labeled("Chosen", null, chosenList);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 8));
        lists.add(left);
        lists.add(right);
        center.add(lists, BorderLayout.CENTER);
        center.add(mid, BorderLayout.EAST);
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        return center;
    }

    private JPanel labeled(String title, JPanel north, JList<Short> list) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel(title), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(4, 4));
        if (north != null) {
            body.add(north, BorderLayout.NORTH);
        }
        body.add(new JScrollPane(list), BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        p.add(Box.createHorizontalGlue());
        p.add(button("OK", e -> {
            result = new ArrayList<>();
            for (int i = 0; i < chosenModel.size(); i++) {
                result.add(chosenModel.get(i));
            }
            dispose();
        }));
        p.add(Box.createHorizontalStrut(6));
        p.add(button("Cancel", e -> {
            result = null;
            dispose();
        }));
        return p;
    }

    private JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        return b;
    }

    private void addSelected() {
        for (Short id : availableList.getSelectedValuesList()) {
            if (!chosenModel.contains(id)) {
                chosenModel.addElement(id);
            }
        }
        applyFilter();
    }

    private void removeSelected() {
        for (Short id : chosenList.getSelectedValuesList()) {
            chosenModel.removeElement(id);
        }
        applyFilter();
    }

    private void move(int delta) {
        int i = chosenList.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= chosenModel.size()) {
            return;
        }
        Short a = chosenModel.get(i);
        chosenModel.set(i, chosenModel.get(j));
        chosenModel.set(j, a);
        chosenList.setSelectedIndex(j);
    }

    /** Rebuilds the Available list from the current search text and tier filter. */
    private void applyFilter() {
        String text = search.getText().trim().toLowerCase(Locale.ROOT);
        FilterItem filter = (FilterItem) tierFilter.getSelectedItem();
        availableModel.clear();
        for (Short id : allAvailable) {
            if (chosenModel.contains(id)) {
                continue;
            }
            if (!matchesTier(id, filter)) {
                continue;
            }
            if (!text.isEmpty() && !PaletteBlocks.name(id).toLowerCase(Locale.ROOT).contains(text)) {
                continue;
            }
            availableModel.addElement(id);
        }
    }

    private boolean matchesTier(Short id, FilterItem filter) {
        if (filter == null || (filter.tier == null && !filter.other)) {
            return true; // "All blocks"
        }
        if (filter.other) {
            return !tierOf.containsKey(id);
        }
        return filter.tier == tierOf.get(id);
    }

    /** One tier-filter dropdown entry. */
    private static final class FilterItem {
        final String label;
        final HullCatalog.Tier tier;
        final boolean other;

        FilterItem(String label, HullCatalog.Tier tier, boolean other) {
            this.label = label;
            this.tier = tier;
            this.other = other;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Renders a block id as its StarMade build icon + display name. */
    private static final class BlockCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            Short id = (Short) value;
            setText(PaletteBlocks.name(id));
            setIcon(PaletteBlocks.icon(id, 20));
            return this;
        }
    }
}
