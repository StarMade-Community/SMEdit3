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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.border.EmptyBorder;
import smc.smedit.log.TextAreaLogHandler;
import smc.smedit.logic.RunnableLogic;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.mods.IRunnableWithProgress;
import smc.smedit.ui.act.Shot;
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
import smc.smedit.ui.act.memRefresh;
import smc.smedit.ui.act.plugin.BlocksPluginAction;
import smc.smedit.ui.act.view.AxisAction;
import smc.smedit.ui.act.view.DontDrawAction;
import smc.smedit.ui.act.view.PlainAction;
import smc.smedit.ui.logic.MenuLogic;
import smc.smedit.ui.logic.ShipSpec;
import smc.smedit.ui.logic.ShipTreeLogic;
import smc.smedit.ui.lwjgl.LWJGLRenderPanel;
import smc.smedit.util.GlobalConfiguration;
import smc.smedit.util.Paths;
import smc.smedit.util.Resources;
import smc.smedit.util.SplashScreen;

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
            /*Chenage the commented line to change the default loaded blueprint*/
            /*method updated as of SMEdit v1.06*/
            final ShipSpec spec = ShipTreeLogic.getBlueprintSpec("Omen-Navy-Class", true);
            //final ShipSpec spec = ShipTreeLogic.getBlueprintSpec("Isanth-VI", true);
            if (spec != null) {
                IRunnableWithProgress t = new IRunnableWithProgress() {
                    @Override
                    public void run(IPluginCallback cb) {
                        StarMadeLogic.getInstance().setCurrentModel(spec);
                        StarMadeLogic.setModel(ShipTreeLogic.loadShip(spec, cb));
                    }
                };
                log.log(Level.INFO, "Loading Ship!");
                RunnableLogic.run(f, "Loading...", t);
            } else {
                log.log(Level.WARNING, "Could not find the ship your asking to load!");
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Ship load failed!", e);
        }
        log.config("Main application started: " + GlobalConfiguration.NAME);
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
    private final JTabbedPane ClientWindows;
    public final JScrollPane textScroll;

    private JButton mPlugins;

    public RenderFrame(String[] args) {
        setTitle(GlobalConfiguration.NAME + " version 1.0" + ((float) GlobalConfiguration.getVersion() / 100));
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

        cp.setLayout(new BorderLayout());
        JPanel main = new JPanel(new BorderLayout());
        main.add(new EditPanel(getClient(), this), BorderLayout.WEST);
        main.add(getClient(), BorderLayout.CENTER);
        textScroll = new JScrollPane(TextAreaLogHandler.TEXT_AREA,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        textScroll.setBorder(null);
        textScroll.setVisible(true);
        ClientWindows = new JTabbedPane();
        ClientWindows.setFont(new Font("Futura Md BT", 0, 10));
        ClientWindows.addTab("Edit Window", main);
        ClientWindows.setFont(new Font("Futura Md BT", 0, 10));
        ClientWindows.addTab("Log Window", textScroll);

        /* Toolbar placement */
        innerToolPane.add(innerToolBar, BorderLayout.NORTH);
        outerToolPane.add(outerToolBar, BorderLayout.NORTH);
        getContentPane().add(ClientWindows, BorderLayout.CENTER);
        getContentPane().add(new StatusPanel(), BorderLayout.SOUTH);

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

    private void setupToolbars() {
        setOuterToolBar(new JToolBar());
        setInnerToolBar(new JToolBar());

        JButton openPrintButton;
        final ImageIcon op = new ImageIcon(Paths.getIconDirectory() + "/open_print.png");
        openPrintButton = getDefaultButton(new OpenExistingAction1(this), "open a blueprint", op);
        outerToolBar.add(openPrintButton);

        JButton openButton;
        final ImageIcon o = new ImageIcon(Paths.getIconDirectory() + "/open.png");
        openButton = getDefaultButton(new OpenFileAction1(this), "open a file", o);
        outerToolBar.add(openButton);

        JButton savePrintButton;
        final ImageIcon sp = new ImageIcon(Paths.getIconDirectory() + "/save.png");
        savePrintButton = getDefaultButton(new SaveAsBlueprintAction1(this, false), "Save blueprint", sp);
        outerToolBar.add(savePrintButton);

        JButton saveButton;
        final ImageIcon sa = new ImageIcon(Paths.getIconDirectory() + "/save_as.png");
        saveButton = getDefaultButton(new SaveAsFileAction1(this), "Save file", sa);
        outerToolBar.add(saveButton);

        JButton screenButton;
        final ImageIcon s = new ImageIcon(Paths.getIconDirectory() + "/shot.png");
        screenButton = getDefaultButton(new Shot(this), "Screenshots of work", s);
        outerToolBar.add(screenButton);

        outerToolBar.addSeparator();
        outerToolBar.addSeparator();

        JButton undoButton;
        final ImageIcon u = new ImageIcon(Paths.getIconDirectory() + "/undo.png");
        undoButton = getDefaultButton(new UndoActionButton(this), "Undo last action", u);
        outerToolBar.add(undoButton);

        JButton redoButton;
        final ImageIcon r = new ImageIcon(Paths.getIconDirectory() + "/redo.png");
        redoButton = getDefaultButton(new RedoActionButton(this), "Redo last action", r);
        outerToolBar.add(redoButton);

        outerToolBar.add(Box.createHorizontalGlue());

        final ImageIcon p = new ImageIcon(Paths.getIconDirectory() + "/plugins.png");
        mPlugins = getDefaultActionlessButton("Plugins", "List of avalable plugins", p);
        outerToolBar.add(mPlugins);
        mPlugins.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doPlugin();
            }
        });

        /*add memory ProgressBar*/
        JButton memButton;
        final ImageIcon c = new ImageIcon(Paths.getIconDirectory() + "/cpu.png");
        memButton = getProgressButton(new memRefresh(), "Click to refresh Memory use", c);
        outerToolBar.add(memButton);

    }

    private void setupMenus() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);
        JMenu menuEdit = new JMenu("Edit");
        menuEdit.setMnemonic(KeyEvent.VK_E);
        JMenu menuView = new JMenu("View");
        menuView.setMnemonic(KeyEvent.VK_V);
        JMenu menuModify = new JMenu("Modify");
        menuModify.setMnemonic(KeyEvent.VK_M);
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
        JSeparator menuFileStart = new JSeparator();
        menuFileStart.setName("pluginsStartHere");
        menuFile.add(menuFileStart);
        menuFile.add(new JSeparator());
        menuFile.add(new QuitAction(this));
        menuBar.add(menuEdit);
        menuEdit.add(new UndoAction(this));
        menuEdit.add(new RedoAction(this));
        menuEdit.add(new JSeparator());
        menuBar.add(menuView);
        menuView.add(new JCheckBoxMenuItem(new PlainAction(this)));
        menuView.add(new JCheckBoxMenuItem(new AxisAction(this)));
        menuView.add(new JCheckBoxMenuItem(new DontDrawAction(this)));
        JSeparator viewFileStart = new JSeparator();
        viewFileStart.setName("pluginsStartHere");
        menuView.add(viewFileStart);
        menuBar.add(menuModify);
        menuBar.add(menuHelp);
        /*link*/
        menuFile.addMenuListener(new PluginPopupListener(this, IBlocksPlugin.SUBTYPE_FILE));
        menuEdit.addMenuListener(new PluginPopupListener(this, IBlocksPlugin.SUBTYPE_EDIT));
        menuView.addMenuListener(new PluginPopupListener(this, IBlocksPlugin.SUBTYPE_VIEW));
        menuModify.addMenuListener(new PluginPopupListener(this, IBlocksPlugin.SUBTYPE_MODIFY, IBlocksPlugin.SUBTYPE_GENERATE));
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
    private JButton getDefaultButton(final Action a, final String tip, final ImageIcon i) {
        final JButton button = new JButton(a);
        button.setToolTipText(tip);
        button.setIcon(i);
        button.setFocusable(false);
        button.setMargin(new Insets(6, 3, 6, 3));
        button.setPreferredSize(new Dimension(32, 32));
        button.setMaximumSize(new Dimension(32, 32));
        button.setBorder(new EmptyBorder(3, 3, 3, 3));

        return button;
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
    private JButton getDefaultActionlessButton(final String text, final String tip, final ImageIcon i) {
        final JButton button = new JButton();
        button.setText(text);
        button.setToolTipText(tip);
        button.setIcon(i);
        button.setFocusable(false);
        button.setMargin(new Insets(6, 3, 6, 3));
        button.setPreferredSize(new Dimension(75, 32));
        button.setMaximumSize(new Dimension(75, 32));
        button.setBorder(new EmptyBorder(3, 3, 3, 3));
        button.setFont(new Font("Tahoma", 0, 10));

        return button;
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
    private JButton getProgressButton(final Action a, final String tip, final ImageIcon i) {
        MemProgressBar mem = new MemProgressBar();
        mem.setMaximumSize(new Dimension(250, 32));
        final JButton button = new JButton(a);
        //button.add(Box.createHorizontalGlue());
        button.add(mem);
        button.setToolTipText(tip);
        button.setIcon(i);
        button.setFocusable(false);

        button.setPreferredSize(new Dimension(250, 32));
        button.setMaximumSize(new Dimension(250, 32));
        button.setBorder(new EmptyBorder(3, 3, 3, 3));

        return button;
    }

    /**
     *
     * @param menu
     * @param subTypes
     */
    public void updatePopup(JMenu menu, int... subTypes) {
        MenuLogic.clearPluginMenus(menu);
        ShipSpec spec = StarMadeLogic.getInstance().getCurrentModel();
        if (spec == null) {
            return;
        }
        int type = spec.getClassification();
        int lastModIndex = menu.getItemCount();
        int lastCount = 0;
        for (int subType : subTypes) {
            int thisCount = MenuLogic.addPlugins(getClient(), menu, type, subType);
            if ((thisCount > 0) && (lastCount > 0)) {
                JSeparator sep = new JSeparator();
                sep.setName("plugin");
                menu.add(sep, lastModIndex);
                lastCount = 0;
            }
            lastCount += thisCount;
            lastModIndex = menu.getItemCount();
        }
    }

    public void doPlugin() {
        JPopupMenu popup = new JPopupMenu();
        int classification = StarMadeLogic.getInstance().getCurrentModel().getClassification();
        List<IBlocksPlugin> plugins = StarMadeLogic.getBlocksPlugins(classification, IBlocksPlugin.SUBTYPE_PAINT);
        if (plugins.isEmpty()) {
            popup.add("no plugins");
        }

        for (IBlocksPlugin plugin : plugins) {
            BlocksPluginAction action = new BlocksPluginAction(getClient(), plugin);
            JMenuItem men = new JMenuItem(action);
            popup.add(men);
        }
        Dimension d = mPlugins.getSize();
        popup.show(mPlugins, d.width, d.height);
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
