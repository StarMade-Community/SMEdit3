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
package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.tool.EditorTool;
import smc.smedit.ui.tool.ToolController;
import smc.smedit.vecmath.Point3i;


public class StatusPanel extends JPanel {

    /** Right-aligned slot in the status bar (e.g. the memory-usage bar). */
    private final JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    /** Non-modal loading indicator (left of the status bar); hidden when idle. */
    private final JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel loadingLabel = new JLabel();
    private final JProgressBar loadingBar = new JProgressBar();

    /** Grouped, thousands-separated integers (e.g. {@code 1,024}). */
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance();
    /** Pixel size of the active-block swatch shown in the block stat. */
    private static final int STAT_ICON = 12;

    // Live-cursor stats (left cluster).
    private final JLabel coordLabel = statLabel();
    private final JLabel hoverLabel = statLabel();
    private final JLabel toolLabel = statLabel();
    private final JLabel blockLabel = statLabel();
    // Aggregate model stats (right cluster).
    private final JLabel blocksLabel = statLabel();
    private final JLabel typesLabel = statLabel();
    private final JLabel dimLabel = statLabel();

    /**
     * A single thin status row. Left: the live-cursor readout (hovered x/y/z, active
     * tool, active block) and the loading indicator. Right: the aggregate model stats
     * (block count, type count, dimensions), the memory-usage bar and the window
     * resize grip. Application logging goes to the Console panel.
     */
    public StatusPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 2));

        loadingBar.setIndeterminate(true);
        loadingBar.setPreferredSize(new Dimension(150, 14));
        loadingPanel.add(loadingLabel);
        loadingPanel.add(loadingBar);
        loadingPanel.setVisible(false);

        JPanel leftStats = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftStats.setOpaque(false);
        leftStats.add(coordLabel);
        leftStats.add(sep());
        leftStats.add(hoverLabel);
        leftStats.add(sep());
        leftStats.add(toolLabel);
        leftStats.add(sep());
        leftStats.add(blockLabel);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(leftStats);
        left.add(loadingPanel);
        add(left, BorderLayout.WEST);

        JPanel rightStats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightStats.setOpaque(false);
        rightStats.add(blocksLabel);
        rightStats.add(sep());
        rightStats.add(typesLabel);
        rightStats.add(sep());
        rightStats.add(dimLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(rightStats);
        right.add(rightPanel);
        right.add(new JLabel(new TriangleSquareWindowsCornerIcon()));
        add(right, BorderLayout.EAST);

        wireStats();
    }

    /** Adds a component to the bottom-right of the status bar (left of the resize grip). */
    public void addRightComponent(java.awt.Component c) {
        rightPanel.add(c);
        rightPanel.revalidate();
    }

    /** Shows the non-modal loading indicator with the given text. Call on the EDT. */
    public void showLoading(String text) {
        loadingLabel.setText(text);
        loadingPanel.setVisible(true);
        loadingPanel.revalidate();
        loadingPanel.repaint();
    }

    /** Updates the loading indicator's text (e.g. from a progress callback). Call on the EDT. */
    public void setLoadingText(String text) {
        loadingLabel.setText(text);
    }

    /** Hides the loading indicator. Call on the EDT. */
    public void hideLoading() {
        loadingPanel.setVisible(false);
        loadingPanel.revalidate();
        loadingPanel.repaint();
    }

    // ------------------------------------------------------------------
    // Live stats
    // ------------------------------------------------------------------

    /**
     * Subscribes the stat labels to their data sources and seeds their initial
     * values: the active tool / block from {@link ToolController}, the hovered cell
     * from {@link CursorStatus}, and the aggregate counts from the loaded model
     * (refreshed on load / import and after each edit stroke).
     */
    private void wireStats() {
        updateTool(ToolController.get().getActive());
        updateBlock();
        updateCursor(null);
        refreshModelStats();

        ToolController.get().addListener(new ToolController.Listener() {
            @Override
            public void toolChanged(EditorTool tool) {
                onEdt(() -> updateTool(tool));
            }

            @Override
            public void activeBlockChanged(short blockId) {
                onEdt(StatusPanel.this::updateBlock);
            }

            @Override
            public void modelEdited() {
                refreshModelStats();
            }
        });
        CursorStatus.get().addListener(cell -> onEdt(() -> updateCursor(cell)));
        StarMadeLogic.getInstance().addPropertyChangeListener("model", evt -> refreshModelStats());
    }

    private void updateTool(EditorTool tool) {
        toolLabel.setText(tool.getDisplayName() + " tool");
    }

    /** Block stat: the swatch + name of the <em>effective</em> active block. */
    private void updateBlock() {
        short base = ToolController.get().getActiveBlockType();
        if (base < 0) {
            blockLabel.setIcon(null);
            blockLabel.setText("no block");
            return;
        }
        short eff = ToolController.get().getEffectiveBlockType();
        blockLabel.setIcon(BlockIcons.icon(eff, STAT_ICON));
        blockLabel.setText(BlockIcons.name(eff));
    }

    private void updateCursor(Point3i cell) {
        if (cell == null) {
            coordLabel.setText(coordText("—", "—", "—"));
            hoverLabel.setIcon(null);
            hoverLabel.setText("—");
            return;
        }
        coordLabel.setText(coordText(
                String.valueOf(cell.x), String.valueOf(cell.y), String.valueOf(cell.z)));
        // The pick lands on a solid block, so look up its type at that cell.
        SparseMatrix<Block> model = StarMadeLogic.getModel();
        Block b = model == null ? null : model.get(cell);
        if (b == null) {
            hoverLabel.setIcon(null);
            hoverLabel.setText("—");
        } else {
            short id = b.getBlockID();
            hoverLabel.setIcon(BlockIcons.icon(id, STAT_ICON));
            hoverLabel.setText(BlockIcons.name(id));
        }
    }

    /**
     * Recomputes the block count, distinct-type count and bounding-box dimensions
     * from the current model. Iterates every block, so it runs off the caller's
     * thread (the "model" change fires on the load thread) and only touches the
     * labels back on the EDT.
     */
    private void refreshModelStats() {
        SparseMatrix<Block> model = StarMadeLogic.getModel();
        final String blocksText;
        final String typesText;
        final String dimText;
        if (model == null || model.size() == 0) {
            blocksText = "0 blocks";
            typesText = "0 types";
            dimText = "dim —";
        } else {
            Set<Short> types = new HashSet<>();
            for (Iterator<Point3i> it = model.iteratorNonNull(); it.hasNext();) {
                Block b = model.get(it.next());
                if (b != null) {
                    types.add(b.getBlockID());
                }
            }
            Point3i lower = new Point3i();
            Point3i upper = new Point3i();
            model.getBounds(lower, upper);
            int dx = upper.x - lower.x + 1;
            int dy = upper.y - lower.y + 1;
            int dz = upper.z - lower.z + 1;
            blocksText = NUM.format(model.size()) + " blocks";
            typesText = NUM.format(types.size()) + " types";
            dimText = "dim " + dx + "×" + dy + "×" + dz;
        }
        onEdt(() -> {
            blocksLabel.setText(blocksText);
            typesLabel.setText(typesText);
            dimLabel.setText(dimText);
        });
    }

    /** Coordinate readout with accent-coloured values (matching the tool palette accent). */
    private static String coordText(String x, String y, String z) {
        String a = accentHex();
        return "<html>x <b><font color='" + a + "'>" + x
                + "</font></b>  y <b><font color='" + a + "'>" + y
                + "</font></b>  z <b><font color='" + a + "'>" + z + "</font></b></html>";
    }

    // ------------------------------------------------------------------
    // Styling helpers
    // ------------------------------------------------------------------

    /** A muted, monospaced stat label. */
    private static JLabel statLabel() {
        JLabel l = new JLabel();
        l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        Color fg = UIManager.getColor("Label.disabledForeground");
        if (fg != null) {
            l.setForeground(fg);
        }
        return l;
    }

    /** A thin vertical divider sized to the status row. */
    private static JComponent sep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 14));
        return s;
    }

    /** The current theme's accent colour as a {@code #rrggbb} string. */
    private static String accentHex() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) {
            c = UIManager.getColor("Component.focusColor");
        }
        if (c == null) {
            c = new Color(0x4d, 0x7c, 0xff);
        }
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Runs {@code r} on the EDT (now if already there, else queued). */
    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
