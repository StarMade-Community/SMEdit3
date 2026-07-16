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
package smc.smedit.plugins.ship.replace;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;


public class ReplacePlugin implements IBlocksPlugin {

    public static final String NAME = "Replace";
    public static final String DESC = "Replace one color on your ship with another.";
    public static final String AUTH = "Jo Jaquinta";
    public static final int[][] CLASSIFICATIONS
            = {
                {TYPE_SHIP, SUBTYPE_PAINT},
                {TYPE_STATION, SUBTYPE_PAINT},
                {TYPE_SHOP, SUBTYPE_PAINT},};
    private static final Logger log = Logger.getLogger(ReplacePlugin.class.getName());

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public String getAuthor() {
        return AUTH;
    }

    @Override
    public Object newParameterBean() {
        return new ReplaceParameters();
    }

    @Override
    public void initParameterBean(SparseMatrix<Block> original, Object params,
            StarMade sm, IPluginCallback cb) {
    }

    @Override
    public int[][] getClassifications() {
        return CLASSIFICATIONS;
    }

    @Override
    public SparseMatrix<Block> modify(SparseMatrix<Block> original,
            Object p, StarMade sm, IPluginCallback cb) {
        ReplaceParameters params;
        params = (ReplaceParameters) p;
        log.log(Level.WARNING, "Params: color1="+params.getColor1()+", color2="+params.getColor2());
        cb.setStatus("Replacing colors");
        cb.startTask(original.size());
        SparseMatrix<Block> modified;
        modified = new SparseMatrix<>();
        for (Iterator<Point3i> i = original.iteratorNonNull(); i.hasNext();) {
            Point3i xyz;
            xyz = i.next();
            Block b;
            b = original.get(xyz);
            if (BlockGroups.isAnyHull(b.getBlockID())) {
                b = modify(xyz, b, params);
            }
            modified.set(xyz, b);
            cb.workTask(1);
        }
        cb.endTask();
        return modified;
    }

    private Block modify(Point3i xyz, Block b, ReplaceParameters params) {
        short oldColor;
        oldColor = BlockGroups.getColor(b.getBlockID());
        if (oldColor == params.getColor1()) {
            short newColor;
            newColor = BlockGroups.getColoredBlock(b.getBlockID(), params.getColor2());
            if (newColor >= 0) {
                b.setBlockID(newColor);
            }
        }
        return b;
    }
}
