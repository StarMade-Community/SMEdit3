package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import smc.smedit.data.BlockTypes;
import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.vecmath.Point3i;

/**
 * Dockable panel that lists data for the current block selection. The selection
 * mode drives what a viewport click selects; the view toggle switches between a
 * per-block table and a by-type summary; the detail control widens the columns;
 * the filter narrows visible rows.
 */
@SuppressWarnings("serial")
public class BlockInfoPanel extends JPanel {

    private final JComboBox<SelectionModel.Mode> mModeBox = new JComboBox<>(SelectionModel.Mode.values());
    private final JComboBox<String> mViewBox = new JComboBox<>(new String[]{"Per-block", "By type"});
    private final JComboBox<String> mDetailBox = new JComboBox<>(new String[]{"Basic", "Full"});
    private final JTextField mFilter = new JTextField(12);
    private final JLabel mSummary = new JLabel(" ");
    private final JTable mTable = new JTable();
    private boolean mSyncingMode;

    public BlockInfoPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        buildControls();
        mTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        add(new JScrollPane(mTable), BorderLayout.CENTER);
        add(mSummary, BorderLayout.SOUTH);

        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        syncModeBox(sel.getMode());
        // Selection changes may fire off the render thread — marshal to the EDT.
        sel.addListener(() -> SwingUtilities.invokeLater(this::rebuild));
        rebuild();
    }

    private void buildControls() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        top.add(new JLabel("Mode:"));
        top.add(mModeBox);
        top.add(new JLabel("View:"));
        top.add(mViewBox);
        top.add(new JLabel("Detail:"));
        top.add(mDetailBox);
        top.add(new JLabel("Filter:"));
        top.add(mFilter);
        JButton clear = new JButton("Clear");
        top.add(clear);
        add(top, BorderLayout.NORTH);

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
        mViewBox.addActionListener(e -> rebuild());
        mDetailBox.addActionListener(e -> rebuild());
        mFilter.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));
        clear.addActionListener(e -> StarMadeLogic.getInstance().getSelection().clear());
    }

    private void syncModeBox(SelectionModel.Mode mode) {
        mSyncingMode = true;
        mModeBox.setSelectedItem(mode);
        mSyncingMode = false;
    }

    /** Rebuilds the table from the current selection, view and detail settings. */
    private void rebuild() {
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        syncModeBox(sel.getMode());
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        List<Point3i> selected = sel.getSelected();
        boolean byType = mViewBox.getSelectedIndex() == 1;
        boolean full = mDetailBox.getSelectedIndex() == 1;

        String[] cols;
        List<Object[]> rows;
        if (byType) {
            cols = full
                    ? new String[]{"ID", "Name", "Count", "Style", "Transp", "Sides", "Total HP"}
                    : new String[]{"ID", "Name", "Count"};
            rows = byTypeRows(selected, grid, full);
        } else {
            cols = full
                    ? new String[]{"Pos", "ID", "Name", "Orient", "Style", "Slab", "Transp", "Sides", "HP", "Active", "Face Tex"}
                    : new String[]{"Pos", "ID", "Name", "Orient", "Active"};
            rows = perBlockRows(selected, grid, full);
        }

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
        applyFilter();
        packColumns();

        int types = distinctTypeCount(selected, grid);
        mSummary.setText(selected.size() + " block" + (selected.size() == 1 ? "" : "s")
                + ", " + types + " type" + (types == 1 ? "" : "s"));
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
            String name = BlockTypes.BLOCK_NAMES.getOrDefault(id, "?");
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

    private List<Object[]> byTypeRows(List<Point3i> selected, SparseMatrix<Block> grid, boolean full) {
        List<Object[]> rows = new ArrayList<>();
        if (grid == null) {
            return rows;
        }
        // id -> [count, total HP], preserving first-seen order.
        Map<Short, int[]> agg = new LinkedHashMap<>();
        for (Point3i p : selected) {
            Block b = grid.get(p);
            if (b == null) {
                continue;
            }
            int[] v = agg.computeIfAbsent(b.getBlockID(), k -> new int[2]);
            v[0]++;
            v[1] += b.getHitPoints();
        }
        for (Map.Entry<Short, int[]> e : agg.entrySet()) {
            short id = e.getKey();
            String name = BlockTypes.BLOCK_NAMES.getOrDefault(id, "?");
            if (full) {
                rows.add(new Object[]{id, name, e.getValue()[0],
                    styleName(BlockTypeColors.getBlockStyle(id)),
                    yesNo(BlockTypeColors.isTransparent(id)),
                    BlockTypeColors.BLOCK_INDIVIDUAL_SIDES.getOrDefault(id, 1),
                    e.getValue()[1]});
            } else {
                rows.add(new Object[]{id, name, e.getValue()[0]});
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
            width += 12; // breathing room
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
