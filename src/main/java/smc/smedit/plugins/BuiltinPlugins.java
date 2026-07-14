/**
 * Copyright 2014 SMEdit https://github.com/StarMade/SMEdit SMTools
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
package smc.smedit.plugins;

import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IStarMadePluginFactory;

/**
 * Registers the in-tree ("built-in") plugins and factories with
 * {@link StarMadeLogic}.
 *
 * <p>Discovery uses {@link java.util.ServiceLoader}: the concrete classes are
 * listed in {@code META-INF/services/smc.smedit.mods.IBlocksPlugin} and
 * {@code META-INF/services/smc.smedit.mods.IStarMadePluginFactory}. Adding a new
 * plugin is a one-line edit to the relevant service file — no code change here.
 * This replaces the original model where the plugins lived in a separate
 * {@code JoFileMods.jar} downloaded at runtime, and the later hand-maintained
 * list of {@code new XxxPlugin()} calls.
 *
 * <p>Each provider is instantiated in isolation: a bad provider (missing no-arg
 * constructor, constructor that throws) is logged and skipped so it can never
 * stop the rest from loading.
 */
public final class BuiltinPlugins {

    private static final Logger log = Logger.getLogger(BuiltinPlugins.class.getName());

    private static boolean registered = false;

    private BuiltinPlugins() {
    }

    /**
     * Discovers and registers every built-in plugin and factory via
     * {@link ServiceLoader}. Safe to call more than once (subsequent calls are
     * no-ops), since the startup path may set the base directory repeatedly.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        int plugins = registerAll(ServiceLoader.load(IBlocksPlugin.class),
                StarMadeLogic::addBlocksPlugin, "block plugin");
        int factories = registerAll(ServiceLoader.load(IStarMadePluginFactory.class),
                f -> StarMadeLogic.getInstance().getPluginFactories().add(f), "factory");

        log.log(Level.INFO, "Registered {0} built-in block plugins and {1} factories",
                new Object[] {plugins, factories});
    }

    /**
     * Streams a {@link ServiceLoader} defensively, registering each provider and
     * isolating failures: {@code provider.get()} throws a
     * {@code ServiceConfigurationError} if that one provider's class won't load or
     * its constructor throws, which is caught so the rest still load.
     *
     * @return the number of providers successfully registered
     */
    private static <T> int registerAll(ServiceLoader<T> loader, Consumer<T> sink, String kind) {
        int[] count = {0};
        try {
            loader.stream().forEach(provider -> {
                try {
                    sink.accept(provider.get());
                    count[0]++;
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Skipping built-in " + kind + " that failed to load", t);
                }
            });
        } catch (Throwable t) {
            log.log(Level.WARNING, "Error iterating built-in " + kind + " providers", t);
        }
        return count[0];
    }
}
