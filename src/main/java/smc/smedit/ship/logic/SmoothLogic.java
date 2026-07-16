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
package smc.smedit.ship.logic;

import java.util.Iterator;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.data.BooleanMatrix3D;
import smc.smedit.data.CubeIterator;
import smc.smedit.data.RenderTile;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

/**
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 **/
public class SmoothLogic {

    public static final int EXTERIOR = 1;
    public static final int INTERIOR = 2;
    public static final int EVERYWHERE = EXTERIOR + INTERIOR;

    public static final int WEDGES = 1;
    public static final int CORNERS = 2;
    public static final int EVERYTHING = WEDGES + CORNERS;

    private static final Point3i[] DELTAS = {
        new Point3i(1, 0, 0),
        new Point3i(-1, 0, 0),
        new Point3i(0, 1, 0),
        new Point3i(0, -1, 0),
        new Point3i(0, 0, 1),
        new Point3i(0, 0, -1),};

    public static void smooth(SparseMatrix<Block> grid, int scope, int type, StarMade sm, IPluginCallback cb) {
        //Set<Point3i> exterior = HullLogic.findExterior(grid, cb);
        BooleanMatrix3D exterior = HullLogic.findExteriorMatrix(grid, cb);
        boolean[] edges = new boolean[6];
        cb.setStatus("Smoothing");
        Point3i lower = new Point3i();
        Point3i upper = new Point3i();
        if ((sm.getSelectedLower() != null) && (sm.getSelectedUpper() != null)) {
            lower.set(sm.getSelectedLower());
            upper.set(sm.getSelectedUpper());
        } else {
            grid.getBounds(lower, upper);
            lower.x--;
            lower.y--;
            lower.z--;
            upper.x++;
            upper.y++;
            upper.z++;
        }
        cb.startTask((upper.x - lower.x + 1) * (upper.y - lower.y + 1) * (upper.z - lower.z + 1));
        for (Iterator<Point3i> i = new CubeIterator(lower, upper); i.hasNext();) {
            cb.workTask(1);
            Point3i p = i.next();
            if (grid.contains(p)) {
                continue;
            }
            if (exterior.contains(p)) {
                if ((scope & EXTERIOR) == 0) {
                    continue;
                }
            } else {
                if ((scope & INTERIOR) == 0) {
                    continue;
                }
            }
            int tot = 0;
            for (int j = 0; j < edges.length; j++) {
                edges[j] = isEdge(grid, p, DELTAS[j]);
                if (edges[j]) {
                    tot++;
                }
            }
            if ((tot == 2) && ((type & WEDGES) != 0)) {
                doWedge(grid, p, edges);
            }
            if ((tot == 3) && ((type & CORNERS) != 0)) {
                doCorner(grid, p, edges);
            }
        }
        cb.endTask();
    }

    private static void doCorner(SparseMatrix<Block> grid, Point3i p, boolean[] edges) {
        int ori = -1;
        if (edges[RenderTile.XM]) {
            if (edges[RenderTile.YM]) {
                if (edges[RenderTile.ZM]) {
                    ori = 1;
                } else if (edges[RenderTile.ZP]) {
                    ori = 0;
                }
            } else if (edges[RenderTile.YP]) {
                if (edges[RenderTile.ZM]) {
                    ori = 5;
                } else if (edges[RenderTile.ZP]) {
                    ori = 4;
                }
            }
        } else if (edges[RenderTile.XP]) {
            if (edges[RenderTile.YM]) {
                if (edges[RenderTile.ZM]) {
                    ori = 2;
                } else if (edges[RenderTile.ZP]) {
                    ori = 3;
                }
            } else if (edges[RenderTile.YP]) {
                if (edges[RenderTile.ZM]) {
                    ori = 6;
                } else if (edges[RenderTile.ZP]) {
                    ori = 7;
                }
            }
        }
        if (ori < 0) {
            return;
        }
        Block b = new Block();
        b.setActive(false);
        b.setBlockID(calculateCornerType(grid, p, edges));
        b.setOrientation((short) ori);
        grid.set(p, b);
    }

    private static void doWedge(SparseMatrix<Block> grid, Point3i p, boolean[] edges) {
        int ori = -1;
        if (edges[RenderTile.XM]) {
            if (edges[RenderTile.YM]) {
                ori = 3;
            } else if (edges[RenderTile.YP]) {
                ori = 5;
            } else if (edges[RenderTile.ZM]) {
                ori = 13;
            } else if (edges[RenderTile.ZP]) {
                ori = 8;
            }
        } else if (edges[RenderTile.XP]) {
            if (edges[RenderTile.YM]) {
                ori = 1;
            } else if (edges[RenderTile.YP]) {
                ori = 7;
            } else if (edges[RenderTile.ZM]) {
                ori = 11;
            } else if (edges[RenderTile.ZP]) {
                ori = 10;
            }
        } else if (edges[RenderTile.YM]) {
            if (edges[RenderTile.ZM]) {
                ori = 2;
            } else if (edges[RenderTile.ZP]) {
                ori = 0;
            }
        } else if (edges[RenderTile.YP]) {
            if (edges[RenderTile.ZM]) {
                ori = 6;
            } else if (edges[RenderTile.ZP]) {
                ori = 4;
            }
        }
        if (ori < 0) {
            return;
        }
        Block b = new Block();
        b.setActive(false);
        b.setBlockID(calculateWedgeType(grid, p, edges));
        b.setOrientation((short) ori);
        grid.set(p, b);

    }

    private static short calculateWedgeType(SparseMatrix<Block> grid, Point3i p,
            boolean[] edges) {
        short type1 = -1;
        short type2 = -1;
        for (int i = 0; i < edges.length; i++) {
            if (!edges[i]) {
                continue;
            }
            Point3i p2 = new Point3i();
            p2.add(p, DELTAS[i]);
            Block b = grid.get(p2);
            if (b == null) {
                continue;
            }
            if (type1 == -1) {
                type1 = b.getBlockID();
            } else if (type2 == -1) {
                type2 = b.getBlockID();
                break;
            }
        }
        if (type1 > type2) {
            type1 = type2;
        }
        if (type1 == Blocks.GREY_STANDARD_ARMOR.getId()) {
            return Blocks.GREY_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.PURPLE_STANDARD_ARMOR.getId()) {
            return Blocks.PURPLE_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BROWN_STANDARD_ARMOR.getId()) {
            return Blocks.BROWN_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BLACK_STANDARD_ARMOR.getId()) {
            return Blocks.BLACK_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.RED_STANDARD_ARMOR.getId()) {
            return Blocks.RED_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BLUE_STANDARD_ARMOR.getId()) {
            return Blocks.BLUE_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.GREEN_STANDARD_ARMOR.getId()) {
            return Blocks.GREEN_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.YELLOW_STANDARD_ARMOR.getId()) {
            return Blocks.YELLOW_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.WHITE_STANDARD_ARMOR.getId()) {
            return Blocks.WHITE_STANDARD_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.GLASS.getId()) {
            return Blocks.GLASS_WEDGE.getId();
        }
        if (type1 == Blocks.GREY_ADVANCED_ARMOR.getId()) {
            return Blocks.GREY_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.PURPLE_ADVANCED_ARMOR.getId()) {
            return Blocks.PURPLE_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BROWN_ADVANCED_ARMOR.getId()) {
            return Blocks.BROWN_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BLACK_ADVANCED_ARMOR.getId()) {
            return Blocks.BLACK_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.RED_ADVANCED_ARMOR.getId()) {
            return Blocks.RED_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BLUE_ADVANCED_ARMOR.getId()) {
            return Blocks.BLUE_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.GREEN_ADVANCED_ARMOR.getId()) {
            return Blocks.GREEN_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.YELLOW_ADVANCED_ARMOR.getId()) {
            return Blocks.YELLOW_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.WHITE_ADVANCED_ARMOR.getId()) {
            return Blocks.WHITE_ADVANCED_ARMOR_WEDGE.getId();
        }
        return type1;
    }

    private static short calculateCornerType(SparseMatrix<Block> grid, Point3i p,
            boolean[] edges) {
        short type1 = -1;
        short type2 = -1;
        short type3 = -1;
        for (int i = 0; i < edges.length; i++) {
            if (!edges[i]) {
                continue;
            }
            Point3i p2 = new Point3i();
            p2.add(p, DELTAS[i]);
            Block b = grid.get(p2);
            if (b == null) {
                continue;
            }
            if (type1 == -1) {
                type1 = b.getBlockID();
            } else if (type2 == -1) {
                type2 = b.getBlockID();
            } else if (type3 == -1) {
                type3 = b.getBlockID();
                break;
            }
        }
        if (type1 != type2) {
            if (type2 == type3) {
                type1 = type2;
            } else if (type1 != type3) {
                type1 = (short) Math.min(type1, Math.min(type2, type3));
            }
        }
        if (type1 == Blocks.GREY_STANDARD_ARMOR.getId()) {
            return Blocks.GREY_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.PURPLE_STANDARD_ARMOR.getId()) {
            return Blocks.PURPLE_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.BROWN_STANDARD_ARMOR.getId()) {
            return Blocks.BROWN_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.BLACK_STANDARD_ARMOR.getId()) {
            return Blocks.BLACK_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.RED_STANDARD_ARMOR.getId()) {
            return Blocks.RED_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.BLUE_STANDARD_ARMOR.getId()) {
            return Blocks.BLUE_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.GREEN_STANDARD_ARMOR.getId()) {
            return Blocks.GREEN_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.YELLOW_STANDARD_ARMOR.getId()) {
            return Blocks.YELLOW_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.WHITE_STANDARD_ARMOR.getId()) {
            return Blocks.WHITE_STANDARD_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.GLASS.getId()) {
            return Blocks.GLASS_CORNER.getId();
        }
        if (type1 == Blocks.GREY_ADVANCED_ARMOR.getId()) {
            return Blocks.GREY_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.PURPLE_ADVANCED_ARMOR.getId()) {
            return Blocks.PURPLE_ADVANCED_ARMOR_WEDGE.getId();
        }
        if (type1 == Blocks.BROWN_ADVANCED_ARMOR.getId()) {
            return Blocks.BROWN_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.BLACK_ADVANCED_ARMOR.getId()) {
            return Blocks.BLACK_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.RED_ADVANCED_ARMOR.getId()) {
            return Blocks.RED_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.BLUE_ADVANCED_ARMOR.getId()) {
            return Blocks.BLUE_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.GREEN_ADVANCED_ARMOR.getId()) {
            return Blocks.GREEN_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.YELLOW_ADVANCED_ARMOR.getId()) {
            return Blocks.YELLOW_ADVANCED_ARMOR_CORNER.getId();
        }
        if (type1 == Blocks.WHITE_ADVANCED_ARMOR.getId()) {
            return Blocks.WHITE_ADVANCED_ARMOR_CORNER.getId();
        }
        return type1;
    }

    private static boolean isEdge(SparseMatrix<Block> grid, Point3i p, Point3i d) {
        Point3i p2 = new Point3i();
        p2.add(p, d);
        if (!grid.contains(p2)) {
            return false;
        }
        short type = grid.get(p2).getBlockID();
        if (BlockGroups.isWedge(type) || BlockGroups.isPowerWedge(type)) {
            return false;
        }
        return BlockGroups.isHull(type) || BlockGroups.isPowerHull(type) || (type == Blocks.GLASS.getId());
    }
}

