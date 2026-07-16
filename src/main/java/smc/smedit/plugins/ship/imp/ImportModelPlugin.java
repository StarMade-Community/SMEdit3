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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.plugins.ship.move.MovePlugin;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.logic.ShipLogic;
import smc.smedit.util.jgl.logic.imp.DAELogic;
import smc.smedit.util.jgl.obj.JGLGroup;
import smc.smedit.util.jgl.obj.JGLNode;
import smc.smedit.util.jgl.obj.tri.JGLObj;
import smc.smedit.util.voxel.ColorPalette;
import smc.smedit.util.voxel.MeshVoxelizer;
import smc.smedit.util.voxel.VoxelBlockMapper;
import smc.smedit.util.voxel.VoxelGrid;
import smc.smedit.vecmath.Color3f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.ext.Hull3f;
import smc.smedit.vecmath.ext.Triangle3f;

/**
 * Imports a 3D model of any supported format and voxelizes it into StarMade
 * blocks — no external tools required. Replaces the separate OBJ, VRML, and
 * Binvox importers: the format is auto-detected from the file extension, mesh
 * formats are voxelized in-house by {@link MeshVoxelizer} (binvox-quality,
 * hole-free), and source colors are mapped onto the user's chosen
 * {@link ConversionPalette}.
 *
 * @author SMEdit3
 **/
public class ImportModelPlugin implements IBlocksPlugin {

    public static final String NAME = "Import/3D Model";
    public static final String DESC = "Import & voxelize a 3D model (OBJ, VRML, DAE, Binvox)";
    public static final String AUTH = "SMEdit3";
    public static final int[][] CLASSIFICATIONS = {
        {TYPE_SHIP, SUBTYPE_FILE, 25},};
    private static final Logger log = Logger.getLogger(ImportModelPlugin.class.getName());

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
        return new ImportModelParameters();
    }

    @Override
    public void initParameterBean(SparseMatrix<Block> original, Object params, StarMade sm, IPluginCallback cb) {
    }

    @Override
    public int[][] getClassifications() {
        return CLASSIFICATIONS;
    }

    @Override
    public SparseMatrix<Block> modify(SparseMatrix<Block> original, Object p, StarMade sm, IPluginCallback cb) {
        ImportModelParameters params = (ImportModelParameters) p;
        short baseColor = sm.getSelectedBlockType();
        if (baseColor <= 0) {
            baseColor = Blocks.GREY_STANDARD_ARMOR.getId();
        }
        ColorPalette palette = ColorPalette.fromBlockIds(
                ConversionPalette.fromText(params.getPalette()).getBlockIds());
        try {
            File file = new File(params.getFile());
            String name = file.getName().toLowerCase(Locale.ROOT);
            VoxelGrid grid;
            if (name.endsWith(".binvox")) {
                grid = readBinvox(file, baseColor);
            } else {
                Hull3f hull = readMesh(file, name);
                if (hull == null || hull.getTriangles().isEmpty()) {
                    throw new IllegalArgumentException("Model " + file.getName() + " has no triangles.");
                }
                grid = MeshVoxelizer.voxelize(hull, params.getLongestDimension(), params.isSolid(),
                        palette, baseColor, cb);
            }
            SparseMatrix<Block> modified = VoxelBlockMapper.toBlocks(grid, true, cb);
            if (modified.size() == 0) {
                throw new IllegalArgumentException("Voxelization produced no blocks; try a larger longest dimension.");
            }
            return centerAndCore(modified, cb);
        } catch (IOException | IllegalArgumentException e) {
            cb.setError(e);
            return null;
        }
    }

    /** Parses a mesh file (by extension) into a triangle hull. */
    private Hull3f readMesh(File file, String lowerName) throws IOException {
        if (lowerName.endsWith(".obj")) {
            return OBJLogic.readFile(file.getPath());
        }
        if (lowerName.endsWith(".wrl") || lowerName.endsWith(".vrml")) {
            return VRMLLogic.readHull(file);
        }
        if (lowerName.endsWith(".dae")) {
            try (FileInputStream fis = new FileInputStream(file)) {
                return daeToHull(DAELogic.readDAE(fis));
            }
        }
        throw new IllegalArgumentException("Unsupported model format: " + file.getName()
                + " (expected .obj, .wrl, .vrml, .dae or .binvox)");
    }

    /** Reads a pre-voxelized binvox file straight into a grid (no color data). */
    private VoxelGrid readBinvox(File file, short baseColor) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            BinvoxData data = BinvoxLogic.read(fis);
            if (data == null || data.getVoxels() == null) {
                throw new IllegalArgumentException("Could not read binvox file " + file.getName());
            }
            int sx = data.getXSpan();
            int sy = data.getYSpan();
            int sz = data.getZSpan();
            VoxelGrid grid = new VoxelGrid(sx, sy, sz);
            boolean[][][] voxels = data.getVoxels();
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    for (int x = 0; x < sx; x++) {
                        if (voxels[z][y][x]) {
                            grid.set(x, y, z, baseColor);
                        }
                    }
                }
            }
            return grid;
        }
    }

    /** Centers the ship on the build box and drops in a core, as the old importers did. */
    private SparseMatrix<Block> centerAndCore(SparseMatrix<Block> modified, IPluginCallback cb) {
        cb.setStatus("Centering hull");
        Point3i lower = new Point3i();
        Point3i upper = new Point3i();
        modified.getBounds(lower, upper);
        int dx = (upper.x + lower.x) / 2 - 8;
        int dy = (upper.y + lower.y) / 2 - 8;
        int dz = (upper.z + lower.z) / 2 - 8;
        modified = MovePlugin.shift(modified, dx, dy, dz, cb);
        ShipLogic.ensureCore(modified);
        return modified;
    }

    // ---- COLLADA (.dae) mesh -> Hull3f -------------------------------------

    private static Hull3f daeToHull(JGLNode node) {
        Hull3f hull = new Hull3f();
        collectTriangles(node, hull);
        return hull;
    }

    private static void collectTriangles(JGLNode node, Hull3f hull) {
        if (node instanceof JGLGroup) {
            for (JGLNode child : ((JGLGroup) node).getChildren()) {
                collectTriangles(child, hull);
            }
        } else if (node instanceof JGLObj) {
            addObjTriangles((JGLObj) node, hull);
        }
    }

    private static void addObjTriangles(JGLObj obj, Hull3f hull) {
        FloatBuffer vb = obj.getVertexBuffer();
        if (vb == null || obj.getMode() != JGLObj.TRIANGLES) {
            return;
        }
        FloatBuffer cb = obj.getColorBuffer();
        int vertLimit = vb.limit();
        int faceCount = obj.getIndices();
        ShortBuffer sib = obj.getIndexShortBuffer();
        IntBuffer iib = obj.getIndexIntBuffer();
        for (int f = 0; f < faceCount; f++) {
            int i0, i1, i2;
            if (sib != null) {
                i0 = Short.toUnsignedInt(sib.get(f * 3));
                i1 = Short.toUnsignedInt(sib.get(f * 3 + 1));
                i2 = Short.toUnsignedInt(sib.get(f * 3 + 2));
            } else if (iib != null) {
                i0 = iib.get(f * 3);
                i1 = iib.get(f * 3 + 1);
                i2 = iib.get(f * 3 + 2);
            } else {
                return;
            }
            if (i0 * 3 + 2 >= vertLimit || i1 * 3 + 2 >= vertLimit || i2 * 3 + 2 >= vertLimit) {
                continue;
            }
            Triangle3f tri = new Triangle3f(vertex(vb, i0), vertex(vb, i1), vertex(vb, i2));
            Color3f color = averageColor(cb, i0, i1, i2);
            if (color != null) {
                tri.setColor(color);
            }
            hull.getTriangles().add(tri);
        }
    }

    private static Point3f vertex(FloatBuffer vb, int i) {
        return new Point3f(vb.get(i * 3), vb.get(i * 3 + 1), vb.get(i * 3 + 2));
    }

    private static Color3f averageColor(FloatBuffer cb, int i0, int i1, int i2) {
        if (cb == null) {
            return null;
        }
        int limit = cb.limit();
        if (i0 * 4 + 2 >= limit || i1 * 4 + 2 >= limit || i2 * 4 + 2 >= limit) {
            return null;
        }
        float r = (cb.get(i0 * 4) + cb.get(i1 * 4) + cb.get(i2 * 4)) / 3f;
        float g = (cb.get(i0 * 4 + 1) + cb.get(i1 * 4 + 1) + cb.get(i2 * 4 + 1)) / 3f;
        float b = (cb.get(i0 * 4 + 2) + cb.get(i1 * 4 + 2) + cb.get(i2 * 4 + 2)) / 3f;
        return new Color3f(r, g, b);
    }
}
