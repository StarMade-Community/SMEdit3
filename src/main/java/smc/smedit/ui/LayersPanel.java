package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.swing.FontIcon;

import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.LayerModel;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

/**
 * Dockable panel that lists the model's visibility layers. Auto layers are one
 * per block category; the user can also add custom layers from the current
 * selection. Each row has an eye toggle (hides the layer's blocks in the
 * viewport), a colour chip, a name and a block count. Clicking a row selects the
 * layer's blocks (feeding the Inspector).
 *
 * <p>Rebuilds its rows on any {@link LayerModel} change and whenever a new model
 * is loaded; toggling a layer triggers a viewport mesh rebuild.
 */
@SuppressWarnings("serial")
public class LayersPanel extends JPanel implements LayerModel.Listener {

    private static final int ICON = 15;

    private final RenderPanel mRenderer;
    private final JPanel mList = new JPanel();

    public LayersPanel(RenderPanel renderer) {
        super(new BorderLayout(0, 6));
        this.mRenderer = renderer;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setMinimumSize(new Dimension(150, 0));

        add(buildHeader(), BorderLayout.NORTH);

        mList.setLayout(new BoxLayout(mList, BoxLayout.Y_AXIS));
        JPanel listHost = new JPanel(new BorderLayout());
        listHost.add(mList, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listHost,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        LayerModel layers = layers();
        layers.addListener(this);
        // A freshly loaded model reshapes the layers — rebuild then.
        StarMadeLogic.getInstance().addPropertyChangeListener("model", ev ->
                SwingUtilities.invokeLater(() -> {
                    layers().rebuildFromModel(StarMadeLogic.getModel());
                    rebuildRows();
                }));

        layers.rebuildFromModel(StarMadeLogic.getModel());
        rebuildRows();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = sectionLabel("Layers");
        header.add(title);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton addBtn = flatButton(Feather.PLUS, "New layer from selection");
        addBtn.addActionListener(e -> addFromSelection());
        JButton showAllBtn = flatButton(Feather.EYE, "Show all layers");
        showAllBtn.addActionListener(e -> {
            layers().showAll();
            refreshViewport();
        });
        bar.add(addBtn);
        bar.add(showAllBtn);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        header.add(bar);
        return header;
    }

    /** Rebuilds the row list from the current layer model. */
    private void rebuildRows() {
        mList.removeAll();
        List<LayerModel.Layer> all = layers().getLayers();
        if (all.isEmpty()) {
            JLabel empty = new JLabel("No blocks loaded.");
            empty.setForeground(muted());
            empty.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            mList.add(empty);
        } else {
            for (LayerModel.Layer layer : all) {
                mList.add(new LayerRow(layer));
                mList.add(Box.createVerticalStrut(5));
            }
        }
        mList.revalidate();
        mList.repaint();
    }

    /** Creates a custom layer from the distinct block types in the selection. */
    private void addFromSelection() {
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        Set<Short> ids = new LinkedHashSet<>();
        if (grid != null) {
            for (Point3i p : sel.getSelected()) {
                Block b = grid.get(p);
                if (b != null) {
                    ids.add(b.getBlockID());
                }
            }
        }
        if (ids.isEmpty()) {
            return; // nothing selected — no-op
        }
        layers().addCustomLayer(null, ids, grid);
        // rebuildRows() runs via the model listener.
    }

    /** Selects every block belonging to a layer, driving the Inspector. */
    private void selectLayer(LayerModel.Layer layer) {
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid == null) {
            return;
        }
        Set<Short> ids = layer.getBlockIds();
        List<Point3i> cells = new ArrayList<>();
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Point3i p = it.next();
            Block b = grid.get(p);
            if (b != null && ids.contains(b.getBlockID())) {
                cells.add(p);
            }
        }
        StarMadeLogic.getInstance().getSelection().select(cells);
    }

    private void refreshViewport() {
        if (mRenderer != null) {
            mRenderer.updateTiles();
        }
    }

    @Override
    public void layersChanged() {
        SwingUtilities.invokeLater(this::rebuildRows);
    }

    // ---- one row ----

    private final class LayerRow extends JPanel {
        LayerRow(LayerModel.Layer layer) {
            super(new BorderLayout(9, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(hairline()),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 16));

            JButton eye = flatButton(layer.isVisible() ? Feather.EYE : Feather.EYE_OFF,
                    layer.isVisible() ? "Hide layer" : "Show layer");
            eye.addActionListener(e -> {
                layers().toggle(layer);
                refreshViewport();
            });

            JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            west.setOpaque(false);
            west.add(eye);
            west.add(new ColorChip(layer.getColor()));
            add(west, BorderLayout.WEST);

            JLabel name = new JLabel(layer.getName());
            if (!layer.isVisible()) {
                name.setForeground(muted());
            }
            add(name, BorderLayout.CENTER);

            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            east.setOpaque(false);
            JLabel count = new JLabel(String.format("%,d", layer.getCount()));
            count.setForeground(muted());
            count.setFont(monoish(count));
            east.add(count);
            if (layer.isCustom()) {
                JButton del = flatButton(Feather.X, "Remove layer");
                del.addActionListener(e -> {
                    layers().removeLayer(layer);
                    refreshViewport();
                });
                east.add(del);
            }
            add(east, BorderLayout.EAST);

            // Click the row body (not the buttons) to select the layer's blocks.
            java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    selectLayer(layer);
                }
            };
            addMouseListener(click);
            name.addMouseListener(click);
        }
    }

    /** A small rounded colour square. */
    private static final class ColorChip extends JComponent {
        private final Color color;

        ColorChip(Color color) {
            this.color = color != null ? color : Color.GRAY;
            setPreferredSize(new Dimension(11, 11));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, 10, 10, 3, 3);
            g2.setColor(hairline());
            g2.drawRoundRect(0, 0, 10, 10, 3, 3);
            g2.dispose();
        }
    }

    // ---- helpers ----

    private static LayerModel layers() {
        return StarMadeLogic.getInstance().getLayers();
    }

    private static JButton flatButton(Feather glyph, String tip) {
        JButton b = new JButton(icon(glyph));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(2, 4, 2, 4));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        return b;
    }

    private static Icon icon(Feather glyph) {
        return FontIcon.of(glyph, ICON, iconColor());
    }

    private static Color iconColor() {
        Color fg = UIManager.getColor("Button.foreground");
        return fg != null ? fg : new Color(0xB8, 0xB8, 0xB8);
    }

    private static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(0x88, 0x8D, 0x98);
    }

    private static Color hairline() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : new Color(0x3A, 0x3D, 0x41);
    }

    private static java.awt.Font monoish(Component c) {
        return new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, c.getFont().getSize());
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
}
