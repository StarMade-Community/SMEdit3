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

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.factories.all.macro.MacroFactory;
import smc.smedit.factories.planet.comp.MaterialFactory;
import smc.smedit.factories.planet.veg.VegetationFactory;
import smc.smedit.factories.ship.filter.ViewFilterFactory;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IStarMadePluginFactory;
import smc.smedit.plugins.all.macro.MacroDeletePlugin;
import smc.smedit.plugins.all.macro.MacroRecordPlugin;
import smc.smedit.plugins.all.macro.MacroRunPlugin;
import smc.smedit.plugins.all.props.PropsPlugin;
import smc.smedit.plugins.planet.gen.DomePlugin;
import smc.smedit.plugins.planet.gen.GiantsCausewayPlugin;
import smc.smedit.plugins.planet.gen.UndulatingPlugin;
import smc.smedit.plugins.planet.gen.VolcanoPlugin;
import smc.smedit.plugins.planet.hollow.HollowPlugin;
import smc.smedit.plugins.planet.info.ObjectReportPlugin;
import smc.smedit.plugins.planet.info.PluginReportPlugin;
import smc.smedit.plugins.planet.select.SelectAllPlugin;
import smc.smedit.plugins.planet.select.SelectCopyPlugin;
import smc.smedit.plugins.planet.select.SelectCopyToPlugin;
import smc.smedit.plugins.planet.select.SelectCutPlugin;
import smc.smedit.plugins.planet.select.SelectDeletePlugin;
import smc.smedit.plugins.planet.select.SelectNonePlugin;
import smc.smedit.plugins.planet.select.SelectPasteFromPlugin;
import smc.smedit.plugins.planet.select.SelectPastePlugin;
import smc.smedit.plugins.planet.select.SelectSpecificPlugin;
import smc.smedit.plugins.ship.edit.HardenPlugin;
import smc.smedit.plugins.ship.edit.SmoothPlugin;
import smc.smedit.plugins.ship.edit.SoftenPlugin;
import smc.smedit.plugins.ship.exp.ExportDAEPlugin;
import smc.smedit.plugins.ship.exp.ExportImagesPlugin;
import smc.smedit.plugins.ship.exp.ExportOBJPlugin;
import smc.smedit.plugins.ship.fill.DeckPlugin;
import smc.smedit.plugins.ship.fill.FillBlockPlugin;
import smc.smedit.plugins.ship.fill.FillPlugin;
import smc.smedit.plugins.ship.hull.HullPlugin;
import smc.smedit.plugins.ship.imp.ImportBinvoxPlugin;
import smc.smedit.plugins.ship.imp.ImportOBJPlugin;
import smc.smedit.plugins.ship.imp.ImportSchematicPlugin;
import smc.smedit.plugins.ship.imp.ImportVRMLPlugin;
import smc.smedit.plugins.ship.move.MovePlugin;
import smc.smedit.plugins.ship.reflect.DuplicatePlugin;
import smc.smedit.plugins.ship.reflect.ReflectPlugin;
import smc.smedit.plugins.ship.replace.ReplaceBlocksPlugin;
import smc.smedit.plugins.ship.replace.ReplacePlugin;
import smc.smedit.plugins.ship.rotate.RotatePlugin;
import smc.smedit.plugins.ship.scale.ScalePlugin;
import smc.smedit.plugins.ship.stripes.OmbrePlugin;
import smc.smedit.plugins.ship.stripes.StripesPlugin;
import smc.smedit.plugins.ship.text.ImagePlugin;
import smc.smedit.plugins.ship.text.TextPlugin;

/**
 * Registers the in-tree ("built-in") plugins and factories with
 * {@link StarMadeLogic}.
 *
 * <p>The original design only discovered plugins from external {@code .jar}
 * files dropped into the Plugins directory (see
 * {@code StarMadeLogic.discoverPlugins}); the plugins that ship inside SMEdit
 * itself were never registered, so none of the edit/import/export/generate
 * tools appeared in the menus. This class closes that gap by instantiating them
 * directly from the application classpath.
 *
 * <p>Each plugin is constructed in isolation and any failure (missing no-arg
 * constructor at runtime, exception in a constructor, etc.) is logged and
 * skipped so one bad plugin can never stop the rest from loading.
 */
public final class BuiltinPlugins {

    private static final Logger log = Logger.getLogger(BuiltinPlugins.class.getName());

    private static boolean registered = false;

    private BuiltinPlugins() {
    }

    /** The built-in block plugins, as isolated no-arg factories. */
    private static final List<Supplier<IBlocksPlugin>> BLOCK_PLUGINS = List.of(
            // Ship — edit
            HardenPlugin::new, SmoothPlugin::new, SoftenPlugin::new,
            // Ship — transform
            RotatePlugin::new, ReflectPlugin::new, DuplicatePlugin::new,
            MovePlugin::new, ScalePlugin::new,
            // Ship — fill
            FillPlugin::new, FillBlockPlugin::new, DeckPlugin::new,
            // Ship — hull
            HullPlugin::new,
            // Ship — paint
            StripesPlugin::new, OmbrePlugin::new, TextPlugin::new, ImagePlugin::new,
            // Ship — replace
            ReplacePlugin::new, ReplaceBlocksPlugin::new,
            // Ship — import
            ImportOBJPlugin::new, ImportBinvoxPlugin::new,
            ImportVRMLPlugin::new, ImportSchematicPlugin::new,
            // Ship — export
            ExportOBJPlugin::new, ExportDAEPlugin::new, ExportImagesPlugin::new,
            // Planet — generate
            DomePlugin::new, VolcanoPlugin::new, UndulatingPlugin::new,
            GiantsCausewayPlugin::new,
            // Planet — modify
            HollowPlugin::new,
            // Planet — selection
            SelectAllPlugin::new, SelectNonePlugin::new, SelectSpecificPlugin::new,
            SelectCopyPlugin::new, SelectCutPlugin::new, SelectDeletePlugin::new,
            SelectPastePlugin::new, SelectCopyToPlugin::new, SelectPasteFromPlugin::new,
            // Planet — info
            ObjectReportPlugin::new, PluginReportPlugin::new,
            // Universal — macros / props
            MacroRecordPlugin::new, MacroRunPlugin::new, MacroDeletePlugin::new,
            PropsPlugin::new);

    /** The built-in plugin factories (dynamic/discovered plugins), as isolated no-arg factories. */
    private static final List<Supplier<IStarMadePluginFactory>> FACTORIES = List.of(
            MacroFactory::new, MaterialFactory::new,
            VegetationFactory::new, ViewFilterFactory::new);

    /**
     * Instantiates and registers every built-in plugin and factory. Safe to call
     * more than once (subsequent calls are no-ops), since the startup path may
     * set the base directory repeatedly.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        int plugins = 0;
        for (Supplier<IBlocksPlugin> supplier : BLOCK_PLUGINS) {
            try {
                StarMadeLogic.addBlocksPlugin(supplier.get());
                plugins++;
            } catch (Throwable t) {
                log.log(Level.WARNING, "Skipping built-in block plugin that failed to load", t);
            }
        }

        int factories = 0;
        for (Supplier<IStarMadePluginFactory> supplier : FACTORIES) {
            try {
                StarMadeLogic.getInstance().getPluginFactories().add(supplier.get());
                factories++;
            } catch (Throwable t) {
                log.log(Level.WARNING, "Skipping built-in plugin factory that failed to load", t);
            }
        }

        log.log(Level.INFO, "Registered {0} built-in block plugins and {1} factories",
                new Object[] {plugins, factories});
    }
}
