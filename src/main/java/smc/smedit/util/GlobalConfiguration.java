/**
 * Copyright 2014
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package smc.smedit.util;

import smc.smedit.log.LogFormatter;
import smc.smedit.log.SystemConsoleHandler;
import smc.smedit.log.TextAreaLogHandler;

import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * Handles the configuration of the main app layout and logic start up
 *
 * @author Robert Barefoot for SMEdit - version 1.0
 */
@SuppressWarnings({"CallToPrintStackTrace", "null"})
public class GlobalConfiguration {

    public static final String NAME = "SMEdit";
    public static final String VERSION = "3.0.0"; //Todo: Move to some sort of config file idk
    private static final Logger log = Logger.getLogger(GlobalConfiguration.class.getName());
    private static final OperatingSystem CURRENT_OS;
    private static boolean RUNNING_FROM_JAR;

    static {
        URL resource = GlobalConfiguration.class.getClassLoader().getResource(Resources.SVERSION);
        if (resource != null) {
            setRUNNING_FROM_JAR(true);

        }
        String os = System.getProperty("os.name");
        if (os.contains("Mac")) {
            CURRENT_OS = OperatingSystem.MAC;
        } else if (os.contains("Windows")) {
            CURRENT_OS = OperatingSystem.WINDOWS;
        } else if (os.contains("Linux")) {
            CURRENT_OS = OperatingSystem.LINUX;
        } else {
            CURRENT_OS = OperatingSystem.UNKNOWN;
        }

        if (isRUNNING_FROM_JAR()) {
            String path;
            path = resource.toString();
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
            final String prefix = "jar:file:/";
            if (path.indexOf(prefix) == 0) {
                path = path.substring(prefix.length());
                path = path.substring(0, path.indexOf('!'));
                if (File.separatorChar != '/') {
                    path = path.replace('/', File.separatorChar);
                }
                try {
                    File pathfile = new File(Paths.getPathCache());
                    if (pathfile.exists()) {
                        pathfile.delete();
                    }
                    pathfile.createNewFile();
                    try (Writer out = new BufferedWriter(new FileWriter(Paths.getPathCache(), StandardCharsets.UTF_8))) {
                        out.write(path);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void createDirectories() {
        ArrayList<String> dirs;
        dirs = new ArrayList<>(11);
        dirs.add(Paths.getHomeDirectory());
        dirs.add(Paths.getResourceDirectory());
        dirs.add(Paths.getIconDirectory());
        dirs.add(Paths.getLogsDirectory());
        dirs.add(Paths.getScreenshotsDirectory());
        dirs.add(Paths.getPluginsDirectory());
        dirs.add(Paths.getShapeLibraryDirectory());
        dirs.add(Paths.getCacheDirectory());
        dirs.add(Paths.getSettingsDirectory());
        // NOTE: deliberately not creating a blueprint folder here — the old code
        // created an empty "Omen-Navy-Class" blueprint in the install's
        // blueprints-default, which then loaded as a broken default ship.
        for (String name : dirs) {
            File dir = new File(name);
            if (!dir.exists() && !dir.mkdirs()) {
                log.log(Level.WARNING, "Could not create application directory: {0}", dir);
            }
        }
    }

    public static OperatingSystem getCurrentOperatingSystem() {
        return CURRENT_OS;
    }

    public static Image getImage(String resource) {
        try {
            return Toolkit.getDefaultToolkit().getImage(getResourceURL(resource));
        } catch (MalformedURLException e) {
        }
        return null;
    }

    /**
     *
     * @param path gets the file location on the web to the needed resource
     * @return
     * @throws MalformedURLException
     */
    public static URL getResourceURL(String path) throws MalformedURLException {
        return RUNNING_FROM_JAR ? GlobalConfiguration.class.getResource(path) : new File(path).toURI().toURL();
    }

    public static void registerLogging() {
        Properties logging = new Properties();
        String logFormatter = LogFormatter.class.getCanonicalName();
        String fileHandler = FileHandler.class.getCanonicalName();
        logging.setProperty("handlers", TextAreaLogHandler.class.getCanonicalName() + "," + fileHandler);
        logging.setProperty(".level", "CONFIG");
        logging.setProperty(SystemConsoleHandler.class.getCanonicalName() + ".formatter", logFormatter);
        logging.setProperty(fileHandler + ".formatter", logFormatter);
        logging.setProperty(TextAreaLogHandler.class.getCanonicalName() + ".formatter", logFormatter);
        logging.setProperty(fileHandler + ".pattern", Paths.getLogsDirectory() + File.separator + "%u.%g.log");
        logging.setProperty(fileHandler + ".count", "10");
        ByteArrayOutputStream logout = new ByteArrayOutputStream();
        try {
            logging.store(logout, "");
            LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(logout.toByteArray()));
        } catch (IOException | SecurityException ignored) {
        }
    }

    /**
     * @return the RUNNING_FROM_JAR
     */
    public static boolean isRUNNING_FROM_JAR() {
        return RUNNING_FROM_JAR;
    }

    /**
     * @param aRUNNING_FROM_JAR the RUNNING_FROM_JAR to set
     */
    public static void setRUNNING_FROM_JAR(boolean aRUNNING_FROM_JAR) {
        RUNNING_FROM_JAR = aRUNNING_FROM_JAR;
    }


}
