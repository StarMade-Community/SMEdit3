package smc.smedit.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.util.ThemeManager;

/**
 * The in-app Preferences dialog: a tabbed panel for Appearance (theme), Layout,
 * StarMade folder and Performance. Replaces the old startup options screen. All
 * settings persist to the shared prefs ({@link StarMadeLogic#getProps()}); the
 * theme applies live, the rest note that they take effect after a restart.
 */
@SuppressWarnings("serial")
public class SettingsDialog extends JDialog {

    private static final String BUILTIN_LAYOUT = "(Built-in default)";

    private final RenderFrame owner;
    private final Properties props = StarMadeLogic.getProps();

    private final String startTheme = ThemeManager.current();
    private boolean themeCommitted;

    private JComboBox<String> themeCombo;
    private JComboBox<String> layoutCombo;
    private JTextField homeField;
    private JSpinner heapSpinner;
    private JComboBox<String> textureCombo;
    private JCheckBox gpuCheck;

    // Baseline for restart-only fields, to decide whether to show the restart note.
    private String baseHome;
    private String baseHeap;
    private String baseTexture;
    private boolean baseGpu;

    public SettingsDialog(RenderFrame owner) {
        super(owner, "Preferences", true);
        this.owner = owner;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appearance", appearanceTab());
        tabs.addTab("Layout", layoutTab());
        tabs.addTab("StarMade", starMadeTab());
        tabs.addTab("Performance", performanceTab());

        snapshotBaseline();

        add(tabs, BorderLayout.CENTER);
        add(buttonBar(), BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(480, 340));
        setLocationRelativeTo(owner);
    }

    // ---- tabs ----

    private JPanel appearanceTab() {
        JPanel p = form();
        themeCombo = new JComboBox<>(ThemeManager.LABELS);
        themeCombo.setSelectedIndex(Math.max(0, indexOf(ThemeManager.IDS, startTheme)));
        // Live preview on change; persisted only on OK/Apply, reverted on Cancel.
        themeCombo.addActionListener(e -> previewTheme(selectedThemeId()));
        addRow(p, 0, "Theme:", themeCombo);
        addNote(p, 1, "Applies immediately.");
        return p;
    }

    private JPanel layoutTab() {
        JPanel p = form();
        List<String> names = new ArrayList<>();
        names.add(BUILTIN_LAYOUT);
        names.addAll(smc.smedit.ui.dock.LayoutPresets.names());
        layoutCombo = new JComboBox<>(names.toArray(new String[0]));
        String saved = props.getProperty("layout.default", "");
        layoutCombo.setSelectedItem(saved.isEmpty() ? BUILTIN_LAYOUT : saved);
        addRow(p, 0, "Default layout:", layoutCombo);

        JButton reset = new JButton("Reset to Default Layout");
        reset.addActionListener(e -> owner.resetLayout());
        addRow(p, 1, "", reset);
        addNote(p, 2, "The chosen preset is restored on the next start.");
        return p;
    }

    private JPanel starMadeTab() {
        JPanel p = form();
        homeField = new JTextField(props.getProperty("starmade.home", ""), 24);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            File init = new File(homeField.getText());
            File chosen = StarMadeDirChooser.choose(this, init.isDirectory() ? init : null);
            if (chosen != null) {
                homeField.setText(chosen.getAbsolutePath());
            }
        });
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(homeField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        addRow(p, 0, "StarMade folder:", row);
        addNote(p, 1, "Changing the install folder takes effect after a restart.");
        return p;
    }

    private JPanel performanceTab() {
        JPanel p = form();
        int heap = parseInt(props.getProperty("memory", "8"), 8);
        heapSpinner = new JSpinner(new SpinnerNumberModel(Math.min(Math.max(heap, 1), 64), 1, 64, 1));
        addRow(p, 0, "Max heap (GB):", heapSpinner);

        textureCombo = new JComboBox<>(texturePacks());
        textureCombo.setSelectedItem(props.getProperty("texture", "Default"));
        addRow(p, 1, "Texture pack:", textureCombo);

        gpuCheck = new JCheckBox("Render on the discrete GPU (Linux hybrid graphics)",
                !"false".equalsIgnoreCase(props.getProperty("gpu.offload", "true")));
        addRow(p, 2, "", gpuCheck);
        addNote(p, 3, "These take effect after a restart.");
        return p;
    }

    // ---- buttons / commit ----

    private JPanel buttonBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new javax.swing.BoxLayout(bar, javax.swing.BoxLayout.LINE_AXIS));
        bar.setBorder(new EmptyBorder(8, 8, 8, 8));
        bar.add(Box.createHorizontalGlue());
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        JButton apply = new JButton("Apply");
        ok.addActionListener(e -> { commit(); dispose(); });
        apply.addActionListener(e -> commit());
        cancel.addActionListener(e -> { revertTheme(); dispose(); });
        bar.add(ok);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(cancel);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(apply);
        getRootPane().setDefaultButton(ok);
        return bar;
    }

    /** Writes every setting to the prefs, persists, and notes restart-only changes. */
    private void commit() {
        boolean restartNeeded =
                !homeField.getText().equals(baseHome)
                || !heapSpinner.getValue().toString().equals(baseHeap)
                || !String.valueOf(textureCombo.getSelectedItem()).equals(baseTexture)
                || gpuCheck.isSelected() != baseGpu;

        props.setProperty("theme", selectedThemeId());
        props.setProperty("layout.default",
                BUILTIN_LAYOUT.equals(layoutCombo.getSelectedItem()) ? "" : String.valueOf(layoutCombo.getSelectedItem()));
        props.setProperty("starmade.home", homeField.getText().trim());
        props.setProperty("memory", heapSpinner.getValue().toString());
        props.setProperty("texture", String.valueOf(textureCombo.getSelectedItem()));
        props.setProperty("gpu.offload", String.valueOf(gpuCheck.isSelected()));
        StarMadeLogic.saveProps();

        themeCommitted = true;
        snapshotBaseline();

        if (restartNeeded) {
            JOptionPane.showMessageDialog(this,
                    "Some changes (StarMade folder, heap, texture pack, GPU) take effect after restarting SMEdit.",
                    "Restart required", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Applies a theme live and re-tints the toolbar glyphs to the new foreground. */
    private void previewTheme(String id) {
        ThemeManager.applyLive(id);
        owner.refreshToolbarIcons();
    }

    /** On Cancel, undo an uncommitted live theme preview. */
    private void revertTheme() {
        if (!themeCommitted && !selectedThemeId().equals(startTheme)) {
            previewTheme(startTheme);
        }
    }

    // ---- helpers ----

    private void snapshotBaseline() {
        baseHome = homeField != null ? homeField.getText() : props.getProperty("starmade.home", "");
        baseHeap = heapSpinner != null ? heapSpinner.getValue().toString() : props.getProperty("memory", "8");
        baseTexture = textureCombo != null ? String.valueOf(textureCombo.getSelectedItem()) : props.getProperty("texture", "Default");
        baseGpu = gpuCheck != null ? gpuCheck.isSelected() : !"false".equalsIgnoreCase(props.getProperty("gpu.offload", "true"));
    }

    private String selectedThemeId() {
        int i = themeCombo.getSelectedIndex();
        return ThemeManager.IDS[Math.max(0, i)];
    }

    private String[] texturePacks() {
        List<String> packs = new ArrayList<>();
        try {
            File dir = new File(StarMadeLogic.getInstance().getBaseDir(), "data/textures/block");
            File[] subs = dir.listFiles(File::isDirectory);
            if (subs != null) {
                for (File f : subs) {
                    packs.add(f.getName());
                }
            }
        } catch (RuntimeException ignored) {
            // base dir not set / not readable — fall back below
        }
        if (packs.isEmpty()) {
            packs.add("Default");
        }
        return packs.toArray(new String[0]);
    }

    private static JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        return p;
    }

    private static void addRow(JPanel p, int row, String label, Component c) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 5, 5, 5);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = row;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
    }

    private static void addNote(JPanel p, int row, String text) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 5, 2, 5);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        JLabel note = new JLabel(text);
        note.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        note.setEnabled(false);
        p.add(note, g);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }
}
