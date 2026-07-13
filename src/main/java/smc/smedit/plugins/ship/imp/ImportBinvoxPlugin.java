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
package smc.smedit.plugins.ship.imp;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.data.BlockTypes;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IPluginCallback;
import static smc.smedit.plugins.ship.imp.BinvoxLogic.getVoxel;
import smc.smedit.plugins.ship.move.MovePlugin;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.logic.ShipLogic;
import smc.smedit.vecmath.Point3i;

/**
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 **/
public class ImportBinvoxPlugin implements IBlocksPlugin {

    public static final String NAME = "Import/Binvox";
    public static final String DESC = "Import Binvox file";
    public static final String AUTH = "Jo Jaquinta";
    public static final int[][] CLASSIFICATIONS
            = {
                {TYPE_SHIP, SUBTYPE_FILE, 25},};
    private static final Logger log = Logger.getLogger(ImportBinvoxPlugin.class.getName());

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
        return new ImportBinvoxParameters();
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
        ImportBinvoxParameters params;
        params = (ImportBinvoxParameters) p;
        short color;
        color = sm.getSelectedBlockType();
        if (color <= 0) {
            color = BlockTypes.HULL_COLOR_GREY_ID;
        }
        try {
            FileInputStream fis;
            fis = new FileInputStream(params.getFile());
            BinvoxData hull;
            hull = BinvoxLogic.readHeader(fis);
            if (hull == null) {
                return null;
            }
            cb.setStatus("Converting " + hull.getYSpan() + "x" + hull.getXSpan() + "x" + hull.getZSpan());
            SparseMatrix<Block> modified;
            modified = new SparseMatrix<>();
            mapHull(modified, hull, cb, color);
            cb.setStatus("Centering hull");
            Point3i lower;
            lower = new Point3i();
            Point3i upper;
            upper = new Point3i();
            modified.getBounds(lower, upper);
            log.log(Level.INFO, "Old bounds="+lower+" -- "+upper);
            int dx = (upper.x + lower.x) / 2 - 8;
            int dy = (upper.y + lower.y) / 2 - 8;
            int dz = (upper.z + lower.z) / 2 - 8;
            log.log(Level.INFO, "Move "+dx+","+dy+","+dz);
            modified = MovePlugin.shift(modified, dx, dy, dz, cb);
            // setting core
            ShipLogic.ensureCore(modified);
            modified.getBounds(lower, upper);
            log.log(Level.INFO, "New bounds="+lower+" -- "+upper);
            Point3i core = MovePlugin.findCore(modified);
            log.log(Level.INFO, "Core="+core);
            return modified;
        } catch (IOException e) {
            cb.setError(e);
            return null;
        }
    }

    private void mapHull(SparseMatrix<Block> modified, BinvoxData hull, IPluginCallback cb, short color) throws IOException {
        cb.startTask(hull.getZSpan());

        for (int x = 0; x < hull.getXSpan(); x++) {
            cb.workTask(1);
            for (int z = 0; z < hull.getZSpan(); z++) {
                for (int y = 0; y < hull.getYSpan(); y++) {
                    if (getVoxel(hull, x, y, z) == false) {
                        continue;
                    }
                    // if surrounded, skip
                    if (getVoxel(hull, x - 1, y, z)
                            && getVoxel(hull, x + 1, y, z)
                            && getVoxel(hull, x, y - 1, z)
                            && getVoxel(hull, x, y + 1, z)
                            && getVoxel(hull, x, y, z - 1)
                            && getVoxel(hull, x, y, z + 1)) {
                        continue;
                    }
                    Block b = new Block();
                    b.setBlockID(color);
                    modified.set(x, y, z, b);
                }
            }
            log.log(Level.INFO, modified.size()+" blocks  "+Runtime.getRuntime().freeMemory());
            if (cb.isPleaseCancel()) {
                break;
            }
            if (x >= 2) {
                hull.getVoxels()[x - 2] = null; // free up memory
            }
        }
        cb.endTask();
    }
}
