package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

/**
 * Dockable panel that lists data for the current block selection. The selection
 * mode drives what a viewport click selects (blocks vs. the whole entity); the
 * detail control widens the columns; the filter narrows visible rows. Summary
 * chips give the running block / type / HP totals, and the Name column carries a
 * colour dot for the block type.
 */
@SuppressWarnings("serial")
public class BlockInfoPanel extends JPanel {

    private final JComboBox<SelectionModel.Mode> mModeBox = new JComboBox<>(SelectionModel.Mode.values());
    private final JComboBox<String> mDetailBox = new JComboBox<>(new String[]{"Basic", "Full"});
    private final JTextField mFilter = new JTextField(12);
    private final JTable mTable = new JTable();
    private final Chip mBlocksChip = new Chip();
    private final Chip mTypesChip = new Chip();
    private final Chip mHpChip = new Chip();
    private boolean mSyncingMode;

    public BlockInfoPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // Don't let the header dictate a wide minimum — otherwise, when tabbed with
        // the Brush panel, the whole group can't be shrunk.
        setMinimumSize(new Dimension(150, 0));

        add(buildHeader(), BorderLayout.NORTH);

        mTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        mTable.setShowVerticalLines(false);
        mTable.setRowHeight(Math.max(mTable.getRowHeight(), 20));
        JScrollPane scroll = new JScrollPane(mTable);
        scroll.setBorder(BorderFactory.createLineBorder(hairline()));
        add(scroll, BorderLayout.CENTER);

        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        syncModeBox(sel.getMode());
        // Selection changes may fire off the render thread — marshal to the EDT.
        sel.addListener(() -> SwingUtilities.invokeLater(this::rebuild));
        rebuild();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.add(sectionLabel("Selection"));

        // Mode / Detail in a compact two-column grid so the controls stack and stay
        // full-width when the dock is narrow.
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 0, 2, 6);
        gc.anchor = GridBagConstraints.WEST;
        addControlRow(controls, gc, 0, "Mode", mModeBox);
        addControlRow(controls, gc, 1, "Detail", mDetailBox);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height));
        header.add(controls);

        // Summary chips.
        JPanel chips = new JPanel();
        chips.setLayout(new BoxLayout(chips, BoxLayout.X_AXIS));
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        chips.add(mBlocksChip);
        chips.add(Box.createHorizontalStrut(6));
        chips.add(mTypesChip);
        chips.add(Box.createHorizontalStrut(6));
        chips.add(mHpChip);
        chips.add(Box.createHorizontalGlue());
        // Seed the pills with representative HTML content before measuring: an empty
        // chip is much shorter than a filled one, and clamping the row's max height to
        // the empty measurement clips the pills once rebuild() fills them in.
        mBlocksChip.set(0, "blocks");
        mTypesChip.set(0, "types");
        mHpChip.set(0, "HP");
        chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, chips.getPreferredSize().height));
        header.add(chips);

        // Filter row.
        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel filterIcon = new JLabel("🔍"); // magnifier
        filterIcon.setForeground(muted());
        mFilter.putClientProperty("JTextField.placeholderText", "Filter rows…");
        filterRow.add(filterIcon, BorderLayout.WEST);
        filterRow.add(mFilter, BorderLayout.CENTER);
        JButton clear = new JButton("Clear");
        clear.setToolTipText("Clear the selection");
        filterRow.add(clear, BorderLayout.EAST);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterRow.getPreferredSize().height));
        header.add(filterRow);

        mModeBox.addActionListener(e -> {
            if (mSyncingMode) {
                return;
            }
            SelectionModel sel = StarMadeLogic.getInstance().getSelection();
            SelectionModel.Mode m = (SelectionModel.Mode) mModeBox.getSelectedItem();
            sel.setMode(m);
            // "Whole Entity" applies immediately; the others wait for a click.
            if (m == SelectionModel.Mode.ENTITY) {
                sel.selectEntity(StarMadeLogic.getModel());
            }
        });
        mDetailBox.addActionListener(e -> rebuild());
        mFilter.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));
        clear.addActionListener(e -> StarMadeLogic.getInstance().getSelection().clear());

        return header;
    }

    private static void addControlRow(JPanel grid, GridBagConstraints gc, int row, String label, Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label.toUpperCase());
        l.setForeground(muted());
        l.setFont(l.getFont().deriveFont(l.getFont().getSize() - 1f));
        grid.add(l, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        grid.add(field, gc);
    }

    private void syncModeBox(SelectionModel.Mode mode) {
        mSyncingMode = true;
        mModeBox.setSelectedItem(mode);
        mSyncingMode = false;
    }

    /** Rebuilds the table from the current selection, group and detail settings. */
    private void rebuild() {
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        syncModeBox(sel.getMode());
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        List<Point3i> selected = sel.getSelected();
        boolean full = mDetailBox.getSelectedIndex() == 1;

        String[] cols = full
                ? new String[]{"Pos", "ID", "Name", "Orient", "Style", "Slab", "Transp", "Sides", "HP", "Active", "Face Tex"}
                : new String[]{"Pos", "ID", "Name", "Orient", "Active"};
        List<Object[]> rows = perBlockRows(selected, grid, full);

        DefaultTableModel dm = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (Object[] row : rows) {
            dm.addRow(row);
        }
        mTable.setModel(dm);
        mTable.setRowSorter(new TableRowSorter<>(dm));
        installNameDot();
        applyFilter();
        packColumns();

        int types = distinctTypeCount(selected, grid);
        long hp = totalHp(selected, grid);
        mBlocksChip.set(selected.size(), selected.size() == 1 ? "block" : "blocks");
        mTypesChip.set(types, types == 1 ? "type" : "types");
        mHpChip.set(hp, "HP");
    }

    /** Attaches the colour-dot renderer to the Name column (if present). */
    private void installNameDot() {
        int nameCol = columnIndex("Name");
        if (nameCol >= 0) {
            mTable.getColumnModel().getColumn(nameCol).setCellRenderer(new ColorDotRenderer());
        }
    }

    private int columnIndex(String header) {
        for (int c = 0; c < mTable.getColumnCount(); c++) {
            if (header.equals(mTable.getColumnName(c))) {
                return c;
            }
        }
        return -1;
    }

    private List<Object[]> perBlockRows(List<Point3i> selected, SparseMatrix<Block> grid, boolean full) {
        List<Object[]> rows = new ArrayList<>();
        if (grid == null) {
            return rows;
        }
        for (Point3i p : selected) {
            Block b = grid.get(p);
            if (b == null) {
                continue;
            }
            short id = b.getBlockID();
            String pos = "(" + p.x + ", " + p.y + ", " + p.z + ")";
            String name = BlockGroups.BLOCK_NAMES.getOrDefault(id, "?");
            if (full) {
                rows.add(new Object[]{pos, id, name, (int) b.getOrientation(),
                    styleName(BlockTypeColors.getBlockStyle(id)), BlockTypeColors.getBlockSlab(id),
                    yesNo(BlockTypeColors.isTransparent(id)),
                    BlockTypeColors.BLOCK_INDIVIDUAL_SIDES.getOrDefault(id, 1),
                    (int) b.getHitPoints(), yesNo(b.isActive()), faceTex(id)});
            } else {
                rows.add(new Object[]{pos, id, name, (int) b.getOrientation(), yesNo(b.isActive())});
            }
        }
        return rows;
    }

    private int distinctTypeCount(List<Point3i> selected, SparseMatrix<Block> grid) {
        if (grid == null) {
            return 0;
        }
        java.util.Set<Short> ids = new java.util.HashSet<>();
        for (Point3i p : selected) {
            Block b = grid.get(p);
            if (b != null) {
                ids.add(b.getBlockID());
            }
        }
        return ids.size();
    }

    private long totalHp(List<Point3i> selected, SparseMatrix<Block> grid) {
        if (grid == null) {
            return 0;
        }
        long hp = 0;
        for (Point3i p : selected) {
            Block b = grid.get(p);
            if (b != null) {
                hp += b.getHitPoints();
            }
        }
        return hp;
    }

    /**
     * Sizes each column to fit its header and cells (up to a cap; overflow scrolls
     * horizontally). Columns stay user-resizable — this just gives a readable
     * starting width so the full name / face-texture list isn't truncated.
     */
    private void packColumns() {
        for (int col = 0; col < mTable.getColumnCount(); col++) {
            TableColumn column = mTable.getColumnModel().getColumn(col);
            TableCellRenderer hr = column.getHeaderRenderer();
            if (hr == null) {
                hr = mTable.getTableHeader().getDefaultRenderer();
            }
            int width = hr.getTableCellRendererComponent(mTable, column.getHeaderValue(), false, false, -1, col)
                    .getPreferredSize().width;
            for (int row = 0; row < mTable.getRowCount(); row++) {
                Component c = mTable.prepareRenderer(mTable.getCellRenderer(row, col), row, col);
                width = Math.max(width, c.getPreferredSize().width);
            }
            width += 14; // breathing room (+ dot for the Name column)
            column.setPreferredWidth(Math.min(width, 600));
        }
    }

    private void applyFilter() {
        RowSorter<?> rs = mTable.getRowSorter();
        if (!(rs instanceof TableRowSorter)) {
            return;
        }
        @SuppressWarnings("unchecked")
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) rs;
        String txt = mFilter.getText().trim();
        if (txt.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            try {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(txt)));
            } catch (RuntimeException ex) {
                sorter.setRowFilter(null);
            }
        }
    }

    private static String styleName(int style) {
        switch (style) {
            case BlockTypeColors.STYLE_NORMAL: return "Normal";
            case BlockTypeColors.STYLE_WEDGE: return "Wedge";
            case BlockTypeColors.STYLE_CORNER: return "Corner";
            case BlockTypeColors.STYLE_SPRITE: return "Sprite";
            case BlockTypeColors.STYLE_TETRA: return "Tetra";
            case BlockTypeColors.STYLE_HEPTA: return "Hepta";
            case BlockTypeColors.STYLE_NORMAL24: return "Normal24";
            default: return String.valueOf(style);
        }
    }

    private static String faceTex(short id) {
        short[] f = BlockTypeColors.BLOCK_TEXTURE_IDS_PER_FACE.get(id);
        if (f == null) {
            Integer t = BlockTypeColors.BLOCK_TEXTURE_IDS.get(id);
            return t == null ? "" : String.valueOf(t);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(f[i]);
        }
        return sb.toString();
    }

    private static String yesNo(boolean b) {
        return b ? "Yes" : "";
    }

    private static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(0x88, 0x8D, 0x98);
    }

    private static Color hairline() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : new Color(0x3A, 0x3D, 0x41);
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setForeground(muted());
        l.setFont(l.getFont().deriveFont(l.getFont().getSize() - 1f));
        l.setBorder(BorderFactory.createEmptyBorder(2, 1, 4, 0));
        l.setHorizontalAlignment(SwingConstants.LEFT);
        return l;
    }

    /** A rounded summary pill like the prototype's chips: bold value + muted unit. */
    private static final class Chip extends JLabel {
        Chip() {
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            setForeground(muted());
        }

        void set(long value, String unit) {
            setText("<html><b>" + String.format("%,d", value) + "</b> " + unit + "</html>");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hairline());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    /** Small rounded colour square drawn before the block name. */
    private static final class SquareIcon implements Icon {
        private final Color color;

        SquareIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, 10, 10, 3, 3);
            g2.setColor(hairline());
            g2.drawRoundRect(x, y, 10, 10, 3, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    /** Name-column renderer that prefixes each row with the block type's colour. */
    private static final class ColorDotRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            Object idVal = null;
            for (int c = 0; c < table.getColumnCount(); c++) {
                if ("ID".equals(table.getColumnName(c))) {
                    idVal = table.getValueAt(row, c);
                    break;
                }
            }
            if (idVal instanceof Number) {
                setIcon(new SquareIcon(BlockTypeColors.getFillColor(((Number) idVal).shortValue())));
            } else {
                setIcon(null);
            }
            return this;
        }
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
