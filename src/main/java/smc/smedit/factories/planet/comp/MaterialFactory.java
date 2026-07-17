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
package smc.smedit.factories.planet.comp;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.logic.utils.IntegerUtils;
import smc.smedit.logic.utils.ResourceUtils;
import smc.smedit.logic.utils.ShortUtils;
import smc.smedit.logic.utils.StringUtils;
import smc.smedit.logic.utils.XMLUtils;
import smc.smedit.mods.IStarMadePlugin;
import smc.smedit.mods.IStarMadePluginFactory;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.util.Paths;

import org.w3c.dom.Document;
import org.w3c.dom.Node;


public class MaterialFactory implements IStarMadePluginFactory {
    private static final Logger log = Logger.getLogger(MaterialFactory.class.getName());

    private final List<MaterialDefinition> mDefs;
    private final List<IStarMadePlugin> mPlugins;

    public MaterialFactory() {
        this.mPlugins = new ArrayList<>();
        this.mDefs = new ArrayList<>();
        loadDefinitions();
        loadPlugins();
    }

    @Override
    public IStarMadePlugin[] getPlugins() {
        return mPlugins.toArray(new IStarMadePlugin[0]);
    }

    private void loadPlugins() {
        for (MaterialDefinition fd : mDefs) {
            mPlugins.add(new MaterialPlugin(fd));
        }
    }

    private void loadDefinitions() {
        File file = new File(Paths.getPluginsDirectory(), "material_composition.xml");
        Document xml = null;
        if (file.exists()) {
            xml = XMLUtils.readFile(file);
        } else {
            InputStream is = ResourceUtils.loadSystemResourceStream("material_composition.xml", MaterialFactory.class);
            if (is != null) {
                xml = XMLUtils.readStream(is);
            }
        }
        if (xml == null) {
            log.log(Level.WARNING, "material_composition.xml not found; no material-composition plugins loaded");
            return;
        }
        loadDefinitions(xml);
    }

    private void loadDefinitions(Document xml) {
        BlockTypeColors.loadBlockIcons();
        Node fs = XMLUtils.findFirstNode(xml, "compositions");
        String baseAuthor = XMLUtils.getAttribute(fs, "author");
        for (Node f : XMLUtils.findNodes(fs, "composition")) {
            String title = XMLUtils.getAttribute(f, "title");
            String desc = XMLUtils.getAttribute(f, "description");
            String author = XMLUtils.getAttribute(f, "author");
            int priority = IntegerUtils.parseInt(XMLUtils.getAttribute(f, "priority"));
            if (StringUtils.isTrivial(author)) {
                author = baseAuthor;
            }
            if (StringUtils.isTrivial(title)) {
                continue;
            }
            MaterialDefinition def = new MaterialDefinition();
            def.setTitle(title);
            def.setDescription(desc);
            def.setAuthor(author);
            def.setPriority(priority);
            for (Node ob : XMLUtils.findNodes(f, "oldBlock")) {
                MaterialEntry entry = parseEntry(ob);
                if (entry == null) {
                    continue;
                }
                if ((entry.getPercent() <= 0) || (entry.getPercent() > 100)) {
                    log.log(Level.WARNING, "Old block must have percentage between 1 and 100: " + XMLUtils.writeString(ob));
                    continue;
                }
                def.getOldBlocks().add(entry);
            }
            for (Node nb : XMLUtils.findNodes(f, "newBlock")) {
                MaterialEntry entry = parseEntry(nb);
                if (entry == null) {
                    continue;
                }
                if (entry.getBlockID() <= 0) {
                    log.log(Level.WARNING, "New block must have specific type: " + XMLUtils.writeString(nb));
                    continue;
                }
                if (entry.getPercent() <= 0) {
                    entry.setPercent(1);
                }
                def.getNewBlocks().add(entry);
            }
            mDefs.add(def);
        }
    }

    private MaterialEntry parseEntry(Node b) {
        MaterialEntry entry = new MaterialEntry();
        String id = XMLUtils.getAttribute(b, "type");
        if ("*".equals(id)) {
            entry.setBlockID((short) -1);
        } else if (BlockTypeColors.mBlockTypes.containsKey(id)) {
            entry.setBlockID(ShortUtils.parseShort(BlockTypeColors.mBlockTypes.get(id)));
        } else {
            short val = ShortUtils.parseShort(id);
            if (val > 0) {
                entry.setBlockID(val);
            } else {
                log.log(Level.WARNING, "Unknown block type '" + id + "' : " + XMLUtils.writeString(b));
                return null;
            }
        }
        entry.setPercent(IntegerUtils.parseInt(XMLUtils.getAttribute(b, "percent")));
        int[] low = parseRange(XMLUtils.getAttribute(b, "low"));
        if (low == null) {
            return null;
        }
        entry.setLowStrategy(low[0]);
        entry.setLowValue(low[1]);
        int[] high = parseRange(XMLUtils.getAttribute(b, "high"));
        if (high == null) {
            return null;
        }
        entry.setHighStrategy(high[0]);
        entry.setHighValue(high[1]);
        return entry;
    }

    private int[] parseRange(String range) {
        int[] ret = new int[2];
        if (range.endsWith("%")) {
            ret[0] = MaterialEntry.ABSOLUTE;
            ret[1] = Integer.parseInt(range.substring(0, range.length() - 1));
        } else {
            ret[0] = MaterialEntry.RELATIVE;
            ret[1] = Integer.parseInt(range);
        }
        return ret;
    }
}
