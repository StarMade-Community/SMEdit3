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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.swing.FontIcon;

import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.mods.IRunnableWithProgress;
import smc.smedit.scene.Scene;
import smc.smedit.scene.SceneGroup;
import smc.smedit.scene.SceneLogic;
import smc.smedit.scene.SceneModel;
import smc.smedit.scene.SceneObject;
import smc.smedit.ui.act.file.SceneIO;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.logic.ShipSpec;
import smc.smedit.ui.logic.ShipTreeLogic;

/**
 * Blender-style outliner for the live {@link SceneModel}. Lists the scene's
 * objects (optionally under group folders), each with an eye toggle, an active
 * indicator and a "⋮" action menu (rename, move to group, dock, export, delete).
 * A header toolbar imports a blueprint as a new object and opens/saves the whole
 * {@code .smedit} scene.
 *
 * <p>Clicking an object activates it: its grid becomes the editable document and
 * the viewport re-frames on it, while every other visible object renders around it
 * as context (see {@code LWJGLRenderPanel.updateTiles}).
 */
@SuppressWarnings("serial")
public class ScenePanel extends JPanel implements SceneModel.Listener {

    private static final int ICON = 15;

    private final RenderFrame mFrame;
    private final JPanel mList = new JPanel();

    public ScenePanel(RenderFrame frame) {
        super(new BorderLayout(0, 6));
        this.mFrame = frame;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setMinimumSize(new Dimension(160, 0));

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

        model().addListener(this);
        // A freshly loaded model may create/replace the active object.
        StarMadeLogic.getInstance().addPropertyChangeListener("model", ev ->
                SwingUtilities.invokeLater(this::rebuild));
        rebuild();
    }

    // ---- header toolbar ----

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(sectionLabel("Scene"));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
//        bar.add(textButton("Import", "Import a blueprint as a new object", e -> importBlueprint()));
        bar.add(textButton("Group", "New empty group", e -> newGroup()));
        bar.add(textButton("Ungroup", "Ungroup objects", e -> unGroup()));
//        bar.add(textButton("Open", "Open a .smedit scene", e -> SceneIO.open(mFrame)));
        bar.add(textButton("Save", "Save the scene as .smedit", e -> SceneIO.save(mFrame)));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        header.add(bar);
        return header;
    }

    // ---- row list ----

    private void rebuild() {
        mList.removeAll();
        Scene scene = model().getScene();
        if (scene == null || scene.getObjects().isEmpty()) {
            JLabel empty = new JLabel("No objects. Open a blueprint or Import one.");
            empty.setForeground(muted());
            empty.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            mList.add(empty);
        } else {
            for (SceneGroup g : scene.getGroups()) {
                mList.add(new GroupRow(g));
                mList.add(Box.createVerticalStrut(3));
                for (SceneObject o : model().objectsInGroup(g)) {
                    mList.add(new ObjectRow(o, true));
                    mList.add(Box.createVerticalStrut(4));
                }
            }
            for (SceneObject o : model().ungroupedObjects()) {
                mList.add(new ObjectRow(o, false));
                mList.add(Box.createVerticalStrut(4));
            }
        }
        mList.revalidate();
        mList.repaint();
    }

    @Override
    public void sceneChanged() {
        SwingUtilities.invokeLater(() -> {
            rebuild();
            refreshViewport();
        });
    }

    // ---- one object row ----

    private final class ObjectRow extends JPanel {
        ObjectRow(SceneObject o, boolean grouped) {
            super(new BorderLayout(9, 0));
            boolean active = model().isActive(o);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? accent() : hairline()),
                    BorderFactory.createEmptyBorder(6, grouped ? 18 : 8, 6, 8)));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 16));

            JButton eye = flatIcon(o.isVisible() ? Feather.EYE : Feather.EYE_OFF,
                    o.isVisible() ? "Hide object" : "Show object");
            eye.addActionListener(e -> model().setVisible(o, !o.isVisible()));

            JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            west.setOpaque(false);
            west.add(eye);
            west.add(new Bullet(active));
            add(west, BorderLayout.WEST);

            JLabel name = new JLabel(o.getName());
            if (active) {
                name.setFont(name.getFont().deriveFont(Font.BOLD));
            }
            if (!o.isVisible()) {
                name.setForeground(muted());
            }
            add(name, BorderLayout.CENTER);

            JButton menu = textButton("⋮", "Object actions", null);
            menu.addActionListener(e -> objectMenu(o).show(menu, 0, menu.getHeight()));
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            east.setOpaque(false);
            east.add(menu);
            add(east, BorderLayout.EAST);

            MouseAdapter click = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        renameObject(o);
                    } else {
                        activate(o);
                    }
                }
            };
            addMouseListener(click);
            name.addMouseListener(click);
        }
    }

    private JPopupMenu objectMenu(SceneObject o) {
        JPopupMenu m = new JPopupMenu();
        m.add(item("Activate", () -> activate(o)));
        m.add(item("Rename…", () -> renameObject(o)));

        JPopupMenu.Separator sep = new JPopupMenu.Separator();
        m.add(sep);

        // Move to group
        javax.swing.JMenu group = new javax.swing.JMenu("Move to group");
        group.add(item("(none)", () -> model().setObjectGroup(o, null)));
        for (SceneGroup g : model().getScene().getGroups()) {
            group.add(item(g.getName(), () -> model().setObjectGroup(o, g)));
        }
        group.addSeparator();
        group.add(item("New group…", () -> {
            SceneGroup g = promptNewGroup();
            if (g != null) {
                model().setObjectGroup(o, g);
            }
        }));
        m.add(group);

        // Dock to another object
        javax.swing.JMenu dock = new javax.swing.JMenu("Dock to");
        dock.add(item("(none)", () -> model().setDockParent(o, null)));
        for (SceneObject other : model().getScene().getObjects()) {
            if (other != o) {
                dock.add(item(other.getName(), () -> model().setDockParent(o, other)));
            }
        }
        m.add(dock);

        m.addSeparator();
        m.add(item("Export…", () -> exportObject(o)));
        m.add(item("Delete", () -> deleteObject(o)));
        return m;
    }

    // ---- one group header row ----

    private final class GroupRow extends JPanel {
        GroupRow(SceneGroup g) {
            super(new BorderLayout(9, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 14));

            JLabel name = new JLabel(g.getName().toUpperCase());
            name.setForeground(muted());
            name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize() - 1f));
            add(name, BorderLayout.CENTER);

            JButton menu = textButton("⋮", "Group actions", null);
            menu.addActionListener(e -> {
                JPopupMenu m = new JPopupMenu();
                m.add(item("Rename…", () -> {
                    String n = JOptionPane.showInputDialog(ScenePanel.this, "Group name:", g.getName());
                    model().renameGroup(g, n);
                }));
                m.add(item("Remove group", () -> model().removeGroup(g)));
                m.show(menu, 0, menu.getHeight());
            });
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            east.setOpaque(false);
            east.add(menu);
            add(east, BorderLayout.EAST);
        }
    }

    // ---- actions ----

    private void activate(SceneObject o) {
        model().setActive(o);
        if (mFrame.getClient() != null && mFrame.getClient().getUndoer() != null) {
            mFrame.getClient().getUndoer().clear();
        }
    }

    private void renameObject(SceneObject o) {
        String n = JOptionPane.showInputDialog(this, "Object name:", o.getName());
        model().renameObject(o, n);
    }

    private void deleteObject(SceneObject o) {
        int r = JOptionPane.showConfirmDialog(this, "Remove \"" + o.getName() + "\" from the scene?",
                "Delete object", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            model().removeObject(o);
        }
    }

    private void newGroup() {
        promptNewGroup();
    }

    /**
     * Dissolves a group, leaving its objects in the scene (ungrouped). Targets the
     * group holding the active object; if that object isn't grouped, ungroups the
     * scene's only group, or asks which one when there are several. Removing a single
     * object from a group is done from the object's own menu (Move to group → none).
     */
    private void unGroup() {
        java.util.List<SceneGroup> groups = model().getScene().getGroups();
        if (groups.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no groups to ungroup.",
                    "Ungroup", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SceneObject active = model().getActiveObject();
        SceneGroup target = active != null ? model().groupOf(active) : null;
        if (target == null) {
            target = groups.size() == 1 ? groups.get(0) : chooseGroup("Ungroup which group?");
        }
        if (target != null) {
            model().removeGroup(target);
        }
    }

    /** Prompts the user to pick one of the scene's groups; {@code null} if cancelled. */
    private SceneGroup chooseGroup(String prompt) {
        java.util.List<SceneGroup> groups = model().getScene().getGroups();
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            names[i] = groups.get(i).getName();
        }
        String chosen = (String) JOptionPane.showInputDialog(this, prompt, "Ungroup",
                JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
        if (chosen == null) {
            return null;
        }
        for (SceneGroup g : groups) {
            if (chosen.equals(g.getName())) {
                return g;
            }
        }
        return null;
    }

    private SceneGroup promptNewGroup() {
        String n = JOptionPane.showInputDialog(this, "Group name:", "");
        if (n == null) {
            return null;
        }
        return model().newGroup(n);
    }

    private void importBlueprint() {
        ShipChooser chooser = new ShipChooser(mFrame);
        chooser.setVisible(true);
        final ShipSpec spec = chooser.getSelected();
        if (spec == null) {
            return;
        }
        mFrame.loadInBackground("Importing " + spec.getName() + "…", new IRunnableWithProgress() {
            @Override
            public void run(IPluginCallback cb) {
                SparseMatrix<Block> grid = ShipTreeLogic.loadShip(spec, cb);
                if (grid != null) {
                    SwingUtilities.invokeLater(() -> model().importObject(spec.getName(), grid));
                }
            }
        });
    }

    private void exportObject(SceneObject o) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Export \"" + o.getName() + "\" as a blueprint (choose parent folder)");
        String dir = StarMadeLogic.getProps().getProperty("open.scene.dir", "");
        if (!dir.isEmpty()) {
            fc.setCurrentDirectory(new File(dir));
        }
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File parent = fc.getSelectedFile();
        final File target = new File(parent, safeName(o.getName()));
        mFrame.loadInBackground("Exporting " + o.getName() + "…", new IRunnableWithProgress() {
            @Override
            public void run(IPluginCallback cb) {
                try {
                    target.mkdirs();
                    SceneLogic.writeObjectAsBlueprint(o, target);
                } catch (Exception ex) {
                    error("Could not export object", ex);
                }
            }
        });
    }

    // ---- helpers ----

    private static SceneModel model() {
        return StarMadeLogic.getInstance().getSceneModel();
    }

    private void refreshViewport() {
        if (mFrame.getClient() != null) {
            mFrame.getClient().updateTiles();
        }
    }

    private void error(String msg, Throwable ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                msg + ":\n" + ex.getMessage(), "Scene", JOptionPane.ERROR_MESSAGE));
    }

    private static String safeName(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "Object" : s;
    }

    private JMenuItem item(String text, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(e -> action.run());
        return mi;
    }

    private JButton textButton(String text, String tip, java.awt.event.ActionListener onClick) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        if (onClick != null) {
            b.addActionListener(onClick);
        }
        return b;
    }

    private JButton flatIcon(Feather glyph, String tip) {
        JButton b = new JButton(FontIcon.of(glyph, ICON, iconColor()));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 4, 2, 4));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        return b;
    }

    /** A small filled/hollow dot marking the active object. */
    private static final class Bullet extends JComponent {
        private final boolean filled;

        Bullet(boolean filled) {
            this.filled = filled;
            setPreferredSize(new Dimension(10, 10));
            setToolTipText(filled ? "Active object" : null);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(filled ? accent() : hairline());
            if (filled) {
                g2.fillOval(1, 1, 8, 8);
            } else {
                g2.drawOval(1, 1, 8, 8);
            }
            g2.dispose();
        }
    }

    private static Color iconColor() {
        Color fg = UIManager.getColor("Button.foreground");
        return fg != null ? fg : new Color(0xB8, 0xB8, 0xB8);
    }

    private static Color accent() {
        Color c = UIManager.getColor("Component.focusColor");
        if (c == null) {
            c = UIManager.getColor("Component.accentColor");
        }
        return c != null ? c : new Color(0x4A, 0x90, 0xD9);
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
}
