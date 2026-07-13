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
 */
package smc.smedit.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import smc.smedit.ui.StarMadeDirChooser;

/**
 *
 * @author Robert Barefoot
 */
public class Paths {

    public static final String ROOT = ".";
    public static final String RESOURCES = ROOT + File.separator + "resources";
    public static final String SVERSION = RESOURCES + File.separator + "start_version.dat";
    private static final Logger log = Logger.getLogger(Paths.class.getName());
    /* file locations */
    private static Properties mProps;
    private static File mStarMadeDir;

    public static String getCollectDirectory() {
        final File dir = new File(Paths.getPluginsDirectory(), ".jar");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String path = dir.getAbsolutePath();
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (final UnsupportedEncodingException ignored) {
        }
        return path;
    }

    /* folder directories */

    public static String getHomeDirectory() {
        Properties props = getProps();
        String home = props.getProperty("starmade.home", "");
        return home + File.separator + "third-party" + File.separator + GlobalConfiguration.NAME;

    }

    public static String getSMEBlueprintDirectory() {
        Properties props = getProps();
        String home = props.getProperty("starmade.home", "");
        return home + File.separator + "blueprints-default";
    }

    public static String getIsanthDirectory() {
        return Paths.getSMEBlueprintDirectory() + File.separator + "Omen-Navy-Class";
    }

    public static String getIsanthDataDirectory() {
        return Paths.getIsanthDirectory() + File.separator + "DATA";
    }

    public static String getCacheDirectory() {
        return Paths.getHomeDirectory() + File.separator + "Cache";
    }

    public static String getLogsDirectory() {
        return Paths.getHomeDirectory() + File.separator + "Logs";
    }

    public static String getPluginsDirectory() {
        return Paths.getHomeDirectory() + File.separator + "Plugins";
    }

    public static String getResourceDirectory() {
        return Paths.getHomeDirectory() + File.separator + "resources";
    }

    public static String getScreenshotsDirectory() {
        return Paths.getHomeDirectory() + File.separator + "Screenshots";
    }

    public static String getSettingsDirectory() {
        return Paths.getHomeDirectory() + File.separator + "Settings";
    }

    public static String getIconDirectory() {
        return Paths.getResourceDirectory() + File.separator + "images";
    }

    public static String getShapeLibraryDirectory() {
        return Paths.getPluginsDirectory() + File.separator + "shapeLibrary";
    }

    public static String getPathCache() {
        return Paths.getSettingsDirectory() + File.separator + "start_path.txt";
    }

    public static String getUnixHome() {
        final String home = System.getProperty("user.home");
        return home == null ? "~" : home;
    }

    public static Properties getProperties() {
        return mProps;
    }

    public static Properties getProps() {
        if (!validateCurrentDirectory()) {
            Properties p = new Properties();
            File home = new File(System.getProperty("user.home"));
            File props = new File(home, ".josm");
            if (props.exists()) {
                try {
                    try (FileInputStream fis = new FileInputStream(props)) {
                        mProps.load(fis);
                    }
                } catch (IOException e) {

                }
            } else {
                mProps = new Properties();
            }
            saveProps();
        }
        return getProperties();
    }

    private static boolean isStarMadeDirectory(File d) {
        // A StarMade install is marked by StarMade.jar. (The old code also
        // required CrashAndBugReport.jar, which modern StarMade may not ship and
        // which disagreed with StarMadeLogic#isStarMadeDirectory — now consistent.)
        return d != null && d.isDirectory() && new File(d, "StarMade.jar").exists();
    }

    public static void loadProps() {
        File home = new File(System.getProperty("user.home"));
        File props = new File(home, ".josm");
        if (props.exists()) {
            mProps = new Properties();
            try {
                try (FileInputStream fis = new FileInputStream(props)) {
                    mProps.load(fis);
                }
            } catch (IOException e) {

            }
        } else {
            mProps = new Properties();
        }
    }

    /**
     * Resolves the StarMade installation directory and records it in
     * {@code ~/.josm}. Resolution order (no full-disk scan): the previously
     * saved / manually chosen folder, the current working directory, a short
     * list of common install locations, and finally a folder picker for manual
     * selection. Returns {@code false} only if the user cancels the picker.
     */
    public static boolean validateCurrentDirectory() {
        loadProps();

        // 1. Previously saved (or manually chosen) install.
        String saved = mProps.getProperty("starmade.home", "");
        if (!saved.isEmpty() && isStarMadeDirectory(new File(saved))) {
            mStarMadeDir = new File(saved);
            return true;
        }

        // 2. Launched from inside the install?
        File cwd = new File(System.getProperty("user.dir"));
        if (isStarMadeDirectory(cwd)) {
            mStarMadeDir = cwd;
            saveProps();
            return true;
        }

        // 3. A few common install locations (bounded; no recursive scan).
        for (File candidate : commonInstallLocations()) {
            if (isStarMadeDirectory(candidate)) {
                mStarMadeDir = candidate;
                saveProps();
                return true;
            }
        }

        // 4. Ask the user to point us at it (manual input).
        File chosen = StarMadeDirChooser.choose(null, mStarMadeDir);
        if (chosen != null) {
            mStarMadeDir = chosen;
            saveProps();
            return true;
        }
        mStarMadeDir = null;
        return false;
    }

    /** Well-known StarMade install locations, checked before prompting. */
    private static List<File> commonInstallLocations() {
        List<File> list = new ArrayList<>();
        String userHome = getUnixHome();
        String os = System.getProperty("os.name", "").toLowerCase();
        list.add(new File(userHome, "StarMade"));
        list.add(new File(userHome, "starmade"));
        if (os.contains("win")) {
            list.add(new File("C:/Program Files (x86)/Steam/steamapps/common/StarMade"));
            list.add(new File("C:/Program Files/Steam/steamapps/common/StarMade"));
            list.add(new File("C:/StarMade"));
        } else if (os.contains("mac")) {
            list.add(new File(userHome, "Library/Application Support/Steam/steamapps/common/StarMade"));
            list.add(new File(userHome, "Library/Application Support/StarMade"));
        } else {
            list.add(new File(userHome, ".steam/steam/steamapps/common/StarMade"));
            list.add(new File(userHome, ".local/share/Steam/steamapps/common/StarMade"));
            list.add(new File(userHome, ".local/share/StarMade"));
        }
        return list;
    }

    private static void saveProps() {
        if (mProps == null) {
            return;
        }
        if (mStarMadeDir != null) {
            mProps.put("starmade.home", mStarMadeDir.toString());
        }
        File home = new File(System.getProperty("user.home"));
        File props = new File(home, ".josm");
        try {
            try (FileWriter fos = new FileWriter(props)) {
                mProps.store(fos, "StarMade Utils defaults");
            }
        } catch (IOException e) {

        }
    }

    private Paths() {
        mProps = new Properties();
    }

}
