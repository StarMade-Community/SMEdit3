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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.ui.tool.EditorTool;
import smc.smedit.ui.tool.ToolController;
import smc.smedit.ui.tool.ToolController.BrushShape;
import smc.smedit.ui.tool.ToolController.FillMode;

/**
 * The redesigned Brush dock — a full material palette plus dynamic/shaped brush
 * settings, replacing the old nine-swatch {@code EditPanel}. It picks the active
 * block for the Paint / Build tools and drives the shared brush settings on
 * {@link ToolController}: brush footprint (point / box / sphere / disc), size,
 * fill mode (solid / hollow / surface) and X/Y/Z symmetry.
 *
 * <p>Layout: a fixed header (active-block chip + search) on top, a scrolling
 * category tree of blocks in the middle (grouped by StarMade's own BlockConfig
 * hierarchy), and the always-visible brush controls at the bottom.
 */
@SuppressWarnings("serial")
public class BrushPanel extends JPanel implements ToolController.Listener {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(BrushPanel.class.getName());

    private static final int CHIP_ICON = 40;
    private static final int TREE_ICON = 18;       // block icon size in tree rows (px)
    private static final int CATEGORY_DEPTH = 3;   // category-path depth grouped in the tree

    private final transient ToolController tc = ToolController.get();

    private final JLabel chipIcon = new JLabel();
    private final JLabel chipName = new JLabel();
    private final JLabel chipSub = new JLabel();
    private final JTextField search = new JTextField();
    private final JLabel sizeValue = new JLabel();

    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree blockTree = new JTree(treeModel);
    private final Map<Short, DefaultMutableTreeNode> blockNodes = new HashMap<>();
    private boolean syncingTree;                                 // guard while syncing tree selection

    private final JComboBox<Form> blockShapeBox = new JComboBox<>();
    private boolean syncingForm;                                 // guard while rebuilding blockShapeBox
    private final List<Short> ids = new ArrayList<>();

    /** A block leaf in the palette tree (category nodes hold a plain String). */
    private static final class BlockRef {
        final short id;
        final String name;

        BlockRef(short id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** One entry of the Block Shape dropdown: a shape ({@code slab==0}) or a slab level. */
    private static final class Form {
        final int style;
        final int slab;
        final String label;

        Form(int style, int slab, String label) {
            this.style = style;
            this.slab = slab;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public BrushPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // The palette grid + brush controls need room; keep the dock from being
        // crushed (this floor also applies to a restored/saved layout, not just
        // the default split width).
        setMinimumSize(new Dimension(200, 200));
        setPreferredSize(new Dimension(280, 600));

        loadBlocks();
        setupTree();

        add(buildHeader(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(blockTree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(hairline()));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);

        // Paint needs a material out of the box — default to grey hull if unset.
        if (tc.getActiveBlockType() < 0) {
            tc.setActiveBlockType(Blocks.GREY_STANDARD_ARMOR.getId());
        }
        buildTree("");
        updateFormControl(tc.getActiveBlockType());
        updateChip(tc.getActiveBlockType());
        syncTreeSelection();
        tc.addListener(this);
    }

    // ------------------------------------------------------------------
    // Block catalogue
    // ------------------------------------------------------------------

    private void loadBlocks() {
        // BLOCK_NAMES is filled lazily by loadBlockIcons(); make sure it has run
        // so the palette isn't empty if we're built before the renderer triggers it.
        if (BlockGroups.BLOCK_NAMES.isEmpty()) {
            BlockTypeColors.loadBlockIcons();
        }
        for (Short id : BlockGroups.BLOCK_NAMES.keySet()) {
            if (id == null) {
                continue;
            }
            // Exclusion (matching the selection filters): drop deprecated blocks and
            // any that are neither buyable in a shop nor craftable in a factory.
            if (BlockTypeColors.isDeprecated(id) || !BlockTypeColors.isObtainable(id)) {
                continue;
            }
            // Collapse variants: show only the full base cube. Slab (¾/½/¼) and
            // wedge/corner/tetra/hepta variants are reached via the Block Shape control.
            if (BlockTypeColors.getBlockSlab(id) > 0 || BlockShapes.isNonBaseVariant(id)) {
                continue;
            }
            ids.add(id);
        }
        ids.sort(Comparator
                .comparing((Short id) -> BlockGroups.BLOCK_NAMES.getOrDefault(id, "").toLowerCase())
                .thenComparing(id -> id));
        long categorised = ids.stream().filter(BlockTypeColors.BLOCK_CATEGORY::containsKey).count();
        LOG.info("Brush palette: " + ids.size() + " blocks (" + categorised
                + " categorised), " + BlockTypeColors.BLOCK_SLAB_IDS.size() + " slab families");
    }

    // ------------------------------------------------------------------
    // Header (chip + search)
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = vbox();

        JPanel chip = new JPanel(new BorderLayout(10, 0));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(hairline()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        chipIcon.setPreferredSize(new Dimension(CHIP_ICON, CHIP_ICON));
        chip.add(chipIcon, BorderLayout.WEST);
        JPanel meta = vbox();
        chipName.setFont(chipName.getFont().deriveFont(chipName.getFont().getSize() + 1f));
        chipSub.setForeground(muted());
        chipSub.setFont(chipSub.getFont().deriveFont(chipSub.getFont().getSize() - 1f));
        meta.add(chipName);
        meta.add(chipSub);
        chip.add(meta, BorderLayout.CENTER);
        header.add(fullWidth(chip));
        header.add(Box.createVerticalStrut(8));

        search.putClientProperty("JTextField.placeholderText", "Search blocks…");
        search.getDocument().addDocumentListener(new SimpleDoc(() -> buildTree(search.getText())));
        header.add(fullWidth(search));
        return header;
    }

    // ------------------------------------------------------------------
    // Palette tree (grouped by StarMade's BlockConfig category hierarchy)
    // ------------------------------------------------------------------

    private void setupTree() {
        blockTree.setRootVisible(false);
        blockTree.setShowsRootHandles(true);
        blockTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        blockTree.setCellRenderer(new BlockTreeRenderer());
        blockTree.setToolTipText("Click a block to select · Shift-click to add · Ctrl-click to remove");
        // The material set is the source of truth; drive it from clicks (Shift = add,
        // Ctrl = remove, plain = replace) and mirror it back onto the tree selection.
        blockTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = blockTree.getPathForLocation(e.getX(), e.getY());
                Object node = path == null ? null : path.getLastPathComponent();
                if (!(node instanceof DefaultMutableTreeNode)
                        || !(((DefaultMutableTreeNode) node).getUserObject() instanceof BlockRef)) {
                    // Category row (or empty) — let it expand/collapse, then restore
                    // the material highlight so a category doesn't look "selected".
                    javax.swing.SwingUtilities.invokeLater(BrushPanel.this::syncTreeSelection);
                    return;
                }
                short id = ((BlockRef) ((DefaultMutableTreeNode) node).getUserObject()).id;
                if (e.isShiftDown()) {
                    tc.addMaterial(id);
                } else if (e.isControlDown()) {
                    tc.removeMaterial(id);
                } else {
                    tc.setActiveBlockType(id);
                }
            }
        });
    }

    /** Rebuilds the tree, keeping only blocks matching {@code query} (all expanded when searching). */
    private void buildTree(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Blocks");
        Map<String, DefaultMutableTreeNode> categories = new HashMap<>();
        blockNodes.clear();
        for (Short id : ids) {
            String name = BlockGroups.BLOCK_NAMES.getOrDefault(id, "");
            if (!q.isEmpty() && !name.toLowerCase().contains(q)) {
                continue;
            }
            DefaultMutableTreeNode parent = categoryNodeFor(root, categories, id);
            DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new BlockRef(id, name));
            parent.add(leaf);
            blockNodes.put(id, leaf);
        }
        treeModel.setRoot(root);
        if (!q.isEmpty()) {
            for (int i = 0; i < blockTree.getRowCount(); i++) {
                blockTree.expandRow(i);
            }
        }
        syncTreeSelection();
    }

    /** Finds/creates the category-node chain for a block's config path (capped depth). */
    private DefaultMutableTreeNode categoryNodeFor(DefaultMutableTreeNode root,
            Map<String, DefaultMutableTreeNode> cache, short id) {
        String[] path = BlockTypeColors.BLOCK_CATEGORY.get(id);
        if (path == null || path.length == 0) {
            path = new String[]{"Other"};
        }
        int depth = Math.min(path.length, CATEGORY_DEPTH);
        DefaultMutableTreeNode parent = root;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            key.append('/').append(path[i]);
            DefaultMutableTreeNode node = cache.get(key.toString());
            if (node == null) {
                node = new DefaultMutableTreeNode(path[i]);
                parent.add(node);
                cache.put(key.toString(), node);
            }
            parent = node;
        }
        return parent;
    }

    /** Mirrors the material set onto the tree selection (highlights all, scrolls to the primary). */
    private void syncTreeSelection() {
        List<TreePath> paths = new ArrayList<>();
        for (Short id : tc.getMaterials()) {
            DefaultMutableTreeNode node = blockNodes.get(id);
            if (node != null) {
                paths.add(new TreePath(node.getPath()));
            }
        }
        syncingTree = true;
        blockTree.setSelectionPaths(paths.toArray(new TreePath[0]));
        if (!paths.isEmpty()) {
            blockTree.scrollPathToVisible(paths.get(0));
        }
        syncingTree = false;
    }

    /** Renders block leaves with their StarMade build icon + name; categories keep the folder look. */
    private static final class BlockTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focus);
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof BlockRef) {
                BlockRef ref = (BlockRef) userObject;
                setIcon(BlockIcons.icon(ref.id, TREE_ICON));
                setText(ref.name);
            }
            return this;
        }
    }

    // ------------------------------------------------------------------
    // Brush controls
    // ------------------------------------------------------------------

    private JPanel buildControls() {
        JPanel controls = vbox();

        // Block Shape: a single dropdown of the forms the active block supports
        // (Cube, then Wedge/Corner/Tetra/Hepta for hull-armor families, then ¾/½/¼
        // slabs). Rebuilt per active block in updateFormControl.
        controls.add(sectionLabel("Block Shape"));
        blockShapeBox.setFocusable(false);
        blockShapeBox.addActionListener(e -> {
            if (syncingForm) {
                return;
            }
            Form f = (Form) blockShapeBox.getSelectedItem();
            if (f != null) {
                tc.setBlockStyle(f.style);
                tc.setSlabLevel(f.slab);
                updateChip(tc.getActiveBlockType());
            }
        });
        controls.add(fullWidth(blockShapeBox));

        controls.add(sectionLabel("Brush Shape"));
        JComboBox<BrushShape> brushShapeBox = new JComboBox<>(BrushShape.values());
        brushShapeBox.setFocusable(false);
        brushShapeBox.setSelectedItem(tc.getBrushShape());
        brushShapeBox.addActionListener(e ->
                tc.setBrushShape((BrushShape) brushShapeBox.getSelectedItem()));
        controls.add(fullWidth(brushShapeBox));

        controls.add(sectionLabel("Size"));
        JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
        JSlider slider = new JSlider(1, 24, tc.getBrushSize());
        slider.setFocusable(false);
        sizeValue.setText(String.valueOf(tc.getBrushSize()));
        sizeValue.setPreferredSize(new Dimension(24, sizeValue.getPreferredSize().height));
        slider.addChangeListener(e -> {
            tc.setBrushSize(slider.getValue());
            sizeValue.setText(String.valueOf(slider.getValue()));
        });
        sizeRow.add(slider, BorderLayout.CENTER);
        sizeRow.add(sizeValue, BorderLayout.EAST);
        controls.add(fullWidth(sizeRow));

        controls.add(sectionLabel("Fill Mode"));
        JPanel fill = new JPanel(new GridLayout(1, FillMode.values().length, 4, 0));
        ButtonGroup fillGroup = new ButtonGroup();
        for (FillMode mode : FillMode.values()) {
            JToggleButton b = new JToggleButton(mode.toString());
            b.setFocusable(false);
            b.setSelected(tc.getFillMode() == mode);
            b.addActionListener(e -> tc.setFillMode(mode));
            fillGroup.add(b);
            fill.add(b);
        }
        controls.add(fullWidth(fill));

        controls.add(sectionLabel("Symmetry"));
        JPanel sym = new JPanel(new GridLayout(1, 3, 4, 0));
        sym.add(symToggle("X", "port / starboard", tc.isSymX(), tc::setSymX));
        sym.add(symToggle("Y", "dorsal / ventral", tc.isSymY(), tc::setSymY));
        sym.add(symToggle("Z", "fore / aft", tc.isSymZ(), tc::setSymZ));
        controls.add(fullWidth(sym));

       /* controls.add(sectionLabel("Flood Select"));
        JPanel flood = new JPanel(new GridLayout(1, 1, 4, 0));
        JToggleButton diag = new JToggleButton("Diagonals", tc.isFloodDiagonals());
        diag.setToolTipText("Double-click flood spreads through edge/corner touches, not just shared faces");
        diag.setFocusable(false);
        diag.addActionListener(e -> tc.setFloodDiagonals(diag.isSelected()));
        flood.add(diag);
        controls.add(fullWidth(flood));*/

        return controls;
    }

    private interface BoolSetter {
        void set(boolean on);
    }

    private JToggleButton symToggle(String axis, String tip, boolean on, BoolSetter setter) {
        JToggleButton b = new JToggleButton(axis, on);
        b.setToolTipText("Mirror " + tip);
        b.setFocusable(false);
        b.addActionListener(e -> setter.set(b.isSelected()));
        return b;
    }

    // ------------------------------------------------------------------
    // ToolController.Listener
    // ------------------------------------------------------------------

    @Override
    public void toolChanged(EditorTool tool) {
        // no-op: the brush panel doesn't care which tool is active.
    }

    @Override
    public void activeBlockChanged(short blockId) {
        updateFormControl(blockId);   // may clamp the remembered form to what this block supports
        updateChip(blockId);
        syncTreeSelection();
    }

    /** Chip shows the <em>effective</em> block — the active base resolved to the chosen shape/slab. */
    private void updateChip(short baseId) {
        if (baseId < 0) {
            chipIcon.setIcon(null);
            chipName.setText("No block selected");
            chipSub.setText(" ");
            return;
        }
        short eff = tc.getEffectiveBlockType();
        chipIcon.setIcon(BlockIcons.icon(eff, CHIP_ICON));
        chipName.setText(BlockIcons.name(eff));
        int count = tc.getMaterials().size();
        chipSub.setText("id " + eff + "  ·  " + formLabel(eff) + "  ·  " + categoryLabel(baseId)
                + (count > 1 ? "  ·  " + count + " materials" : ""));
    }

    /** The block's top real category (e.g. "Hulls"), from its BlockConfig path. */
    private static String categoryLabel(short id) {
        String[] path = BlockTypeColors.BLOCK_CATEGORY.get(id);
        if (path == null || path.length == 0) {
            return "?";
        }
        return path.length > 1 ? path[1] : path[0];
    }

    /**
     * Rebuilds the Block Shape dropdown for what the active block supports (Cube
     * always; Wedge/Corner/Tetra/Hepta for hull-armor families; ¾/½/¼ slabs for
     * blocks that have them) and selects the current form, clamping to Cube if the
     * remembered form isn't valid for this block.
     */
    private void updateFormControl(short baseId) {
        List<Form> forms = new ArrayList<>();
        if (baseId >= 0) {
            for (int style : BlockShapes.stylesOf(baseId)) {
                forms.add(new Form(style, 0, styleName(style)));
            }
        } else {
            forms.add(new Form(BlockTypeColors.STYLE_NORMAL, 0, styleName(BlockTypeColors.STYLE_NORMAL)));
        }
        if (baseId >= 0 && BlockSlabs.hasSlabs(baseId)) {
            for (int level : BlockSlabs.LEVELS) {
                forms.add(new Form(BlockTypeColors.STYLE_NORMAL, level, BlockSlabs.label(level) + " Slab"));
            }
        }

        // Clamp the current form to one this block offers (Cube is always first).
        int style = tc.getBlockStyle();
        int slab = tc.getSlabLevel();
        Form selected = null;
        for (Form f : forms) {
            if (f.style == style && f.slab == slab) {
                selected = f;
                break;
            }
        }
        if (selected == null) {
            selected = forms.get(0);
            tc.setBlockStyle(selected.style);
            tc.setSlabLevel(selected.slab);
        }

        syncingForm = true;
        blockShapeBox.removeAllItems();
        for (Form f : forms) {
            blockShapeBox.addItem(f);
        }
        blockShapeBox.setSelectedItem(selected);
        blockShapeBox.setEnabled(forms.size() > 1);
        syncingForm = false;
    }

    /** Describes a block's form for the chip: a slab fraction or its shape name. */
    private static String formLabel(short id) {
        int slab = BlockSlabs.levelOf(id);
        return slab > 0 ? BlockSlabs.label(slab) + " slab"
                : styleName(BlockTypeColors.getBlockStyle(id));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String styleName(int style) {
        switch (style) {
            case BlockTypeColors.STYLE_WEDGE: return "Wedge";
            case BlockTypeColors.STYLE_CORNER: return "Corner";
            case BlockTypeColors.STYLE_SPRITE: return "Sprite";
            case BlockTypeColors.STYLE_TETRA: return "Tetra";
            case BlockTypeColors.STYLE_HEPTA: return "Hepta";
            case BlockTypeColors.STYLE_NORMAL24: return "Rail";
            default: return "Cube";
        }
    }

    private static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Left-aligns a row and lets it stretch to the panel width but not its height. */
    private static <T extends JPanel> T fullWidth(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JComboBox<?> fullWidth(JComboBox<?> c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JTextField fullWidth(JTextField c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setForeground(muted());
        l.setFont(l.getFont().deriveFont((float) l.getFont().getSize() - 1f));
        l.setBorder(BorderFactory.createEmptyBorder(10, 1, 4, 0));
        l.setHorizontalAlignment(SwingConstants.LEFT);
        return l;
    }

    private static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(0x88, 0x8D, 0x98);
    }

    private static Color hairline() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : new Color(0x3A, 0x3D, 0x41);
    }

    /** DocumentListener that runs one action on any text change. */
    private static final class SimpleDoc implements DocumentListener {
        private final Runnable action;

        SimpleDoc(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}
