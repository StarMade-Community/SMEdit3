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

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.logic.utils.XMLUtils;
import smc.smedit.util.lwjgl.win.JGLTextureCache;

/**
 * Loads StarMade's 3D "LOD" block models (consoles, lights, pipes, grates,
 * capsules, …) and caches them for the renderer, so those blocks can be drawn as
 * their real mesh instead of a textured cube.
 *
 * <p>Resolution chain (all relative to the StarMade install's {@code data/} dir):
 * <ol>
 * <li>a block's {@code <LodShape>NAME</LodShape>} (parsed into
 * {@link smc.smedit.ui.BlockTypeColors#BLOCK_LOD_SHAPE}) names the model;</li>
 * <li>{@code data/config/mainConfig.xml} {@code <LOD path="/models/lod/">}
 * maps NAME → {@code filename}/{@code relpath}, e.g.
 * {@code <BlueConsole filename="Blue_Console" relpath="Console"/>};</li>
 * <li>{@code <relpath>/<filename>.scene} (OGRE XML) names the mesh + material;</li>
 * <li>{@code <meshFile>.mesh.xml} (OGRE XML) holds the triangles + per-vertex
 * position/texcoord — positions are already in unit-cube block-local space;</li>
 * <li>{@code <filename>.material} (OGRE material script) names the diffuse PNG.</li>
 * </ol>
 *
 * <p>The mesh vertices are used verbatim: StarMade's block-local coordinate frame
 * is the same one SMEdit's shaped-block geometry is ported from, so no axis swap
 * is needed. Each distinct diffuse PNG is registered once with
 * {@link JGLTextureCache} and drawn as its own {@code JGLObj}; models sharing a
 * texture (e.g. all console colours → {@code Consoles.png}) share the id.
 */
public final class LodModelCache {

    private LodModelCache() {
    }

    /** Model texture ids start well above block-id-based ids to avoid collisions. */
    private static final int TEXTURE_ID_BASE = 1_000_000;

    /**
     * OGRE texcoords are top-origin (v=0 at the image top) while
     * {@link JGLTextureCache} uploads rows bottom-up, so the mesh's v is flipped
     * to match. (If model textures ever appear vertically mirrored, toggle this.)
     */
    private static final boolean FLIP_V = true;

    /** Parsed model geometry (flat arrays) + the GL texture it samples. */
    public static final class LodModel {
        public final float[] positions; // n*3, block-local (centre origin, [-0.5,0.5])
        public final float[] uvs;       // n*2
        public final int[] tris;        // m*3 vertex indices
        public final int textureId;     // JGLTextureCache id, or 0 if untextured

        LodModel(float[] positions, float[] uvs, int[] tris, int textureId) {
            this.positions = positions;
            this.uvs = uvs;
            this.tris = tris;
            this.textureId = textureId;
        }
    }

    /** Sentinel cached for a model that failed to load (so we don't retry it). */
    private static final LodModel MISSING = new LodModel(new float[0], new float[0], new int[0], 0);

    private static final Map<String, LodModel> CACHE = new HashMap<>();
    /** model name → {relpath, filename} from mainConfig.xml (keys also lower-cased). */
    private static Map<String, String[]> index;
    /** diffuse PNG absolute path → allocated GL texture id. */
    private static final Map<String, Integer> textureIds = new HashMap<>();
    private static int nextTextureId = TEXTURE_ID_BASE;

    /**
     * @return the parsed model for a LodShape name, or {@code null} if it has no
     * mapping or any part failed to load (caller should fall back to a cube).
     */
    public static synchronized LodModel getModel(String lodShape) {
        if (lodShape == null || lodShape.isEmpty()) {
            return null;
        }
        LodModel cached = CACHE.get(lodShape);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        LodModel loaded = load(lodShape);
        CACHE.put(lodShape, loaded == null ? MISSING : loaded);
        return loaded;
    }

    /** Drops all cached models (e.g. when the StarMade dir / texture pack changes). */
    public static synchronized void clear() {
        CACHE.clear();
        index = null;
        textureIds.clear();
        nextTextureId = TEXTURE_ID_BASE;
    }

    private static LodModel load(String lodShape) {
        try {
            ensureIndex();
            String[] entry = index.get(lodShape);
            if (entry == null) {
                entry = index.get(lodShape.toLowerCase(Locale.ENGLISH));
            }
            if (entry == null) {
                return null;
            }
            String relpath = entry[0];
            String filename = entry[1];
            File lodDir = new File(StarMadeLogic.getInstance().getBaseDir(), "data/models/lod");
            File dir = (relpath == null || relpath.isEmpty() || ".".equals(relpath))
                    ? lodDir : new File(lodDir, relpath);

            // Scene → mesh file name + the node's bind transform (fall back to the
            // config filename convention). Some models (e.g. light rods) are stored
            // rotated and rely on the scene node's rotation to sit correctly — the
            // console's node is identity, rods' is a -90° X rotation.
            String meshFile = filename + ".mesh";
            float[] nodeXform = null;
            File sceneFile = new File(dir, filename + ".scene");
            Document scene = XMLUtils.readFile(sceneFile);
            if (scene != null) {
                Node entity = XMLUtils.findFirstNodeRecursive(scene, "entity");
                if (entity != null) {
                    String mf = XMLUtils.getAttribute(entity, "meshFile");
                    if (mf != null && !mf.isEmpty()) {
                        meshFile = mf;
                    }
                    nodeXform = parseNodeTransform(entity.getParentNode());
                }
            }

            File meshXml = new File(dir, meshFile + ".xml");
            if (!meshXml.isFile()) {
                meshXml = new File(dir, filename + ".mesh.xml");
            }
            Document mesh = XMLUtils.readFile(meshXml);
            if (mesh == null) {
                return null;
            }

            List<Float> pos = new ArrayList<>();
            List<Float> uv = new ArrayList<>();
            List<Integer> tris = new ArrayList<>();
            if (!parseMesh(mesh, pos, uv, tris) || tris.isEmpty()) {
                return null;
            }

            int textureId = resolveTexture(dir, filename);

            float[] positions = toFloatArray(pos);
            if (nodeXform != null) {
                applyNodeTransform(positions, nodeXform);
            }
            return new LodModel(positions, toFloatArray(uv), toIntArray(tris), textureId);
        } catch (RuntimeException e) {
            // Never let a malformed model take down the render thread — fall back.
            System.err.println("[LOD] failed to load model '" + lodShape + "': " + e);
            return null;
        }
    }

    /** Parses mainConfig.xml's {@code <LOD>} section into the name → file index. */
    private static void ensureIndex() {
        if (index != null) {
            return;
        }
        Map<String, String[]> map = new HashMap<>();
        File cfg = new File(StarMadeLogic.getInstance().getBaseDir(), "data/config/mainConfig.xml");
        Document doc = XMLUtils.readFile(cfg);
        if (doc != null) {
            Node lod = XMLUtils.findFirstNodeRecursive(doc, "LOD");
            if (lod != null) {
                for (Node child = lod.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    String name = child.getNodeName();
                    String filename = XMLUtils.getAttribute(child, "filename");
                    if (filename == null || filename.isEmpty()) {
                        continue;
                    }
                    String relpath = XMLUtils.getAttribute(child, "relpath");
                    String[] entry = {relpath, filename};
                    map.put(name, entry);
                    map.putIfAbsent(name.toLowerCase(Locale.ENGLISH), entry);
                }
            }
        }
        index = map;
    }

    /**
     * Parses an OGRE {@code .mesh.xml} into concatenated position/uv/triangle
     * lists. Handles multiple submeshes (each with its own vertexbuffer) and a
     * shared {@code <sharedgeometry>}, offsetting face indices per submesh.
     */
    private static boolean parseMesh(Document mesh, List<Float> pos, List<Float> uv, List<Integer> tris) {
        int sharedBase = -1;
        Node shared = XMLUtils.findFirstNode(mesh, "mesh/sharedgeometry");
        if (shared != null) {
            sharedBase = pos.size() / 3;
            parseVertexBuffer(shared, pos, uv);
        }
        List<Node> submeshes = XMLUtils.findAllNodesRecursive(mesh, "submesh");
        for (Node submesh : submeshes) {
            boolean useShared = "true".equalsIgnoreCase(XMLUtils.getAttribute(submesh, "usesharedvertices"));
            int base;
            if (useShared && sharedBase >= 0) {
                base = sharedBase;
            } else {
                base = pos.size() / 3;
                Node geometry = XMLUtils.findFirstNode(submesh, "geometry");
                if (geometry != null) {
                    parseVertexBuffer(geometry, pos, uv);
                }
            }
            Node faces = XMLUtils.findFirstNode(submesh, "faces");
            if (faces == null) {
                continue;
            }
            for (Node face = faces.getFirstChild(); face != null; face = face.getNextSibling()) {
                if (face.getNodeType() != Node.ELEMENT_NODE || !"face".equals(face.getNodeName())) {
                    continue;
                }
                tris.add(base + parseIntAttr(face, "v1"));
                tris.add(base + parseIntAttr(face, "v2"));
                tris.add(base + parseIntAttr(face, "v3"));
            }
        }
        return !pos.isEmpty();
    }

    /** Reads every {@code <vertex>} under a geometry/sharedgeometry node. */
    private static void parseVertexBuffer(Node geometryOrShared, List<Float> pos, List<Float> uv) {
        for (Node vertex : XMLUtils.findAllNodesRecursive(geometryOrShared, "vertex")) {
            Node position = XMLUtils.findFirstNode(vertex, "position");
            Node texcoord = XMLUtils.findFirstNode(vertex, "texcoord");
            pos.add(parseFloatAttr(position, "x"));
            pos.add(parseFloatAttr(position, "y"));
            pos.add(parseFloatAttr(position, "z"));
            float u = texcoord != null ? parseFloatAttr(texcoord, "u") : 0f;
            float v = texcoord != null ? parseFloatAttr(texcoord, "v") : 0f;
            uv.add(u);
            uv.add(FLIP_V ? 1f - v : v);
        }
    }

    /**
     * Reads a scene {@code <node>}'s bind transform (position, scale, rotation
     * quaternion). Returns {@code {px,py,pz, sx,sy,sz, qw,qx,qy,qz}}, or
     * {@code null} when the node is a plain identity (the common case) so callers
     * can skip the per-vertex work.
     */
    private static float[] parseNodeTransform(Node node) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        Node pos = XMLUtils.findFirstNode(node, "position");
        Node scale = XMLUtils.findFirstNode(node, "scale");
        Node rot = XMLUtils.findFirstNode(node, "rotation");
        float px = parseFloatAttr(pos, "x"), py = parseFloatAttr(pos, "y"), pz = parseFloatAttr(pos, "z");
        float sx = 1, sy = 1, sz = 1;
        if (scale != null) {
            sx = parseFloatAttrDefault(scale, "x", 1);
            sy = parseFloatAttrDefault(scale, "y", 1);
            sz = parseFloatAttrDefault(scale, "z", 1);
        }
        float qw = 1, qx = 0, qy = 0, qz = 0;
        if (rot != null) {
            qw = parseFloatAttrDefault(rot, "qw", 1);
            qx = parseFloatAttr(rot, "qx");
            qy = parseFloatAttr(rot, "qy");
            qz = parseFloatAttr(rot, "qz");
        }
        boolean identity = px == 0 && py == 0 && pz == 0 && sx == 1 && sy == 1 && sz == 1
                && qw == 1 && qx == 0 && qy == 0 && qz == 0;
        return identity ? null : new float[] {px, py, pz, sx, sy, sz, qw, qx, qy, qz};
    }

    /** Bakes a node bind transform (scale, then quaternion rotate, then translate) into positions. */
    private static void applyNodeTransform(float[] positions, float[] t) {
        float px = t[0], py = t[1], pz = t[2];
        float sx = t[3], sy = t[4], sz = t[5];
        float qw = t[6], qx = t[7], qy = t[8], qz = t[9];
        // Unit quaternion → rotation matrix (row-major).
        float m00 = 1 - 2 * (qy * qy + qz * qz), m01 = 2 * (qx * qy - qw * qz), m02 = 2 * (qx * qz + qw * qy);
        float m10 = 2 * (qx * qy + qw * qz), m11 = 1 - 2 * (qx * qx + qz * qz), m12 = 2 * (qy * qz - qw * qx);
        float m20 = 2 * (qx * qz - qw * qy), m21 = 2 * (qy * qz + qw * qx), m22 = 1 - 2 * (qx * qx + qy * qy);
        for (int i = 0; i + 2 < positions.length; i += 3) {
            float vx = positions[i] * sx, vy = positions[i + 1] * sy, vz = positions[i + 2] * sz;
            positions[i] = m00 * vx + m01 * vy + m02 * vz + px;
            positions[i + 1] = m10 * vx + m11 * vy + m12 * vz + py;
            positions[i + 2] = m20 * vx + m21 * vy + m22 * vz + pz;
        }
    }

    /**
     * Reads the diffuse PNG named in {@code <filename>.material} (the first
     * {@code texture ...} line = the first texture_unit) and registers it with
     * {@link JGLTextureCache}, returning its id (0 if none/missing).
     */
    private static int resolveTexture(File dir, String filename) {
        File materialFile = new File(dir, filename + ".material");
        String textureName = firstTextureName(materialFile);
        if (textureName == null) {
            return 0;
        }
        File textureFile = new File(dir, textureName);
        if (!textureFile.isFile()) {
            return 0;
        }
        String key = textureFile.getAbsolutePath();
        Integer id = textureIds.get(key);
        if (id != null) {
            return id;
        }
        // Load eagerly so a bad/corrupt PNG fails here (→ untextured) rather than
        // throwing on the render thread inside JGLTextureCache.getTexture.
        BufferedImage img;
        try {
            img = ImageIO.read(textureFile);
        } catch (IOException e) {
            return 0;
        }
        if (img == null) {
            return 0;
        }
        id = nextTextureId++;
        JGLTextureCache.register(id, img);
        textureIds.put(key, id);
        return id;
    }

    /** First {@code texture <name>} token in an OGRE material script, or null. */
    private static String firstTextureName(File materialFile) {
        if (!materialFile.isFile()) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(materialFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("texture ")) {
                    String name = t.substring("texture ".length()).trim();
                    // Some scripts append extra tokens (e.g. cubic/type) — take the
                    // first whitespace-delimited token.
                    int sp = name.indexOf(' ');
                    if (sp >= 0) {
                        name = name.substring(0, sp);
                    }
                    return name.isEmpty() ? null : name;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static float parseFloatAttr(Node n, String attr) {
        return parseFloatAttrDefault(n, attr, 0f);
    }

    private static float parseFloatAttrDefault(Node n, String attr, float def) {
        if (n == null) {
            return def;
        }
        String v = XMLUtils.getAttribute(n, attr);
        try {
            return v.isEmpty() ? def : Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseIntAttr(Node n, String attr) {
        String v = XMLUtils.getAttribute(n, attr);
        try {
            return v.isEmpty() ? 0 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }
}
