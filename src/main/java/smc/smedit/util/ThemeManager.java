package smc.smedit.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import smc.smedit.logic.StarMadeLogic;

/**
 * Applies and persists the FlatLaf UI theme. The chosen theme id is stored in the
 * shared prefs ({@code theme} key) and read at startup by
 * {@code SMEdit.setupLookAndFeel}; the Settings dialog switches it live.
 */
public final class ThemeManager {

    /** Persisted theme ids, parallel to {@link #LABELS}. */
    public static final String[] IDS = {"light", "maclight", "dark", "macdark"};
    /** Human-readable labels for the Settings combo. */
    public static final String[] LABELS = {"Light", "macOS Light", "Dark", "macOS Dark"};

    private static final String KEY = "theme";
    private static final String DEFAULT = "dark";

    private ThemeManager() {
    }

    /** The saved theme id (default {@code dark}). */
    public static String current() {
        return StarMadeLogic.getProps().getProperty(KEY, DEFAULT);
    }

    /** Installs the FlatLaf for the given theme id (no UI refresh — call before windows exist). */
    public static void apply(String id) {
        switch (id == null ? DEFAULT : id) {
            case "light":
                FlatLightLaf.setup();
                break;
            case "maclight":
                FlatMacLightLaf.setup();
                break;
            case "macdark":
                FlatMacDarkLaf.setup();
                break;
            case "dark":
            default:
                FlatDarkLaf.setup();
                break;
        }
    }

    /** Installs a theme and restyles all open windows (does not persist — caller saves on commit). */
    public static void applyLive(String id) {
        apply(id);
        FlatLaf.updateUI();
    }

    /** Persists the chosen theme id to the shared prefs. */
    public static void save(String id) {
        StarMadeLogic.getProps().setProperty(KEY, id);
        StarMadeLogic.saveProps();
    }
}
