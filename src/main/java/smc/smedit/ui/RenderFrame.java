/**
 * Copyright 2014 SMEdit 
 * https://github.com/StarMade/SMEdit SMTools
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
 *
 */
package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.border.EmptyBorder;
import smc.smedit.log.TextAreaLogHandler;
import smc.smedit.logic.BlueprintLogic;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.mods.IRunnableWithProgress;
import smc.smedit.ui.act.Shot;
import smc.smedit.ui.act.plugin.BlocksPluginAction;
import smc.smedit.ui.act.edit.RedoAction;
import smc.smedit.ui.act.edit.RedoActionButton;
import smc.smedit.ui.act.edit.UndoAction;
import smc.smedit.ui.act.edit.UndoActionButton;
import smc.smedit.ui.act.file.OpenExistingAction;
import smc.smedit.ui.act.file.OpenExistingAction1;
import smc.smedit.ui.act.file.OpenFileAction;
import smc.smedit.ui.act.file.OpenFileAction1;
import smc.smedit.ui.act.file.QuitAction;
import smc.smedit.ui.act.file.SaveAction;
import smc.smedit.ui.act.file.SaveAsBlueprintAction;
import smc.smedit.ui.act.file.SaveAsBlueprintAction1;
import smc.smedit.ui.act.file.SaveAsFileAction;
import smc.smedit.ui.act.file.SaveAsFileAction1;
import smc.smedit.ui.act.view.AxisAction;
import smc.smedit.ui.act.view.CameraModeAction;
import smc.smedit.ui.act.view.GridAction;
import smc.smedit.ui.logic.ShipSpec;
import smc.smedit.ui.logic.ShipTreeLogic;
import smc.smedit.ui.lwjgl.LWJGLRenderPanel;
import smc.smedit.ui.dock.DockPanel;
import smc.smedit.ui.dock.LayoutPresets;
import smc.smedit.ui.shelf.PluginCategories;
import smc.smedit.ui.shelf.ShelfIcons;
import smc.smedit.ui.tool.EditorTool;
import smc.smedit.ui.tool.ToolController;
import smc.smedit.ui.tool.ToolRail;
import smc.smedit.util.GlobalConfiguration;
import smc.smedit.util.Paths;
import smc.smedit.util.Resources;
import smc.smedit.util.SplashScreen;

import java.io.File;
import java.util.EnumSet;

import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.DockableTabPreference;
import io.github.andrewauclair.moderndocking.settings.Settings;
import io.github.andrewauclair.moderndocking.ui.ToolbarLocation;
import io.github.andrewauclair.moderndocking.app.AppState;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.DockingState;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import io.github.andrewauclair.moderndocking.ext.ui.DockingUI;
import io.github.andrewauclair.moderndocking.layouts.ApplicationLayout;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.swing.FontIcon;

@SuppressWarnings("serial")
public class RenderFrame extends JFrame {

    private static final Logger log = Logger.getLogger(RenderFrame.class.getName());
    private static final String os = System.getProperty("os.name").toLowerCase();
    private static String[] mArgs;
    private static boolean debugLogging = true;

    public static void preLoad() {
        Properties props = StarMadeLogic.getProps();
        String home = props.getProperty("starmade.home", "");
        if (!StarMadeLogic.isStarMadeDirectory(home)) {
            if (StarMadeLogic.isStarMadeDirectory(System.getProperty("user.dir"))) {
                home = System.getProperty("user.dir");
            } else {
                java.io.File chosen = StarMadeDirChooser.choose(null,
                        home.isEmpty() ? null : new java.io.File(home));
                if (chosen == null) {
                    System.exit(0);
                }
                home = chosen.getAbsolutePath();
            }
            props.put("starmade.home", home);
            StarMadeLogic.saveProps();
        }
        StarMadeLogic.setBaseDir(home);
    }

    public static void main(String[] args) {
        preLoad();
        if (os.contains("windows")) {
            SplashScreen splash = new SplashScreen(args);
            if (!splash.error) {
                startup(args);
            }
            splash.close();
        } else if (os.contains("mac")) {
            startup(args);
        } else if (os.contains("linux")) {
            startup(args);

        }
    }

    /**
     * Returns the startup run objects for app startup
     * only (not useful for script writers).
     *
     * @param args
     * the first loaded ship for the client
     */
    public static void startup(String[] args) {
        final RenderFrame f = new RenderFrame(args);
        f.setVisible(true);
        try {
            // Load the first available default blueprint (e.g. an Isanth from the
            // install's blueprints-default) rather than a hardcoded name — the old
            // "Omen-Navy-Class" was an empty auto-created folder that crashed the
            // renderer. Start with an empty editor if none are present.
            final String defaultName = firstDefaultBlueprint();
            if (defaultName != null) {
                final ShipSpec spec = ShipTreeLogic.getBlueprintSpec(defaultName, true);
                IRunnableWithProgress t = cb -> {
                    StarMadeLogic.getInstance().setCurrentModel(spec);
                    StarMadeLogic.setModel(ShipTreeLogic.loadShip(spec, cb));
                };
                log.log(Level.INFO, "Loading default blueprint: {0}", defaultName);
                f.loadInBackground("Loading " + defaultName + "…", t);
            } else {
                log.log(Level.INFO, "No default blueprint found; starting with an empty editor.");
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Ship load failed!", e);
        }
        log.config("Main application started: " + GlobalConfiguration.NAME);
    }

    /**
     * Picks a default blueprint to open on startup: the first valid blueprint in
     * the install's {@code blueprints-default} (one with a {@code header.smbph}),
     * preferring an "Isanth". Returns {@code null} if none are available.
     */
    private static String firstDefaultBlueprint() {
        try {
            List<String> names = BlueprintLogic.getDefaultBlueprintNames();
            if (names != null && !names.isEmpty()) {
                for (String n : names) {
                    if (n.toLowerCase().contains("isanth")) {
                        return n;
                    }
                }
                return names.get(0);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not list default blueprints.", e);
        }
        return null;
    }

    /**
     * @return the debugLogging
     */
    public static boolean isDebugLogging() {
        return debugLogging;
    }

    /**
     * @param aDebugLogging the debugLogging to set
     */
    public static void setDebugLogging(boolean aDebugLogging) {
        debugLogging = aDebugLogging;
    }

    public static void debug(String s) {
        if (isDebugLogging()) {
            log.info(s);
        }
    }

    /**
     * Probes whether the LWJGL native libraries can load on the current JVM.
     * Referencing {@code org.lwjgl.Sys} triggers its static initializer, which
     * loads the native library from {@code org.lwjgl.librarypath} (set to the
     * StarMade install's native/&lt;os&gt; folder in
     * {@link smc.smedit.logic.StarMadeLogic#setBaseDir}). On failure — e.g. no
     * StarMade install is configured, or its natives are stock LWJGL 2.x builds
     * that link the removed libjawt {@code SUNWprivate_1.1} symbol — this throws
     * an {@link Error}, and the editor falls back to the software renderer
     * instead of a dead GL canvas.
     */
    private static boolean isOpenGlAvailable() {
        try {
            org.lwjgl.Sys.getVersion();
            return true;
        } catch (final Throwable t) {
            log.log(Level.WARNING, "OpenGL/LWJGL natives unavailable "
                    + "(is a StarMade install with native/<os> configured?); "
                    + "using the software renderer. Pass -opengl to force. Cause: " + t);
            return false;
        }
    }

    private boolean compactToolbars = true;
    private boolean borderedButtons = true;
    private RenderPanel mClient;
    private JToolBar outerToolBar;
    private JToolBar innerToolBar;
    public final JScrollPane textScroll;

    // Dockable (Modern Docking) panels + the default layout, kept for Reset Layout.
    private ApplicationLayout mDefaultLayout;
    private DockPanel mViewportDock;
    private DockPanel mBrushDock;
    private DockPanel mConsoleDock;
    private DockPanel mBlockInfoDock;
    private DockPanel mLayersDock;
    private JMenu mWindowMenu;
    /** Menu bar home for one-shot operations (plugins) with no left-rail tool. */
    private JMenu mOperationsMenu;
    private StatusPanel mStatusPanel;

    public RenderFrame(String[] args) {
        setTitle(GlobalConfiguration.NAME + " v" + GlobalConfiguration.VERSION);
        mArgs = args;
        setIconImage(GlobalConfiguration.getImage(Resources.ICON));
        setSize(1024, 768);
        setLocationRelativeTo(getOwner());

        /* outer-most containers actually reserved for docking the toolbars.
         * so "cp" is actually not the contentpane of the JPanel, but let's
         * ignore that. */
        JPanel outerToolPane = (JPanel) super.getContentPane();
        JPanel innerToolPane = new JPanel(new BorderLayout());
        JPanel cp = new JPanel(new BorderLayout());
        outerToolPane.setLayout(new BorderLayout());
        outerToolPane.add(innerToolPane, BorderLayout.CENTER);
        innerToolPane.add(cp, BorderLayout.CENTER);

        // Prefer the hardware (OpenGL / LWJGL) renderer for accuracy and
        // performance. It needs the LWJGL natives (loaded from the StarMade
        // install via org.lwjgl.librarypath, set in StarMadeLogic#setBaseDir), so
        // we probe them first and fall back to the Java2D software renderer if
        // they can't load (e.g. no StarMade install configured). Overrides:
        // -software / -noopengl force software; -opengl forces OpenGL even if the
        // probe fails.
        boolean forceSoftware = false;
        boolean forceOpenGl = false;
        for (final String a : mArgs) {
            if ("-software".equals(a) || "-noopengl".equals(a)) {
                forceSoftware = true;
            } else if ("-opengl".equals(a)) {
                forceOpenGl = true;
            }
        }
        if (!forceSoftware && (forceOpenGl || isOpenGlAvailable())) {
            mClient = new LWJGLRenderPanel();
        } else {
            mClient = new AWTRenderPanel();
        }

        setupMenus();
        setupToolbars();

        textScroll = new JScrollPane(TextAreaLogHandler.TEXT_AREA,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        textScroll.setBorder(null);
        textScroll.setVisible(true);
        // Route application logging into the Console panel (the status bar no longer
        // shows an "App Events" label).
        Logger.getLogger("").addHandler(new TextAreaLogHandler());

        // Paint-program-style dockable layout (Modern Docking): the viewport,
        // brush palette and console are dockable / floating / collapsible panels,
        // and the arrangement is remembered between runs.
        BrushPanel brushPanel = new BrushPanel();
        RootDockingPanel dockRoot = setupDocking(brushPanel);
        populateWindowMenu();

        /* Toolbar placement: the main toolbar on top, and the per-tool options
         * (context) bar directly beneath it — it changes with the active tool. */
        JPanel northBar = new JPanel(new BorderLayout());
        northBar.add(outerToolBar, BorderLayout.NORTH);
        innerToolBar.setFloatable(false);
        northBar.add(innerToolBar, BorderLayout.SOUTH);
        getContentPane().add(northBar, BorderLayout.NORTH);
        getContentPane().add(dockRoot, BorderLayout.CENTER);
        // Fixed left tool rail (paint/modelling-tool "active tool" column). It is
        // deliberately chrome, not a dockable, so it can't be torn off or hidden.
        getContentPane().add(new ToolRail(), BorderLayout.WEST);
        // Keep the context bar in sync with the active tool.
        ToolController.get().addListener(this::updateContextBar);
        updateContextBar(ToolController.get().getActive());
        // A different model may expose a different operation set; rebuild the
        // Operations menu and the active tool's context-bar operations when it changes.
        StarMadeLogic.getInstance().addPropertyChangeListener("currentModel",
                e -> javax.swing.SwingUtilities.invokeLater(() -> {
                    rebuildOperationsMenu();
                    updateContextBar(ToolController.get().getActive());
                }));
        mStatusPanel = new StatusPanel();
        MemProgressBar memBar = new MemProgressBar();
        memBar.setPreferredSize(new Dimension(200, 20));
        mStatusPanel.addRightComponent(memBar);
        getContentPane().add(mStatusPanel, BorderLayout.SOUTH);

        // The GL viewport is a heavyweight canvas; Modern Docking's split dividers
        // default to a thin strip that's hard to grab next to it. Fatten them (and
        // keep continuous layout) so panels like the Brush palette can be resized.
        javax.swing.SwingUtilities.invokeLater(() -> tuneSplitPanes(getContentPane()));

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent evt) {
                if (safeClose()) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                getClient().requestFocusInWindow();
            }
        });
    }

    /**
     * Builds the dockable layout: registers the viewport, brush and console as
     * dockables, arranges a sensible default (viewport centre, brush left,
     * console bottom), then restores any previously-saved layout and enables
     * auto-persistence. The default is remembered for {@code Window > Reset
     * Layout}. The viewport is neither closable nor floatable — tearing the
     * heavyweight OpenGL canvas into a separate window would break its context.
     */
    private RootDockingPanel setupDocking(BrushPanel brushPanel) {
        Docking.initialize(this);
        DockingUI.initialize(); // FlatLaf-styled dock headers

        // Group panels into tabs by default (Krita / web-browser style). TOP_ALWAYS
        // gives every docked panel a persistent tab strip along its top edge, so the
        // common way to combine two panels is to drop one onto the other's tab strip
        // (or its centre): they merge into a single tabbed group where only one panel
        // is visible at a time. Dropping on a panel *edge* still splits them side by
        // side, so a side-by-side arrangement remains available when it's wanted.
        // SCROLL_TAB_LAYOUT keeps the tab strip usable once several panels are stacked.
        Settings.setDefaultTabPreference(DockableTabPreference.TOP_ALWAYS);
        Settings.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Enable the pin/collapse toolbars on the left, right and bottom edges so
        // panels can be minimized (auto-hidden) to an edge and re-expanded.
        RootDockingPanel dockRoot = new RootDockingPanel(this,
                EnumSet.of(ToolbarLocation.WEST, ToolbarLocation.EAST, ToolbarLocation.SOUTH));

        // args: closable, floatable, collapsible(auto-hide). The viewport stays
        // put — it's the main editing surface (and a heavyweight GL canvas).
        mViewportDock = new DockPanel("viewport", "Viewport", getClient(), false, false, false);
        mBrushDock = new DockPanel("brush", "Brush", brushPanel, true, true, true);
        mConsoleDock = new DockPanel("console", "Console", textScroll, true, true, true);
        mBlockInfoDock = new DockPanel("blockinfo", "Inspector", new BlockInfoPanel(), true, true, true);
        mLayersDock = new DockPanel("layers", "Layers", new LayersPanel(getClient()), true, true, true);

        // Clean "cross" default layout: a full-width console on the bottom (docked to
        // the window root), with the Brush and Selection panels flanking the viewport
        // in the middle band. The side panels are docked RELATIVE TO THE VIEWPORT, not
        // the window, so they only split the middle band and leave the console spanning
        // full width (docking them to the window root instead nests the console under
        // the brush, squashing the palette — the old, awful default). One-shot
        // operations no longer live in a dockable shelf — they surface in the active
        // tool's context bar and the Operations menu.
        Docking.dock(mViewportDock, this);
        Docking.dock(mConsoleDock, this, DockingRegion.SOUTH, 0.22);
        Docking.dock(mBrushDock, mViewportDock, DockingRegion.WEST, 0.20);
        Docking.dock(mBlockInfoDock, mViewportDock, DockingRegion.EAST, 0.22);
        // Tab the Layers panel onto the Inspector (same right flank, like the
        // prototype's Inspector/Layers tabs).
        Docking.dock(mLayersDock, mBlockInfoDock, DockingRegion.CENTER);

        // Remember this arrangement as the default, restore a saved layout if one
        // exists, then keep the layout persisted across runs (workspace memory).
        mDefaultLayout = DockingState.getApplicationLayout();
        try {
            File layoutFile = new File(Paths.getSettingsDirectory(), "layout.xml");
            AppState.setPersistFile(layoutFile);
            AppState.setDefaultApplicationLayout(mDefaultLayout);
            if (layoutFile.isFile()) {
                AppState.restore();
            }
            AppState.setAutoPersist(true);
            // If the user picked a default layout preset in Settings, load it.
            String defaultLayout = StarMadeLogic.getProps().getProperty("layout.default", "");
            if (!defaultLayout.isEmpty() && LayoutPresets.names().contains(defaultLayout)) {
                LayoutPresets.load(defaultLayout);
            }
            // Same one-time migration for the Layers panel: users with a saved
            // layout from before it existed won't have it, so restore() leaves it
            // hidden. Tab it onto the Inspector once, then respect the saved state.
            if (!StarMadeLogic.isProperty("layers.introduced")) {
                if (!Docking.isDocked(mLayersDock)) {
                    if (Docking.isDocked(mBlockInfoDock)) {
                        Docking.dock(mLayersDock, mBlockInfoDock, DockingRegion.CENTER);
                    } else {
                        Docking.dock(mLayersDock, mViewportDock, DockingRegion.EAST, 0.22);
                    }
                }
                StarMadeLogic.setProperty("layers.introduced", true);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not restore docking layout; using default.", e);
        }
        return dockRoot;
    }

    /**
     * Recursively fattens every docking split divider and forces continuous
     * layout, so dividers are grabbable and resize live — important next to the
     * heavyweight GL canvas, whose peer otherwise makes a thin lightweight divider
     * awkward to drag. Best-effort: called after the initial layout is built.
     */
    private static void tuneSplitPanes(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JSplitPane) {
                javax.swing.JSplitPane sp = (javax.swing.JSplitPane) c;
                sp.setContinuousLayout(true);
                sp.setDividerSize(9);
            }
            if (c instanceof java.awt.Container) {
                tuneSplitPanes((java.awt.Container) c);
            }
        }
    }

    /** Restores the default panel arrangement (Window &gt; Reset Layout, Settings &gt; Layout). */
    public void resetLayout() {
        if (mDefaultLayout != null) {
            DockingState.restoreApplicationLayout(mDefaultLayout);
        }
    }

    /**
     * Fills the Window menu: a show/hide toggle per collapsible panel, Reset
     * Layout, and the named-preset submenu. Called after {@link #setupDocking}.
     */
    private void populateWindowMenu() {
        mWindowMenu.removeAll();
        JCheckBoxMenuItem brushToggle = panelToggle(mBrushDock, DockingRegion.WEST, 0.20);
        JCheckBoxMenuItem consoleToggle = panelToggle(mConsoleDock, DockingRegion.SOUTH, 0.25);
        JCheckBoxMenuItem blockInfoToggle = panelToggle(mBlockInfoDock, DockingRegion.EAST, 0.2);
        JCheckBoxMenuItem layersToggle = panelToggle(mLayersDock, DockingRegion.EAST, 0.2);
        mWindowMenu.add(brushToggle);
        mWindowMenu.add(consoleToggle);
        mWindowMenu.add(blockInfoToggle);
        mWindowMenu.add(layersToggle);
        mWindowMenu.addSeparator();
        JMenuItem reset = new JMenuItem("Reset Layout");
        reset.addActionListener(e -> resetLayout());
        mWindowMenu.add(reset);
        mWindowMenu.addSeparator();
        mWindowMenu.add(buildLayoutsMenu());

        // Keep the checkboxes in sync with the real docked state (a panel may have
        // been closed via its header X since the menu was last shown).
        mWindowMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                brushToggle.setSelected(Docking.isDocked(mBrushDock));
                consoleToggle.setSelected(Docking.isDocked(mConsoleDock));
                blockInfoToggle.setSelected(Docking.isDocked(mBlockInfoDock));
                layersToggle.setSelected(Docking.isDocked(mLayersDock));
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
    }

    /**
     * A checkbox item that shows/hides a dockable. Re-docking a closed panel is
     * done explicitly to its home region (and it is re-registered first if the
     * close deregistered it), so re-enabling always brings the panel back.
     */
    private JCheckBoxMenuItem panelToggle(DockPanel dock, DockingRegion region, double split) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(dock.getTabText(), Docking.isDocked(dock));
        item.addActionListener(e -> {
            if (item.isSelected()) {
                if (!Docking.isDockableRegistered(dock.getPersistentID())) {
                    Docking.registerDockable(dock);
                }
                if (!Docking.isDocked(dock)) {
                    Docking.dock(dock, this, region, split);
                } else {
                    Docking.bringToFront(dock);
                }
            } else if (Docking.isDocked(dock)) {
                Docking.undock(dock);
            }
        });
        return item;
    }

    /** Builds the "Layouts" submenu (named workspace presets), rebuilt on open. */
    private JMenu buildLayoutsMenu() {
        JMenu menu = new JMenu("Layouts");
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) { rebuildLayoutsMenu(menu); }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        rebuildLayoutsMenu(menu);
        return menu;
    }

    private void rebuildLayoutsMenu(JMenu menu) {
        menu.removeAll();
        JMenuItem saveAs = new JMenuItem("Save Current Layout As…");
        saveAs.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Layout preset name:",
                    "Save Layout", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                try {
                    LayoutPresets.save(name);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "Could not save layout preset", ex);
                    JOptionPane.showMessageDialog(this, "Could not save layout: " + ex.getMessage(),
                            "Save Layout", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        menu.add(saveAs);

        List<String> names = LayoutPresets.names();
        if (!names.isEmpty()) {
            menu.addSeparator();
            for (String name : names) {
                JMenuItem load = new JMenuItem(name);
                load.addActionListener(e -> {
                    try {
                        LayoutPresets.load(name);
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "Could not load layout preset '" + name + "'", ex);
                    }
                });
                menu.add(load);
            }
            menu.addSeparator();
            JMenu delete = new JMenu("Delete");
            for (String name : names) {
                JMenuItem del = new JMenuItem(name);
                del.addActionListener(e -> LayoutPresets.delete(name));
                delete.add(del);
            }
            menu.add(delete);
        }
    }

    private void setupToolbars() {
        setOuterToolBar(new JToolBar());
        setInnerToolBar(new JToolBar());

        outerToolBar.add(getDefaultButton(new OpenExistingAction1(this), "Open BP", "open a blueprint", icon(Feather.FOLDER)));
        outerToolBar.add(getDefaultButton(new OpenFileAction1(this), "Open", "open a file", icon(Feather.FILE)));
        outerToolBar.add(getDefaultButton(new SaveAsBlueprintAction1(this, false), "Save BP", "Save blueprint", icon(Feather.SAVE)));
        outerToolBar.add(getDefaultButton(new SaveAsFileAction1(this), "Save", "Save file", icon(Feather.DOWNLOAD)));
        outerToolBar.add(getDefaultButton(new Shot(this), "Screenshot", "Screenshots of work", icon(Feather.CAMERA)));

        outerToolBar.addSeparator();
        outerToolBar.addSeparator();

        outerToolBar.add(getDefaultButton(new UndoActionButton(this), "Undo", "Undo last action", icon(Feather.ROTATE_CCW)));
        outerToolBar.add(getDefaultButton(new RedoActionButton(this), "Redo", "Redo last action", icon(Feather.ROTATE_CW)));

        outerToolBar.addSeparator();
        JButton resetCamButton = new JButton("Reset View");
        resetCamButton.setToolTipText("Reset the camera to look down at the ship from the top-left");
        resetCamButton.addActionListener(e -> getClient().resetCamera());
        outerToolBar.add(resetCamButton);

        outerToolBar.add(Box.createHorizontalGlue());

        // Tools/plugins now live in the dockable Maya-style "Tools" shelf below the
        // toolbar (see setupDocking) rather than in a drop-down button here.

        JButton settingsButton = new JButton(icon(Feather.SETTINGS));
        settingsButton.setToolTipText("Preferences");
        settingsButton.setFocusable(false);
        settingsButton.setMargin(new Insets(6, 3, 6, 3));
        settingsButton.setBorder(new EmptyBorder(3, 3, 3, 3));
        settingsButton.setPreferredSize(new Dimension(32, 32));
        settingsButton.setMaximumSize(new Dimension(32, 32));
        settingsButton.addActionListener(e -> openSettings());
        outerToolBar.add(settingsButton);
        // The memory-usage bar now lives at the bottom-right of the status bar.
    }

    /** Opens the tabbed Preferences dialog (Edit &gt; Preferences… and the toolbar gear). */
    private void openSettings() {
        new SettingsDialog(this).setVisible(true);
    }

    /**
     * Runs a model-load task on a background thread with a non-modal status-bar
     * progress bar, so the UI stays interactive during the load (unlike the modal
     * {@link RunnableLogic}). The mesh build runs on this thread too — the model
     * property change fires synchronously on whatever thread calls {@code setModel}.
     */
    public void loadInBackground(String title, IRunnableWithProgress task) {
        mStatusPanel.showLoading(title);
        Thread th = new Thread(title) {
            @Override
            public void run() {
                try {
                    task.run(new StatusBarCallback());
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Background load failed", t);
                } finally {
                    javax.swing.SwingUtilities.invokeLater(mStatusPanel::hideLoading);
                }
            }
        };
        th.setDaemon(true);
        th.start();
    }

    /** Minimal {@link IPluginCallback} that reports status to the status-bar loading bar. */
    private final class StatusBarCallback implements IPluginCallback {
        @Override
        public void setStatus(String status) {
            javax.swing.SwingUtilities.invokeLater(() -> mStatusPanel.setLoadingText(status));
        }

        @Override
        public void startTask(int size) {
        }

        @Override
        public void workTask(int amnt) {
        }

        @Override
        public void endTask() {
        }

        @Override
        public boolean isPleaseCancel() {
            return false;
        }

        @Override
        public void setErrorTitle(String title) {
        }

        @Override
        public void setErrorDescription(String desc) {
        }

        @Override
        public void setError(Throwable t) {
            log.log(Level.WARNING, "Load error", t);
        }
    }

    /**
     * Rebuilds the per-tool options bar for the active tool. Most brush settings
     * live in the Brush panel; this surfaces the active tool's name and its few
     * tool-specific quick options (e.g. the Select mode).
     */
    private void updateContextBar(EditorTool tool) {
        innerToolBar.removeAll();
        JLabel name = new JLabel(tool.getDisplayName(),
                FontIcon.of(tool.getIcon(), 16, iconColor()), JLabel.LEFT);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        name.setBorder(new EmptyBorder(2, 6, 2, 10));
        innerToolBar.add(name);
        innerToolBar.addSeparator();
        switch (tool) {
            case SELECT:
                SelectionModel sel = StarMadeLogic.getInstance().getSelection();
                innerToolBar.add(new JLabel("Mode: "));
                JComboBox<SelectionModel.Mode> modeBox = new JComboBox<>(SelectionModel.Mode.values());
                modeBox.setSelectedItem(sel.getMode());
                modeBox.setMaximumSize(modeBox.getPreferredSize());
                modeBox.addActionListener(e -> {
                    SelectionModel.Mode m = (SelectionModel.Mode) modeBox.getSelectedItem();
                    sel.setMode(m);
                    if (m == SelectionModel.Mode.ENTITY) {
                        sel.selectEntity(StarMadeLogic.getModel());
                    }
                });
                innerToolBar.add(modeBox);
                innerToolBar.addSeparator();
                innerToolBar.add(hint("Double-click a block to select its whole connected type."));
                break;
            case FILL:
                innerToolBar.add(hint("Click to repaint the selection — or the connected same-type region — with the brush material."));
                break;
            case BUILD:
                innerToolBar.add(hint("Left-click a block face to place the active block."));
                break;
            case PAINT:
                innerToolBar.add(hint("Left-click to recolour. Select a region to confine painting to it."));
                innerToolBar.addSeparator();
                JCheckBox convert = new JCheckBox("Convert equivalent shapes",
                        ToolController.get().isConvertShapes());
                convert.setFocusable(false);
                convert.setToolTipText("Keep the painted block's shape / slab in the new material where possible.");
                convert.addActionListener(e -> ToolController.get().setConvertShapes(convert.isSelected()));
                innerToolBar.add(convert);
                break;
            case ERASE:
                innerToolBar.add(hint("Left-click (or drag) to remove blocks."));
                break;
            case PICKER:
                innerToolBar.add(hint("Click a block to load its type into the brush (Alt-click works from any tool)."));
                break;
            default:
                innerToolBar.add(hint(tool.getTooltip()));
                break;
        }
        addToolOperations(tool);
        innerToolBar.revalidate();
        innerToolBar.repaint();
    }

    /**
     * Appends the active tool's one-shot operations to the context bar as compact
     * buttons (e.g. Select → Select All / None / Specific), followed by an
     * overflow "Operations ▾" button that opens every category regardless of the
     * active tool. Operations with no tool home are reached from that overflow (or
     * the Operations menu). The overflow is always added — even for tools that
     * carry no operations — so every operation stays one click away.
     */
    private void addToolOperations(EditorTool tool) {
        Map<String, List<IBlocksPlugin>> byCategory = PluginCategories.byCategory();
        for (String category : tool.operationCategories()) {
            List<IBlocksPlugin> ops = byCategory.get(category);
            if (ops == null || ops.isEmpty()) {
                continue;
            }
            innerToolBar.addSeparator();
            for (IBlocksPlugin plugin : ops) {
                innerToolBar.add(operationButton(plugin, category));
            }
        }

        // Push the overflow to the far right; it exposes every operation category so
        // nothing is more than one click away even without a matching tool.
        innerToolBar.add(Box.createHorizontalGlue());
        JButton more = new JButton("Operations", icon(Feather.MORE_HORIZONTAL));
        more.setToolTipText("All operations, grouped by category");
        more.setFocusable(false);
        more.setHorizontalTextPosition(SwingConstants.LEFT);
        more.setEnabled(!byCategory.isEmpty());
        more.addActionListener(e -> buildOperationsPopup(byCategory).show(more, 0, more.getHeight()));
        innerToolBar.add(more);
    }

    /** A compact icon-over-label context-bar button that runs one operation. */
    private JButton operationButton(IBlocksPlugin plugin, String category) {
        JButton button = new JButton(new BlocksPluginAction(getClient(), plugin));
        button.setText(PluginCategories.shortLabel(plugin.getName()));
        button.setIcon(ShelfIcons.iconFor(plugin.getName(), category, 18));
        button.setToolTipText(PluginCategories.tooltip(plugin));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(2);
        button.setFocusable(false);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 10.0f));
        button.setMargin(new Insets(2, 6, 2, 6));
        return button;
    }

    /** Popup listing every operation category as a submenu (the context-bar overflow). */
    private JPopupMenu buildOperationsPopup(Map<String, List<IBlocksPlugin>> byCategory) {
        JPopupMenu popup = new JPopupMenu();
        for (Map.Entry<String, List<IBlocksPlugin>> entry : byCategory.entrySet()) {
            JMenu sub = new JMenu(entry.getKey());
            sub.setIcon(ShelfIcons.iconFor("", entry.getKey(), TOOLBAR_ICON_SIZE));
            for (IBlocksPlugin plugin : entry.getValue()) {
                sub.add(operationItem(plugin, entry.getKey()));
            }
            popup.add(sub);
        }
        if (byCategory.isEmpty()) {
            JMenuItem none = new JMenuItem("No operations available");
            none.setEnabled(false);
            popup.add(none);
        }
        return popup;
    }

    /** A muted, non-interactive hint label for the context bar. */
    private static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        java.awt.Color fg = javax.swing.UIManager.getColor("Label.disabledForeground");
        if (fg != null) {
            l.setForeground(fg);
        }
        return l;
    }

    /** Size (px) of the toolbar glyph icons. */
    private static final int TOOLBAR_ICON_SIZE = 18;

    /** Builds a toolbar icon from an Ikonli glyph, tinted to the current theme foreground. */
    private static Icon icon(Ikon glyph) {
        return FontIcon.of(glyph, TOOLBAR_ICON_SIZE, iconColor());
    }

    /** The current theme's foreground colour for toolbar glyphs (dark on light themes, light on dark). */
    private static java.awt.Color iconColor() {
        java.awt.Color fg = javax.swing.UIManager.getColor("Button.foreground");
        return fg != null ? fg : new java.awt.Color(0xB8, 0xB8, 0xB8);
    }

    /**
     * Re-tints the toolbar glyph icons to the current theme foreground. FontIcons
     * are coloured at creation, so a live theme switch would otherwise leave them
     * in the old (e.g. near-invisible) colour.
     */
    public void refreshToolbarIcons() {
        java.awt.Color fg = iconColor();
        for (java.awt.Component c : outerToolBar.getComponents()) {
            if (c instanceof javax.swing.AbstractButton) {
                Icon ic = ((javax.swing.AbstractButton) c).getIcon();
                if (ic instanceof FontIcon) {
                    ((FontIcon) ic).setIconColor(fg);
                }
            }
        }
        outerToolBar.repaint();
        // The context bar's operation glyphs and the Operations menu are tinted at
        // creation too; rebuild them to re-tint for the new theme.
        rebuildOperationsMenu();
        updateContextBar(ToolController.get().getActive());
    }

    private void setupMenus() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);
        JMenu menuEdit = new JMenu("Edit");
        menuEdit.setMnemonic(KeyEvent.VK_E);
        JMenu menuView = new JMenu("View");
        menuView.setMnemonic(KeyEvent.VK_V);
        JMenu menuHelp = new JMenu("Help");
        menuHelp.setMnemonic(KeyEvent.VK_H);
        /*layout*/
        setJMenuBar(menuBar);
        menuBar.add(menuFile);
        menuFile.add(new OpenExistingAction(this));
        menuFile.add(new OpenFileAction(this));
        menuFile.add(new JSeparator());
        menuFile.add(new SaveAction(this));
        JMenu saveAs = new JMenu("Save As");
        menuFile.add(saveAs);
        saveAs.add(new SaveAsBlueprintAction(this, false));
        saveAs.add(new SaveAsBlueprintAction(this, true));
        saveAs.add(new SaveAsFileAction(this));
        menuFile.add(new JSeparator());
        menuFile.add(new QuitAction(this));
        menuBar.add(menuEdit);
        menuEdit.add(new UndoAction(this));
        menuEdit.add(new RedoAction(this));
        menuEdit.add(new JSeparator());
        JMenuItem prefs = new JMenuItem("Preferences…");
        prefs.addActionListener(e -> openSettings());
        menuEdit.add(prefs);
        menuEdit.add(new JSeparator());
        menuBar.add(menuView);
        menuView.add(new JCheckBoxMenuItem(new AxisAction(this)));
        menuView.add(new JCheckBoxMenuItem(new GridAction(this)));
        // Where the axis/grid guide is centred.
        JMenu axisOrigin = new JMenu("Axis Origin");
        ButtonGroup anchorGroup = new ButtonGroup();
        JRadioButtonMenuItem sceneCenter = new JRadioButtonMenuItem("Scene Center",
                getClient().getAxisAnchor() == RenderPanel.AxisAnchor.SCENE);
        sceneCenter.addActionListener(e -> getClient().setAxisAnchor(RenderPanel.AxisAnchor.SCENE));
        JRadioButtonMenuItem selectionCenter = new JRadioButtonMenuItem("Selection Center",
                getClient().getAxisAnchor() == RenderPanel.AxisAnchor.SELECTION);
        selectionCenter.addActionListener(e -> getClient().setAxisAnchor(RenderPanel.AxisAnchor.SELECTION));
        anchorGroup.add(sceneCenter);
        anchorGroup.add(selectionCenter);
        axisOrigin.add(sceneCenter);
        axisOrigin.add(selectionCenter);
        menuView.add(axisOrigin);
        JMenuItem resetCamItem = new JMenuItem("Reset Camera");
        resetCamItem.addActionListener(e -> getClient().resetCamera());
        menuView.add(resetCamItem);
        JCheckBoxMenuItem fpItem = new JCheckBoxMenuItem(new CameraModeAction(this));
        menuView.add(fpItem);
        // Camera mode also toggles from the viewport ('C'), so re-sync the tick each
        // time the menu opens rather than leaving it stale.
        menuView.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                fpItem.setSelected(getClient().getCameraMode() == RenderPanel.CameraMode.FIRST_PERSON);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        JCheckBoxMenuItem orthoItem = new JCheckBoxMenuItem("Orthographic");
        orthoItem.addActionListener(e -> getClient().setOrthographic(orthoItem.isSelected()));
        menuView.add(orthoItem);
        // Operations menu — every one-shot plugin, grouped by category into
        // submenus. This is the durable home for operations without a left-rail
        // tool (Import, Export, Macro, …); tool-matched ones also appear here as
        // well as in the active tool's context bar. Rebuilt when the model changes.
        mOperationsMenu = new JMenu("Operations");
        mOperationsMenu.setMnemonic(KeyEvent.VK_O);
        rebuildOperationsMenu();
        menuBar.add(mOperationsMenu);
        // Window menu — items are added in populateWindowMenu() after the docking
        // layout is built (the panel toggles need the dockable instances).
        mWindowMenu = new JMenu("Window");
        mWindowMenu.setMnemonic(KeyEvent.VK_W);
        menuBar.add(mWindowMenu);
        menuBar.add(menuHelp);
    }

    /**
     * Rebuilds the Operations menu from the plugins valid for the current model:
     * one submenu per {@link PluginCategories} category, each holding that
     * category's operations. Safe to call on the EDT at any time.
     */
    private void rebuildOperationsMenu() {
        if (mOperationsMenu == null) {
            return;
        }
        mOperationsMenu.removeAll();
        Map<String, List<IBlocksPlugin>> byCategory = PluginCategories.byCategory();
        for (Map.Entry<String, List<IBlocksPlugin>> entry : byCategory.entrySet()) {
            JMenu sub = new JMenu(entry.getKey());
            sub.setIcon(ShelfIcons.iconFor("", entry.getKey(), TOOLBAR_ICON_SIZE));
            for (IBlocksPlugin plugin : entry.getValue()) {
                sub.add(operationItem(plugin, entry.getKey()));
            }
            mOperationsMenu.add(sub);
        }
        if (mOperationsMenu.getMenuComponentCount() == 0) {
            JMenuItem none = new JMenuItem("No operations available");
            none.setEnabled(false);
            mOperationsMenu.add(none);
        }
    }

    /** A menu item that runs one operation (plugin), iconified and short-labelled. */
    private JMenuItem operationItem(IBlocksPlugin plugin, String category) {
        JMenuItem item = new JMenuItem(new BlocksPluginAction(getClient(), plugin));
        item.setText(PluginCategories.shortLabel(plugin.getName()));
        item.setIcon(ShelfIcons.iconFor(plugin.getName(), category, TOOLBAR_ICON_SIZE));
        item.setToolTipText(PluginCategories.tooltip(plugin));
        return item;
    }

    /**
     * Makes a JButton with the given icon and tooltop. If the icon cannot be
     * loaded, then the text will be used instead.
     *
     * Adds this RenderFame as an actionListener.
     *
     * @return a shiny new JButton
     *
     */
    private JButton getDefaultButton(final Action a, final String label, final String tip, final Icon i) {
        final JButton button = new JButton(a);
        button.setToolTipText(tip);
        button.setFocusable(false);
        button.setMargin(new Insets(6, 3, 6, 3));
        button.setBorder(new EmptyBorder(3, 3, 3, 3));
        if (i != null) {
            button.setIcon(i);
            button.setText(null);
            button.setPreferredSize(new Dimension(32, 32));
            button.setMaximumSize(new Dimension(32, 32));
        } else {
            // No icon available — show the text label so the button is still usable.
            button.setText(label);
        }
        return button;
    }

    private boolean safeClose() {
        boolean pass;
        final int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you would like to quit?", "Close",
                JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        pass = result == JOptionPane.YES_OPTION;
        return pass;
    }

    /**
     *
     * @return
     */
    public RenderPanel getClient() {
        return mClient;
    }

    /**
     *
     * @param client
     */
    public void setClient(RenderPanel client) {
        mClient = client;
    }

    /**
     *
     * @return
     */
    public JFrame getFrame() {
        for (Component c = this; c != null; c = c.getParent()) {
            if (c instanceof JFrame) {
                return (JFrame) c;
            }
        }
        return null;
    }

    /**
     * @return the compactToolbars
     */
    public boolean isCompactToolbars() {
        return compactToolbars;
    }

    /**
     * @param compactToolbars the compactToolbars to set
     */
    public void setCompactToolbars(boolean compactToolbars) {
        this.compactToolbars = compactToolbars;
    }

    /**
     * @return the borderedButtons
     */
    public boolean isBorderedButtons() {
        return borderedButtons;
    }

    /**
     * @param borderedButtons the borderedButtons to set
     */
    public void setBorderedButtons(boolean borderedButtons) {
        this.borderedButtons = borderedButtons;
    }

    /**
     * @return the outerToolBar
     */
    public JToolBar getOuterToolBar() {
        return outerToolBar;
    }

    /**
     * @param outerToolBar the outerToolBar to set
     */
    public void setOuterToolBar(JToolBar outerToolBar) {
        this.outerToolBar = outerToolBar;
    }

    /**
     * @return the innerToolBar
     */
    public JToolBar getInnerToolBar() {
        return innerToolBar;
    }

    /**
     * @param innerToolBar the innerToolBar to set
     */
    public void setInnerToolBar(JToolBar innerToolBar) {
        this.innerToolBar = innerToolBar;
    }

}
