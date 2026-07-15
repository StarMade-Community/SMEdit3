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
package smc.smedit.ui.lwjgl;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import smc.smedit.data.RenderPoly;
import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.util.jgl.obj.JGLGroup;
import smc.smedit.util.jgl.obj.tri.JGLObj;
import smc.smedit.util.lwjgl.win.JGLTextureCache;
import smc.smedit.vecmath.Color3f;
import smc.smedit.vecmath.Point2f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.logic.MathUtils;

public class LWJGLRenderLogic {

    private static int mTextureID = -1;

    public static void addBlocks(JGLGroup group, SparseMatrix<Block> grid, boolean plain) {
        if (mTextureID < 0) {
            BlockTypeColors.loadBlockIcons();
            JGLTextureCache.register(1, BlockTypeColors.mAllTextures);
            mTextureID = 1;
        }
        // Opaque and transparent (glass/light) geometry are meshed separately so
        // the transparent pass can be drawn last, blended, with depth-writes off —
        // otherwise glass punches a hole in the depth buffer and you see straight
        // through into the ship.
        MeshInfo opaque = newMeshInfo(plain);
        MeshInfo transparent = newMeshInfo(plain);
        // LOD-model blocks (consoles/lights/pipes/…) are drawn as their real mesh
        // instead of a cube. Each distinct model texture becomes its own mesh/obj
        // (StarMade model textures are per-model PNGs, not the block atlas), split
        // opaque vs transparent so lights blend in the transparent pass.
        Map<Integer, MeshInfo> modelOpaque = new HashMap<>();
        Map<Integer, MeshInfo> modelTransparent = new HashMap<>();
        for (Iterator<Point3i> i = grid.iteratorNonNull(); i.hasNext();) {
            Point3i p = i.next();
            Block b = grid.get(p);
            if (b == null) {
                continue;
            }
            short id = b.getBlockID();
            boolean trans = BlockTypeColors.isTransparent(id);
            // Textured render only: in plain (colour) mode fall back to the cube.
            if (!plain && BlockTypeColors.hasLodModel(id)) {
                LodModelCache.LodModel model = LodModelCache.getModel(BlockTypeColors.getLodShape(id));
                if (model != null) {
                    Map<Integer, MeshInfo> target = trans ? modelTransparent : modelOpaque;
                    MeshInfo mi = target.computeIfAbsent(model.textureId, k -> newModelMeshInfo());
                    addLodModel(mi, p, b, model);
                    continue;
                }
                // model unavailable -> fall through and draw the cube (no regression)
            }
            addBlock(trans ? transparent : opaque, grid, p);
        }
        group.add(infoToObj(opaque));
        // Opaque model meshes draw with the opaque pass (after the atlas mesh).
        for (Map.Entry<Integer, MeshInfo> e : modelOpaque.entrySet()) {
            group.add(modelInfoToObj(e.getValue(), e.getKey(), false));
        }
        if (!transparent.verts.isEmpty()) {
            JGLObj transObj = infoToObj(transparent);
            transObj.setTransparent(true);
            group.add(transObj); // added after the opaque mesh so it draws on top
        }
        // Transparent model meshes (lights) blend last, over everything.
        for (Map.Entry<Integer, MeshInfo> e : modelTransparent.entrySet()) {
            group.add(modelInfoToObj(e.getValue(), e.getKey(), true));
        }
    }

    /** A texture-mapped triangle mesh accumulator for LOD models (no per-vertex colour). */
    private static MeshInfo newModelMeshInfo() {
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        info.uv = new ArrayList<>();
        return info;
    }

    /** Builds a textured triangle {@link JGLObj} from a model mesh accumulator. */
    private static JGLObj modelInfoToObj(MeshInfo info, int textureId, boolean transparent) {
        JGLObj obj = new JGLObj();
        obj.setMode(JGLObj.TRIANGLES);
        obj.setVertices(info.verts);
        obj.setIndices(info.indexes);
        obj.setTextures(info.uv);
        obj.setTextureID(textureId);
        if (transparent) {
            obj.setTransparent(true);
        }
        return obj;
    }

    /**
     * Appends one LOD-model instance at block {@code p} into {@code info}
     * (triangle mesh). Vertices are the model's block-local positions offset by
     * the block cell; UVs come straight from the mesh (its own texture, not the
     * atlas). Indices are offset by the running vertex count so many blocks batch
     * into one obj.
     */
    public static void addLodModel(MeshInfo info, Point3i p, Block b, LodModelCache.LodModel model) {
        int baseVert = info.verts.size();
        float[] pos = model.positions;
        float[] uv = model.uvs;
        int orient = b.getOrientation();
        float[] r = new float[3];
        int n = pos.length / 3;
        for (int v = 0; v < n; v++) {
            // StarMade rotates the mesh by the block's orientation before placing
            // it (Oriencube transform); do the same, then offset to the cell.
            LodOrientation.rotate(orient, pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2], r);
            info.verts.add(new Point3f(p.x + r[0], p.y + r[1], p.z + r[2]));
            info.uv.add(new Point2f(uv[v * 2], uv[v * 2 + 1]));
        }
        for (int t = 0; t < model.tris.length; t++) {
            info.indexes.add(baseVert + model.tris[t]);
        }
    }

    private static MeshInfo newMeshInfo(boolean plain) {
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        if (plain) {
            info.colors = new ArrayList<>();
        } else {
            info.uv = new ArrayList<>();
        }
        return info;
    }

    /**
     *
     * @param info
     * @return
     */
    public static JGLObj infoToObj(MeshInfo info) {
        JGLObj obj = new JGLObj();
        obj.setMode(JGLObj.QUADS);
        obj.setVertices(info.verts);
        obj.setIndices(info.indexes);
        if (info.colors != null) {
            obj.setColors(info.colors);
        } else {
            obj.setTextures(info.uv);
            obj.setTextureID(mTextureID);
        }
        return obj;
    }

    /**
     *
     * @param group
     * @param grid
     * @param p
     */
    public static void addBlock(MeshInfo group, SparseMatrix<Block> grid, Point3i p) {
        Block b = grid.get(p);
        if (b == null) {
            return;
        }
        // Slab blocks (partial-height) take priority over the shape style.
        int slab = BlockTypeColors.getBlockSlab(b.getBlockID());
        if (slab > 0) {
            addSlab(group, p, b, slab);
            return;
        }
        // Shaped blocks render as their real geometry; unhandled styles/orientations
        // fall back to a full cube (solid, if the wrong shape).
        int style = BlockTypeColors.getBlockStyle(b.getBlockID());
        if (style == BlockTypeColors.STYLE_WEDGE) {
            int[] cut = wedgeCutFaces(b.getOrientation());
            if (cut != null) {
                addWedge(group, grid, p, b, cut);
                return;
            }
        } else if (style == BlockTypeColors.STYLE_CORNER) {
            addCorner(group, p, b);
            return;
        } else if (style == BlockTypeColors.STYLE_SPRITE) {
            addSprite(group, p, b);
            return;
        } else if (style == BlockTypeColors.STYLE_TETRA) {
            addTetra(group, p, b);
            return;
        } else if (style == BlockTypeColors.STYLE_HEPTA) {
            addHepta(group, p, b);
            return;
        }
        Point3f lower = new Point3f(p.x - .5f, p.y - .5f, p.z - .5f);
        Point3f upper = new Point3f(p.x + .5f, p.y + .5f, p.z + .5f);
        short[] colors = new short[]{b.getBlockID()};
        List<JGLObj> objs = new ArrayList<>();
        int orient = b.getOrientation();
        if (!hidesFace(grid, b, p.x + 1, p.y, p.z)) {
            addSelectFace(group, upper.x, lower.y, lower.z, upper.x, upper.y, upper.z,
                    RenderPoly.XP, colors[0 % colors.length], orient);
        }
        if (!hidesFace(grid, b, p.x - 1, p.y, p.z)) {
            addSelectFace(group, lower.x, lower.y, lower.z, lower.x, upper.y, upper.z,
                    RenderPoly.XM, colors[1 % colors.length], orient);
        }
        if (!hidesFace(grid, b, p.x, p.y + 1, p.z)) {
            addSelectFace(group, lower.x, upper.y, lower.z, upper.x, upper.y, upper.z,
                    RenderPoly.YP, colors[2 % colors.length], orient);
        }
        if (!hidesFace(grid, b, p.x, p.y - 1, p.z)) {
            addSelectFace(group, lower.x, lower.y, lower.z, upper.x, lower.y, upper.z,
                    RenderPoly.YM, colors[3 % colors.length], orient);
        }
        if (!hidesFace(grid, b, p.x, p.y, p.z + 1)) {
            addSelectFace(group, lower.x, lower.y, upper.z, upper.x, upper.y, upper.z,
                    RenderPoly.ZP, colors[4 % colors.length], orient);
        }
        if (!hidesFace(grid, b, p.x, p.y, p.z - 1)) {
            addSelectFace(group, lower.x, lower.y, lower.z, upper.x, upper.y, lower.z,
                    RenderPoly.ZM, colors[5 % colors.length], orient);
        }
        for (JGLObj obj : objs) {
            obj.setData("point", p);
            obj.setData("block", b);
        }
    }

    // ---- Wedge (sloped block) geometry ----
    //
    // A wedge is a triangular prism: two adjacent cube faces are cut away and
    // replaced by a slope. The cut-face pair is chosen by the block's orientation
    // value (0-13), matching the legacy software renderer (RenderLogic.doWedge).
    // Element faces: FRONT=0 BACK=1 TOP=2 BOTTOM=3 RIGHT=4 LEFT=5.
    private static final int[][] WEDGE_CUT_BY_ORIENT = new int[14][];

    static {
        WEDGE_CUT_BY_ORIENT[0] = new int[] {2, 1};  // TOP, BACK
        WEDGE_CUT_BY_ORIENT[1] = new int[] {5, 2};  // LEFT, TOP
        WEDGE_CUT_BY_ORIENT[2] = new int[] {2, 0};  // TOP, FRONT
        WEDGE_CUT_BY_ORIENT[3] = new int[] {4, 2};  // RIGHT, TOP
        WEDGE_CUT_BY_ORIENT[4] = new int[] {3, 1};  // BOTTOM, BACK
        WEDGE_CUT_BY_ORIENT[5] = new int[] {4, 3};  // RIGHT, BOTTOM
        WEDGE_CUT_BY_ORIENT[6] = new int[] {3, 0};  // BOTTOM, FRONT
        WEDGE_CUT_BY_ORIENT[7] = new int[] {5, 3};  // LEFT, BOTTOM
        WEDGE_CUT_BY_ORIENT[8] = new int[] {4, 1};  // RIGHT, BACK
        WEDGE_CUT_BY_ORIENT[9] = null;              // unknown -> draw a cube
        WEDGE_CUT_BY_ORIENT[10] = new int[] {5, 1}; // LEFT, BACK
        WEDGE_CUT_BY_ORIENT[11] = new int[] {5, 0}; // LEFT, FRONT
        WEDGE_CUT_BY_ORIENT[12] = new int[] {4, 1}; // (== 8)
        WEDGE_CUT_BY_ORIENT[13] = new int[] {4, 0}; // RIGHT, FRONT
    }

    /** @return the two cut faces for a wedge orientation, or {@code null} if unknown. */
    private static int[] wedgeCutFaces(int orientation) {
        if (orientation >= 0 && orientation < WEDGE_CUT_BY_ORIENT.length) {
            return WEDGE_CUT_BY_ORIENT[orientation];
        }
        return null;
    }

    private static float[] faceDir(int face) {
        switch (face) {
            case 0: return new float[] {0, 0, 1};   // FRONT +Z
            case 1: return new float[] {0, 0, -1};  // BACK  -Z
            case 2: return new float[] {0, 1, 0};   // TOP   +Y
            case 3: return new float[] {0, -1, 0};  // BOTTOM -Y
            case 4: return new float[] {1, 0, 0};   // RIGHT +X
            default: return new float[] {-1, 0, 0}; // LEFT  -X
        }
    }

    /**
     * Adds a wedge prism. {@code cut} is the pair of faces the slope replaces (from
     * {@link #wedgeCutFaces}). The two full faces opposite the cut faces are culled
     * against neighbours like a cube; the slope and the two triangular sides
     * complete the prism. Faces are emitted double-sided so they are visible
     * regardless of winding (the viewport has backface culling on).
     */
    public static void addWedge(MeshInfo info, SparseMatrix<Block> grid, Point3i p, Block b, int[] cut) {
        short id = b.getBlockID();
        float[] a = faceDir(cut[0]);
        float[] bDir = faceDir(cut[1]);
        float[] c = cross(a, bDir); // prism axis (perpendicular to both cut faces)

        // Triangle corners: C1 = +a-b, C2 = -a-b (right angle), C3 = -a+b.
        Point3f c1 = corner(p, a, bDir, 0.5f, -0.5f);
        Point3f c2 = corner(p, a, bDir, -0.5f, -0.5f);
        Point3f c3 = corner(p, a, bDir, -0.5f, 0.5f);
        Point3f v1a = shift(c1, c, 0.5f), v1b = shift(c1, c, -0.5f);
        Point3f v2a = shift(c2, c, 0.5f), v2b = shift(c2, c, -0.5f);
        Point3f v3a = shift(c3, c, 0.5f), v3b = shift(c3, c, -0.5f);

        // Full face opposite the first cut face (normal -a).
        if (!hidesFaceDir(grid, p, b, a, -1)) {
            addWedgeFace(info, v2a, v2b, v3b, v3a, id);
        }
        // Full face opposite the second cut face (normal -b).
        if (!hidesFaceDir(grid, p, b, bDir, -1)) {
            addWedgeFace(info, v1a, v1b, v2b, v2a, id);
        }
        // The slope (hypotenuse) — always exposed.
        addWedgeFace(info, v1a, v1b, v3b, v3a, id);
        // The two triangular sides (a degenerate quad = triangle), culled against
        // the neighbours along the prism axis.
        if (!hidesFaceDir(grid, p, b, c, 1)) {
            addWedgeFace(info, v1a, v2a, v3a, v3a, id);
        }
        if (!hidesFaceDir(grid, p, b, c, -1)) {
            addWedgeFace(info, v1b, v2b, v3b, v3b, id);
        }
    }

    /** Adds one shaped-block face. Backface culling is disabled, so it's visible from both sides. */
    private static void addWedgeFace(MeshInfo info, Point3f a, Point3f b, Point3f c, Point3f d, short id) {
        addSelectQuad(info, a, b, c, d, id);
    }

    // The 5 vertices per corner ("Spike") orientation 0-23 — the 4 base-square
    // corners (in order) + the apex — ported verbatim from StarMade's Spike* classes.
    private static final float[][][] CORNER_VERTS = {
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {-.5f, .5f, .5f}},  // 0
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}}, // 1
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}},  // 2
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, .5f}},   // 3
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {-.5f, -.5f, .5f}},     // 4
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}},    // 5
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {.5f, -.5f, -.5f}},     // 6
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {.5f, -.5f, .5f}},      // 7
        {{-.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {-.5f, .5f, .5f}},  // 8
        {{-.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}},   // 9
        {{-.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, -.5f, .5f}},  // 10
        {{-.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {-.5f, -.5f, .5f}}, // 11
        {{-.5f, .5f, .5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, .5f, .5f}, {-.5f, -.5f, -.5f}},    // 12
        {{-.5f, .5f, .5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, .5f, .5f}, {-.5f, .5f, -.5f}},     // 13
        {{-.5f, .5f, .5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}},      // 14
        {{-.5f, .5f, .5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, .5f, .5f}, {.5f, -.5f, -.5f}},     // 15
        {{-.5f, -.5f, .5f}, {-.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, -.5f, -.5f}}, // 16
        {{-.5f, -.5f, .5f}, {-.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, -.5f}},  // 17
        {{-.5f, -.5f, .5f}, {-.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}},   // 18
        {{-.5f, -.5f, .5f}, {-.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, -.5f, .5f}},  // 19
        {{.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {-.5f, -.5f, -.5f}},    // 20
        {{.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {-.5f, .5f, -.5f}},     // 21
        {{.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {-.5f, .5f, .5f}},      // 22
        {{.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {-.5f, -.5f, .5f}},     // 23
    };

    /** Adds a corner ("Spike"): a square-base pyramid; 24 orientations from StarMade's exact vertices. */
    public static void addCorner(MeshInfo info, Point3i p, Block b) {
        short id = b.getBlockID();
        float[][] v = CORNER_VERTS[((b.getOrientation() % 24) + 24) % 24];
        Point3f q0 = pt(p, v[0][0], v[0][1], v[0][2]);
        Point3f q1 = pt(p, v[1][0], v[1][1], v[1][2]);
        Point3f q2 = pt(p, v[2][0], v[2][1], v[2][2]);
        Point3f q3 = pt(p, v[3][0], v[3][1], v[3][2]);
        Point3f apex = pt(p, v[4][0], v[4][1], v[4][2]);
        addWedgeFace(info, q0, q1, q2, q3, id); // base square
        addWedgeFace(info, q0, q1, apex, apex, id);
        addWedgeFace(info, q1, q2, apex, apex, id);
        addWedgeFace(info, q2, q3, apex, apex, id);
        addWedgeFace(info, q3, q0, apex, apex, id);
    }

    private static int switchLeftRight(int dir) {
        if (dir == 4) {
            return 5;
        }
        if (dir == 5) {
            return 4;
        }
        return dir;
    }

    /**
     * Adds a slab: a cube shortened along one axis to {@code 0.5 - slab*0.25}
     * (slab 1/2/3 = 3/4, 1/2, 1/4 thick). Orientation % 6 picks the face it's cut
     * toward (matching StarMade Element.getSlab handling).
     */
    public static void addSlab(MeshInfo info, Point3i p, Block b, int slab) {
        short id = b.getBlockID();
        float slabP = 0.5f - slab * 0.25f;
        float minX = -0.5f, minY = -0.5f, minZ = -0.5f;
        float maxX = 0.5f, maxY = 0.5f, maxZ = 0.5f;
        switch (switchLeftRight(((int) b.getOrientation()) % 6)) {
            case 0: maxZ = slabP; break;   // FRONT: cut +Z
            case 1: minZ = -slabP; break;  // BACK:  cut -Z
            case 2: maxY = slabP; break;   // TOP:   cut +Y
            case 3: minY = -slabP; break;  // BOTTOM: cut -Y
            case 4: maxX = slabP; break;   // RIGHT: cut +X
            default: minX = -slabP; break; // LEFT:  cut -X
        }
        float x0 = p.x + minX, x1 = p.x + maxX;
        float y0 = p.y + minY, y1 = p.y + maxY;
        float z0 = p.z + minZ, z1 = p.z + maxZ;
        int orient = b.getOrientation();
        addSelectFace(info, x1, y0, z0, x1, y1, z1, RenderPoly.XP, id, orient);
        addSelectFace(info, x0, y0, z0, x0, y1, z1, RenderPoly.XM, id, orient);
        addSelectFace(info, x0, y1, z0, x1, y1, z1, RenderPoly.YP, id, orient);
        addSelectFace(info, x0, y0, z0, x1, y0, z1, RenderPoly.YM, id, orient);
        addSelectFace(info, x0, y0, z1, x1, y1, z1, RenderPoly.ZP, id, orient);
        addSelectFace(info, x0, y0, z0, x1, y1, z0, RenderPoly.ZM, id, orient);
    }

    /** Adds a sprite: two crossed vertical quads (an X), like foliage. Rendered double-sided. */
    public static void addSprite(MeshInfo info, Point3i p, Block b) {
        short id = b.getBlockID();
        addWedgeFace(info, pt(p, -.5f, -.5f, -.5f), pt(p, .5f, -.5f, .5f),
                pt(p, .5f, .5f, .5f), pt(p, -.5f, .5f, -.5f), id);
        addWedgeFace(info, pt(p, .5f, -.5f, -.5f), pt(p, -.5f, -.5f, .5f),
                pt(p, -.5f, .5f, .5f), pt(p, .5f, .5f, -.5f), id);
    }

    private static Point3f pt(Point3i p, float dx, float dy, float dz) {
        return new Point3f(p.x + dx, p.y + dy, p.z + dz);
    }

    // The 4 tetrahedron vertices per orientation 0-7, ported verbatim from
    // StarMade's Tetrahedron* shape classes (registration order = stored value).
    private static final float[][][] TETRA_VERTS = {
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {-.5f, .5f, .5f}},   // 0
        {{-.5f, -.5f, -.5f}, {-.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {-.5f, .5f, -.5f}}, // 1
        {{-.5f, -.5f, -.5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, -.5f}},   // 2
        {{-.5f, -.5f, .5f}, {.5f, -.5f, .5f}, {.5f, -.5f, -.5f}, {.5f, .5f, .5f}},     // 3
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, .5f}, {-.5f, -.5f, .5f}},     // 4
        {{-.5f, .5f, -.5f}, {-.5f, .5f, .5f}, {.5f, .5f, -.5f}, {-.5f, -.5f, -.5f}},   // 5
        {{-.5f, .5f, -.5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {.5f, -.5f, -.5f}},     // 6
        {{-.5f, .5f, .5f}, {.5f, .5f, .5f}, {.5f, .5f, -.5f}, {.5f, -.5f, .5f}},       // 7
    };

    // The cube corner sliced off per hepta ("penta") orientation 0-7 (the negated
    // tetra apex, matching StarMade's Penta* classes).
    private static final float[][] HEPTA_CUT = {
        {.5f, .5f, -.5f}, {.5f, .5f, .5f}, {-.5f, .5f, .5f}, {-.5f, .5f, -.5f},
        {.5f, -.5f, -.5f}, {.5f, -.5f, .5f}, {-.5f, -.5f, .5f}, {-.5f, -.5f, -.5f},
    };

    /** Adds a tetra (tetrahedron): 4 triangular faces from StarMade's exact vertices. */
    public static void addTetra(MeshInfo info, Point3i p, Block b) {
        short id = b.getBlockID();
        float[][] v = TETRA_VERTS[((b.getOrientation() % 8) + 8) % 8];
        Point3f v0 = pt(p, v[0][0], v[0][1], v[0][2]);
        Point3f v1 = pt(p, v[1][0], v[1][1], v[1][2]);
        Point3f v2 = pt(p, v[2][0], v[2][1], v[2][2]);
        Point3f v3 = pt(p, v[3][0], v[3][1], v[3][2]);
        addWedgeFace(info, v0, v1, v2, v2, id);
        addWedgeFace(info, v0, v1, v3, v3, id);
        addWedgeFace(info, v0, v2, v3, v3, id);
        addWedgeFace(info, v1, v2, v3, v3, id);
    }

    /**
     * Adds a hepta: a cube with the corner tetra sliced off (7 faces = 3 full
     * squares + 3 triangles + the diagonal cut), the cut corner from {@link #HEPTA_CUT}.
     */
    public static void addHepta(MeshInfo info, Point3i p, Block b) {
        short id = b.getBlockID();
        float[] c = HEPTA_CUT[((b.getOrientation() % 8) + 8) % 8];
        float cx = c[0], cy = c[1], cz = c[2];
        Point3f ax = pt(p, -cx, cy, cz);
        Point3f ay = pt(p, cx, -cy, cz);
        Point3f az = pt(p, cx, cy, -cz);
        Point3f f = pt(p, -cx, -cy, -cz);
        Point3f exy = pt(p, -cx, -cy, cz);
        Point3f exz = pt(p, -cx, cy, -cz);
        Point3f eyz = pt(p, cx, -cy, -cz);
        // Three full squares (the faces not touching the cut corner).
        addWedgeFace(info, ax, exy, f, exz, id);
        addWedgeFace(info, ay, exy, f, eyz, id);
        addWedgeFace(info, az, exz, f, eyz, id);
        // Three triangles (the cut-corner faces, minus the corner).
        addWedgeFace(info, ay, az, eyz, eyz, id);
        addWedgeFace(info, ax, az, exz, exz, id);
        addWedgeFace(info, ax, ay, exy, exy, id);
        // The diagonal cut face.
        addWedgeFace(info, ax, ay, az, az, id);
    }

    private static float[] cross(float[] u, float[] v) {
        return new float[] {
            u[1] * v[2] - u[2] * v[1],
            u[2] * v[0] - u[0] * v[2],
            u[0] * v[1] - u[1] * v[0],
        };
    }

    /** Block-centre + sa*a + sb*b (half-unit corner offsets). */
    private static Point3f corner(Point3i p, float[] a, float[] b, float sa, float sb) {
        return new Point3f(p.x + a[0] * sa + b[0] * sb,
                p.y + a[1] * sa + b[1] * sb,
                p.z + a[2] * sa + b[2] * sb);
    }

    private static Point3f shift(Point3f base, float[] d, float s) {
        return new Point3f(base.x + d[0] * s, base.y + d[1] * s, base.z + d[2] * s);
    }

    /**
     * Whether {@code id} fully fills its cell, so it can cover a neighbour's face.
     * Only a full-size cube (opaque or transparent) does. Slabs, shaped blocks
     * (wedge/corner/tetra/hepta/sprite) and LOD-model blocks all leave real gaps
     * in their cell, so a neighbour's face against them must stay drawn — even when
     * the cell "behind" them is occupied, because that neighbour may itself be
     * thin. Treating them as fillers culled faces that were actually visible
     * through the gap, leaving see-through holes into the ship.
     */
    private static boolean fillsCell(short id) {
        if (BlockTypeColors.hasLodModel(id)) {
            return false;
        }
        if (BlockTypeColors.getBlockSlab(id) > 0) {
            return false;
        }
        int style = BlockTypeColors.getBlockStyle(id);
        return style == BlockTypeColors.STYLE_NORMAL || style == BlockTypeColors.STYLE_NORMAL24;
    }

    /**
     * Whether {@code self}'s face toward the block at (x,y,z) is hidden and can be
     * skipped. Rules:
     * <ul>
     * <li>Only a cell-filling neighbour can hide the face; slabs, shaped blocks and
     * LOD models never do (their gaps reveal the face behind).</li>
     * <li>A cell-filling opaque cube always hides the face.</li>
     * <li>Two adjacent transparent blocks of the <em>same</em> id share an
     * internal face neither needs to draw (no overdraw inside a glass volume).</li>
     * <li>A buried transparent cube (glass fill deep in a solid mass) hides the
     * face behind it — the interior was already drawn opaque — while a window still
     * shows the hull directly behind the glass.</li>
     * </ul>
     */
    private static boolean hidesFace(SparseMatrix<Block> grid, Block self, int x, int y, int z) {
        Block n = grid.get(x, y, z);
        if (n == null) {
            return false;
        }
        short nid = n.getBlockID();
        if (!fillsCell(nid)) {
            return false;
        }
        if (!BlockTypeColors.isTransparent(nid)) {
            return true; // solid full cube fully covers the face
        }
        // Cell-filling transparent cube (glass): same-id neighbours share an
        // internal face, and a buried glass block doesn't reveal the interior
        // behind it (already drawn opaque); an exposed window still does.
        short sid = self.getBlockID();
        if (sid == nid) {
            return true;
        }
        return !hasAirNeighbor(grid, x, y, z);
    }

    /** Whether the cell at (x,y,z) has at least one empty (air) orthogonal neighbour. */
    private static boolean hasAirNeighbor(SparseMatrix<Block> grid, int x, int y, int z) {
        return !grid.contains(x + 1, y, z) || !grid.contains(x - 1, y, z)
                || !grid.contains(x, y + 1, z) || !grid.contains(x, y - 1, z)
                || !grid.contains(x, y, z + 1) || !grid.contains(x, y, z - 1);
    }

    /** {@link #hidesFace} in a direction from {@code p} (dir components are rounded). */
    private static boolean hidesFaceDir(SparseMatrix<Block> grid, Point3i p, Block self, float[] dir, int sign) {
        return hidesFace(grid, self, p.x + Math.round(dir[0] * sign),
                p.y + Math.round(dir[1] * sign),
                p.z + Math.round(dir[2] * sign));
    }

    /**
     *
     * @param group
     * @param lower
     * @param upper
     * @param colors
     */
    public static void addBox(MeshInfo group, Point3f lower, Point3f upper, short[] colors) {
        if ((lower == null) || (upper == null)) {
            return;
        }
        lower = new Point3f(lower.x - .5f, lower.y - .5f, lower.z - .5f);
        upper = new Point3f(upper.x + .5f, upper.y + .5f, upper.z + .5f); // only place where bounds are at +1
        addSelectFace(group, upper.x, lower.y, lower.z, upper.x, upper.y, upper.z,
                RenderPoly.XP, colors[0 % colors.length]);
        addSelectFace(group, lower.x, lower.y, lower.z, lower.x, upper.y, upper.z,
                RenderPoly.XM, colors[1 % colors.length]);
        addSelectFace(group, lower.x, upper.y, lower.z, upper.x, upper.y, upper.z,
                RenderPoly.YP, colors[2 % colors.length]);
        addSelectFace(group, lower.x, lower.y, lower.z, upper.x, lower.y, upper.z,
                RenderPoly.YM, colors[3 % colors.length]);
        addSelectFace(group, lower.x, lower.y, upper.z, upper.x, upper.y, upper.z,
                RenderPoly.ZP, colors[4 % colors.length]);
        addSelectFace(group, lower.x, lower.y, lower.z, upper.x, upper.y, lower.z,
                RenderPoly.ZM, colors[5 % colors.length]);
    }

    /**
     * Outlines a set of selected cells by their exact shape: emits, for every cell,
     * only the faces whose orthogonal neighbour is <em>not</em> selected. Rendered
     * as wireframe this traces the true silhouette of an irregular selection (a
     * flood-fill, a scattered multi-pick) instead of a single min/max bounding box.
     * Each cell's cube is inflated by {@code e} so the edges don't z-fight the blocks.
     */
    public static void addSelectionCells(MeshInfo group, java.util.Collection<Point3i> cells,
            short[] colors, float e) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        java.util.Set<Point3i> set = (cells instanceof java.util.Set)
                ? (java.util.Set<Point3i>) cells : new java.util.HashSet<>(cells);
        for (Point3i p : set) {
            float lox = p.x - .5f - e, loy = p.y - .5f - e, loz = p.z - .5f - e;
            float hix = p.x + .5f + e, hiy = p.y + .5f + e, hiz = p.z + .5f + e;
            if (!set.contains(new Point3i(p.x + 1, p.y, p.z))) {
                addSelectFace(group, hix, loy, loz, hix, hiy, hiz, RenderPoly.XP, colors[0 % colors.length]);
            }
            if (!set.contains(new Point3i(p.x - 1, p.y, p.z))) {
                addSelectFace(group, lox, loy, loz, lox, hiy, hiz, RenderPoly.XM, colors[1 % colors.length]);
            }
            if (!set.contains(new Point3i(p.x, p.y + 1, p.z))) {
                addSelectFace(group, lox, hiy, loz, hix, hiy, hiz, RenderPoly.YP, colors[2 % colors.length]);
            }
            if (!set.contains(new Point3i(p.x, p.y - 1, p.z))) {
                addSelectFace(group, lox, loy, loz, hix, loy, hiz, RenderPoly.YM, colors[3 % colors.length]);
            }
            if (!set.contains(new Point3i(p.x, p.y, p.z + 1))) {
                addSelectFace(group, lox, loy, hiz, hix, hiy, hiz, RenderPoly.ZP, colors[4 % colors.length]);
            }
            if (!set.contains(new Point3i(p.x, p.y, p.z - 1))) {
                addSelectFace(group, lox, loy, loz, hix, hiy, loz, RenderPoly.ZM, colors[5 % colors.length]);
            }
        }
    }

    /**
     *
     * @param group
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param face
     * @param type
     */
    public static void addSelectFace(MeshInfo group, float x1, float y1, float z1, float x2, float y2, float z2,
            int face, short type) {
        addSelectFace(group, x1, y1, z1, x2, y2, z2, face, type, 0);
    }

    /** As {@link #addSelectFace}, with the block {@code orientation} for per-face texture selection. */
    public static void addSelectFace(MeshInfo group, float x1, float y1, float z1, float x2, float y2, float z2,
            int face, short type, int orientation) {
        if (MathUtils.epsilonEquals(x1, x2)) {
            if (face == RenderPoly.XP) {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x1, y1, z2),
                        new Point3f(x1, y2, z2),
                        new Point3f(x1, y2, z1),
                        type, face, orientation);
            } else {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x1, y2, z1),
                        new Point3f(x1, y2, z2),
                        new Point3f(x1, y1, z2),
                        type, face, orientation);
            }
        } else if (MathUtils.epsilonEquals(y1, y2)) {
            if (face == RenderPoly.YP) {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x2, y1, z1),
                        new Point3f(x2, y1, z2),
                        new Point3f(x1, y1, z2),
                        type, face, orientation);
            } else {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x1, y1, z2),
                        new Point3f(x2, y1, z2),
                        new Point3f(x2, y1, z1),
                        type, face, orientation);
            }
        } else if (MathUtils.epsilonEquals(z1, z2)) {
            if (face == RenderPoly.ZP) {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x1, y2, z1),
                        new Point3f(x2, y2, z1),
                        new Point3f(x2, y1, z1),
                        type, face, orientation);
            } else {
                addSelectQuad(group, new Point3f(x1, y1, z1),
                        new Point3f(x2, y1, z1),
                        new Point3f(x2, y2, z1),
                        new Point3f(x1, y2, z1),
                        type, face, orientation);
            }
        }
    }

    /**
     *
     * @param info
     * @param left
     * @param top
     * @param right
     * @param bottom
     * @param type
     */
    public static void addSelectQuad(MeshInfo info, Point3f left, Point3f top, Point3f right, Point3f bottom,
            short type) {
        addSelectQuad(info, left, top, right, bottom, type, -1, 0);
    }

    /**
     * Adds one quad. {@code face} is a {@link RenderPoly} cube face (XP..ZM) so
     * the quad samples that face's own texture on multi-textured blocks (with the
     * block's {@code orientation} applied); pass -1 (e.g. for shaped-block faces)
     * to use the block's primary texture.
     */
    public static void addSelectQuad(MeshInfo info, Point3f left, Point3f top, Point3f right, Point3f bottom,
            short type, int face, int orientation) {
        info.verts.add(left);
        info.verts.add(top);
        info.verts.add(right);
        info.verts.add(bottom);
        info.indexes.add(info.verts.size() - 1);
        info.indexes.add(info.verts.size() - 2);
        info.indexes.add(info.verts.size() - 3);
        info.indexes.add(info.verts.size() - 4);
        if (info.colors != null) {
            Color c = BlockTypeColors.getFillColor(type);
            Color3f color = new Color3f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
            info.colors.add(color);
            info.colors.add(color);
            info.colors.add(color);
            info.colors.add(color);
        }
        if (info.uv != null) {
            Rectangle2D.Float rec = (face >= 0)
                    ? BlockTypeColors.getFaceTextureLocation(type, face, orientation)
                    : BlockTypeColors.getAllTextureLocation(type);
            Point3f[] quad = {left, top, right, bottom};
            for (int k = 0; k < 4; k++) {
                int idx;
                if (face >= 0) {
                    // Absolute StarMade port: identify which geometric corner of the
                    // face this vertex is (cornerKey), then look up the exact tile
                    // corner StarMade's texOrder assigns it for this orientation.
                    int cornerKey = faceCornerKey(face, quad[k], left, top, right, bottom);
                    idx = BlockTypeColors.getFaceCornerUvIdx(type, face, orientation, cornerKey);
                    if (idx < 0) {
                        idx = SHAPED_CORNER_IDX[k];
                    }
                } else {
                    // Shaped-block face (no cube side): keep the simple corner mapping.
                    idx = SHAPED_CORNER_IDX[k];
                }
                float u = idx & 1;
                float v = (idx >> 1) & 1;
                info.uv.add(new Point2f(rec.x + u * rec.width, rec.y + v * rec.height));
            }
        }
    }

    // Fallback tile-corner indices (u + 2v) for shaped-block faces that don't map
    // to a cube side: c0=(0,0), c1=(1,0), c2=(1,1), c3=(0,1).
    private static final int[] SHAPED_CORNER_IDX = {0, 1, 3, 2};

    // Per RenderPoly face (XP..ZM), the two tangent world axes {axisA, axisB}
    // (0=x,1=y,2=z). axisA carries corner-key bit 0, axisB bit 1 — matching
    // StarMade's quadPosMark decode, so cornerKey lines up with vertexOrderMap.
    private static final int[][] FACE_TANGENT_AXES = {
        {1, 2}, // XP  -> RIGHT  (+X): A=Y, B=Z
        {1, 2}, // XM  -> LEFT   (-X): A=Y, B=Z
        {0, 2}, // YP  -> TOP    (+Y): A=X, B=Z
        {0, 2}, // YM  -> BOTTOM (-Y): A=X, B=Z
        {1, 0}, // ZP  -> FRONT  (+Z): A=Y, B=X
        {1, 0}, // ZM  -> BACK   (-Z): A=Y, B=X
    };

    /**
     * Which geometric corner of cube face {@code face} the vertex {@code vtx} is:
     * a key in {@code axisA_bit + 2*axisB_bit} form (bit set = the vertex sits on
     * the high side of that tangent axis). {@code a,b,c,d} are the face's 4 corners
     * (used to find the face centre). Matches StarMade's vertexOrderMap corner codes.
     */
    private static int faceCornerKey(int face, Point3f vtx, Point3f a, Point3f b, Point3f c, Point3f d) {
        int axisA = FACE_TANGENT_AXES[face][0];
        int axisB = FACE_TANGENT_AXES[face][1];
        float midA = (comp(a, axisA) + comp(b, axisA) + comp(c, axisA) + comp(d, axisA)) * 0.25f;
        float midB = (comp(a, axisB) + comp(b, axisB) + comp(c, axisB) + comp(d, axisB)) * 0.25f;
        int aBit = comp(vtx, axisA) > midA ? 1 : 0;
        int bBit = comp(vtx, axisB) > midB ? 1 : 0;
        return aBit + 2 * bBit;
    }

    private static float comp(Point3f p, int axis) {
        return axis == 0 ? p.x : (axis == 1 ? p.y : p.z);
    }

    /**
     * Reorders a transparent mesh's quads back-to-front relative to {@code cam}
     * (world/model space, same space as the block vertices) so alpha blending
     * composites in the correct order — without this, stacked glass blends in
     * index order and shows artifacts. Cheap enough to call on camera moves, and
     * safe to run while the render thread draws (both hold the obj's monitor).
     */
    public static void sortTransparentQuads(JGLObj obj, Point3f cam) {
        synchronized (obj) {
            // Only the quad atlas mesh is sorted this way; LOD-model meshes are
            // TRIANGLES (n/3, not n/4) and would be corrupted by the quad reorder.
            if (obj.getMode() != JGLObj.QUADS) {
                return;
            }
            java.nio.FloatBuffer vb = obj.getVertexBuffer();
            if (vb == null) {
                return;
            }
            java.nio.IntBuffer ib = obj.getIndexIntBuffer();
            java.nio.ShortBuffer sb = (ib == null) ? obj.getIndexShortBuffer() : null;
            int n = ib != null ? ib.limit() : (sb != null ? sb.limit() : 0);
            int quads = n / 4;
            if (quads < 2) {
                return;
            }
            int[] idx = new int[n];
            if (ib != null) {
                for (int i = 0; i < n; i++) {
                    idx[i] = ib.get(i);
                }
            } else {
                for (int i = 0; i < n; i++) {
                    idx[i] = sb.get(i) & 0xFFFF;
                }
            }
            float[] dist = new float[quads];
            Integer[] order = new Integer[quads];
            for (int q = 0; q < quads; q++) {
                order[q] = q;
                float cx = 0, cy = 0, cz = 0;
                for (int k = 0; k < 4; k++) {
                    int vi = idx[q * 4 + k] * 3;
                    cx += vb.get(vi);
                    cy += vb.get(vi + 1);
                    cz += vb.get(vi + 2);
                }
                cx *= 0.25f;
                cy *= 0.25f;
                cz *= 0.25f;
                float dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
                dist[q] = dx * dx + dy * dy + dz * dz;
            }
            java.util.Arrays.sort(order, (a, b) -> Float.compare(dist[b], dist[a])); // farthest first
            if (ib != null) {
                for (int q = 0; q < quads; q++) {
                    int src = order[q] * 4;
                    for (int k = 0; k < 4; k++) {
                        ib.put(q * 4 + k, idx[src + k]);
                    }
                }
                ib.position(0);
            } else {
                for (int q = 0; q < quads; q++) {
                    int src = order[q] * 4;
                    for (int k = 0; k < 4; k++) {
                        sb.put(q * 4 + k, (short) idx[src + k]);
                    }
                }
                sb.position(0);
            }
        }
    }
}

class MeshInfo {

    List<Point3f> verts;
    List<Integer> indexes;
    List<Color3f> colors;
    List<Point2f> uv;
}
