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
package smc.smedit.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.logic.utils.BooleanUtils;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IStarMadePlugin;
import smc.smedit.mods.IStarMadePluginFactory;
import smc.smedit.ship.data.Block;
import smc.smedit.util.Paths;


public class StarMadeLogic {

    public static final String INVERT_X_AXIS = "InvertXAxis";
    public static final String INVERT_Y_AXIS = "InvertYAxis";

    private static StarMade mStarMade;
    private static final Logger log = Logger.getLogger(StarMadeLogic.class.getName());

    public static synchronized StarMade getInstance() {
        if (mStarMade == null) {
            mStarMade = new StarMade();
        }
        return mStarMade;
    }

    public static void setBaseDir(String baseDir) {
        File bd = new File(baseDir);
        if (!bd.exists()) {
            throw new IllegalArgumentException("Base directory '" + baseDir + "' does not exist");
        }
        getInstance().setBaseDir(bd);

        File ntive = new File(bd, "native");
        File libs = null;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            libs = new File(ntive, "windows");
        } else if (!os.contains("mac")) if (os.contains("linux")) {
            libs = new File(ntive, "linux");
        } else if (os.contains("solaris")) {
            libs = new File(ntive, "solaris");
        } else {
            libs = new File(ntive, "macosx");
        }
        if (libs != null && libs.isDirectory()) {
            // Point LWJGL at StarMade's native libraries via org.lwjgl.librarypath
            // -- LWJGL's own property, read lazily when the native is first
            // loaded, so setting it here (before the renderer initializes) works.
            // We deliberately do NOT touch java.library.path: the JVM parses that
            // once at startup and it cannot be changed at runtime (the old code's
            // ClassLoader.sys_paths reflection hack for that threw on Java 9+).
            //
            // Using the install's natives is both correct and necessary: StarMade
            // ships LWJGL 2.9.3 natives rebuilt for Java 9+, while the stock
            // LWJGL 2.x natives still link the removed libjawt SUNWprivate_1.1
            // symbol and fail to load on the Java 21 toolchain. This also
            // guarantees the editor's OpenGL renderer matches the game exactly.
            System.setProperty("org.lwjgl.librarypath", libs.getAbsolutePath());
            log.log(Level.INFO, "LWJGL native path -> {0}", libs.getAbsolutePath());
        }

        // Register the plugins that ship inside SMEdit itself (edit/import/
        // export/generate tools). Historically only external plugin JARs were
        // discovered, so none of the built-in tools ever reached the menus.
        smc.smedit.plugins.BuiltinPlugins.register();

        List<String> blocksPlugins = new ArrayList<>();
        List<String> pluginFactories = new ArrayList<>();
        discoverPlugins(baseDir, blocksPlugins, pluginFactories);
        loadPlugins(blocksPlugins, pluginFactories);
    }

    private static void loadPlugins(List<String> blocksPlugins, List<String> pluginFactories) {
        for (String blocksPluginClassName : blocksPlugins) {
            addBlocksPlugin(blocksPluginClassName);
        }
        for (String pluginFactoryClassName : pluginFactories) {
            addPluginFactory(pluginFactoryClassName);
        }
    }

    public static boolean addBlocksPlugin(String blocksPluginClassName) {
        try {
            Class<?> blocksPluginClass = getInstance().getModLoader().loadClass(blocksPluginClassName);
            if (blocksPluginClass == null) {
                log.log(Level.WARNING, "No such class: " + blocksPluginClassName);
                return false;
            }
            IBlocksPlugin plugin = (IBlocksPlugin) blocksPluginClass.newInstance();
            if (plugin == null) {
                log.log(Level.WARNING, "Can't instantiate class: " + blocksPluginClassName);
                return false;
            }
            addBlocksPlugin(plugin);
            return true;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log.log(Level.WARNING, "plugin failed!", e);
            return false;
        }

    }

    public static boolean addPluginFactory(String pluginFactoryClassName) {
        try {
            Class<?> pluginFactoryClass = getInstance().getModLoader().loadClass(pluginFactoryClassName);
            if (pluginFactoryClass == null) {
                return false;
            }
            IStarMadePluginFactory factory = (IStarMadePluginFactory) pluginFactoryClass.newInstance();
            if (factory == null) {
                return false;
            }
            getInstance().getPluginFactories().add(factory);
            return true;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log.log(Level.WARNING, "pluginFactory failed!", e);
            return false;
        }

    }

    public static void addBlocksPlugin(IBlocksPlugin plugin) {
        getInstance().getBlocksPlugins().add(plugin);
    }

    private static void discoverPlugins(String baseDir,
            List<String> blocksPlugins, List<String> pluginFactories) {
        List<URL> urls = new ArrayList<>();
        File pluginDir = new File(Paths.getPluginsDirectory());
        if (pluginDir.exists()) {
            for (File jar : pluginDir.listFiles()) {
                if (jar.getName().endsWith(".jar")) {
                    try {
                        URL url = jar.toURI().toURL();
                        urls.add(url);
                        URL jarURL = new URL("jar:" + url.toString() + "!/");
                        JarURLConnection jarConnection = (JarURLConnection) jarURL.openConnection();
                        Manifest manifest = jarConnection.getManifest();
                        String plugins = manifest.getMainAttributes().getValue("BlocksPlugins");
                        if (plugins != null) {
                            for (StringTokenizer st = new StringTokenizer(plugins, ","); st.hasMoreTokens();) {
                                String plugin = st.nextToken();
                                blocksPlugins.add(plugin);
                                //System.out.println("BLock Plugin = "+plugin);
                            }
                        }
                        String factories = manifest.getMainAttributes().getValue("PluginFactories");
                        if (factories != null) {
                            for (StringTokenizer st = new StringTokenizer(factories, ","); st.hasMoreTokens();) {
                                String factory = st.nextToken();
                                pluginFactories.add(factory);
                                //System.out.println("Plugin Factory = "+factory);
                            }
                        }
                    } catch (IOException e) {
                        log.log(Level.WARNING, "plugins failed!", e);
                    }
                }
            }
        }
        // instantiate plugins
        URLClassLoader modLoader = new URLClassLoader(urls.toArray(new URL[0]), StarMadeLogic.class.getClassLoader());
        getInstance().setModLoader(modLoader);
    }

    public static List<IBlocksPlugin> getAllBlocksPlugins() {
        List<IBlocksPlugin> plugins = new ArrayList<>();
        plugins.addAll(getInstance().getBlocksPlugins());
        for (IStarMadePluginFactory factory : getInstance().getPluginFactories()) {
            IStarMadePlugin[] plugs;
            try {   // a factory may scan disk and fail; don't let it break the menus
                plugs = factory.getPlugins();
            } catch (Exception e) {
                log.log(Level.WARNING, "plugin factory getPlugins() failed", e);
                continue;
            }
            if (plugs == null) {
                continue;
            }
            for (IStarMadePlugin plugin : plugs) {
                if (plugin instanceof IBlocksPlugin) {
                    plugins.add((IBlocksPlugin) plugin);
                }
            }
        }
        return plugins;
    }

    public static List<IBlocksPlugin> getBlocksPlugins(int type, int subtype) {
        List<IBlocksPlugin> plugins = new ArrayList<>();
        for (IBlocksPlugin plugin : getAllBlocksPlugins()) {
            if (isPlugin(plugin, type, subtype)) {
                plugins.add(plugin);
            }
        }
        return plugins;
    }

    private static boolean isPlugin(IStarMadePlugin plugin, int type, int subtype) {
        try {   // lets be protective against badly written plugins
            for (int[] classification : plugin.getClassifications()) {
                if ((type == IBlocksPlugin.TYPE_ALL) || (IBlocksPlugin.TYPE_ALL == classification[0]) || (type == classification[0])) {
                    if ((subtype == -1) || (subtype == classification[1])) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "plugins failed!", e);
        }
        return false;
    }

    public static boolean isStarMadeDirectory(String dir) {
        File d = new File(dir);
        if (!d.exists()) {
            return false;
        }
        File smJar = new File(d, "StarMade.jar");
        return smJar.exists();
    }

    public static Properties getProps() {
        if (getInstance().getProps() == null) {
            Properties p = new Properties();
            File home = new File(System.getProperty("user.home"));
            File props = new File(home, ".josm");
            if (props.exists()) {
                try {
                    try (FileInputStream fis = new FileInputStream(props)) {
                        p.load(fis);
                    }
                } catch (IOException e) {
                    log.log(Level.WARNING, "props failed!", e);

                }
            }
            getInstance().setProps(p);
        }
        return getInstance().getProps();
    }

    public static void saveProps() {
        if (getInstance().getProps() == null) {
            return;
        }
        File home = new File(System.getProperty("user.home"));
        File props = new File(home, ".josm");
        try {
            try (FileWriter fos = new FileWriter(props)) {
                getInstance().getProps().store(fos, "StarMade Utils defaults");
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "saveProps failed!", e);

        }
    }

    public static String getProperty(String key) {
        return getProps().getProperty(key);
    }

    public static boolean isProperty(String key) {
        return BooleanUtils.parseBoolean(getProperty(key));
    }

    public static void setProperty(String key, String value) {
        getProps().setProperty(key, value);
        saveProps();
    }

    public static void setProperty(String key, boolean value) {
        setProperty(key, String.valueOf(value));
    }

    public static boolean isClassification(int class1, int class2) {
        return ((class1 == IBlocksPlugin.TYPE_ALL) || (class1 == class2));
    }

    public static SparseMatrix<Block> getModel() {
        return getInstance().getModel();
    }

    public static void setModel(SparseMatrix<Block> model) {
        getInstance().setModel(model);
    }
}
