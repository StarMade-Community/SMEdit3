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
package smc.smedit.scene;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.BlueprintLogic;
import smc.smedit.logic.utils.XMLUtils;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Blueprint;
import smc.smedit.ship.data.Logic;
import smc.smedit.ship.logic.LogicLogic;
import smc.smedit.ship.logic.ShipLogic;
import smc.smedit.ship.logic.Smbp5Logic;
import smc.smedit.ship.logic.Smd3Logic;
import smc.smedit.vecmath.Matrix4f;

/**
 * Reader/writer for the {@code .smedit} scene container.
 *
 * <p>A {@code .smedit} file is a ZIP archive:
 * <pre>
 * scene.xml                        manifest: object tree, transforms, groups, visibility
 * objects/&lt;id&gt;/header.smbph        one blueprint-style folder per object (its local grid)
 * objects/&lt;id&gt;/logic.smbpl
 * objects/&lt;id&gt;/meta.smbpm
 * objects/&lt;id&gt;/DATA/&lt;id&gt;.x.y.z.smd3
 * </pre>
 * Each object folder is written and read with the existing, tested blueprint
 * codecs ({@link Smbp5Logic}, {@link Smd3Logic}, {@link BlueprintLogic}); the
 * scene layer only adds the XML manifest and the ZIP container. The manifest is
 * DOM XML via {@link XMLUtils} (no new dependency).
 */
public final class SceneLogic {

    /** Current {@code scene.xml} schema version. */
    public static final int SCENE_VERSION = 1;
    private static final String OBJECTS_DIR = "objects";

    private SceneLogic() {
    }

    // ---- writing ----

    /** Writes {@code scene} to {@code smeditFile} as a self-contained {@code .smedit} ZIP. */
    public static void writeScene(Scene scene, File smeditFile) throws IOException {
        Path stage = Files.createTempDirectory("smedit-write-");
        try {
            // 1. per-object blueprint folders under objects/<id>/
            for (SceneObject obj : scene.getObjects()) {
                File objDir = new File(new File(stage.toFile(), OBJECTS_DIR), obj.getId());
                writeObjectFolder(obj, objDir);
            }
            // 2. scene.xml manifest
            writeManifest(scene, new File(stage.toFile(), "scene.xml"));
            // 3. zip the staging tree into the target file
            zipDir(stage.toFile(), smeditFile);
        } finally {
            deleteRecursively(stage.toFile());
        }
    }

    /**
     * Exports a single scene object as a standalone StarMade blueprint folder
     * (header/logic/meta + DATA), loadable by the game. Used by the outliner's
     * per-object "Export…" action.
     */
    public static void writeObjectAsBlueprint(SceneObject obj, File blueprintDir) throws IOException {
        writeObjectFolder(obj, blueprintDir);
    }

    /**
     * Writes one object's blocks as a StarMade blueprint folder. This is a
     * de-singletoned copy of {@link BlueprintLogic#saveBlueprint} that takes the
     * object's own grid/logic instead of reaching into {@code StarMadeLogic}.
     */
    private static void writeObjectFolder(SceneObject obj, File dir) throws IOException {
        dir.mkdirs();
        SparseMatrix<Block> grid = obj.getGrid();
        try (OutputStream headerOut = new FileOutputStream(new File(dir, "header.smbph"))) {
            Smbp5Logic.writeHeader(grid, headerOut);
        }
        try (OutputStream logicOut = new FileOutputStream(new File(dir, "logic.smbpl"))) {
            Logic preserved = obj.getLogic();
            if (preserved != null && !preserved.getControllers().isEmpty()) {
                LogicLogic.writeFile(preserved, logicOut, false);
            } else {
                Smbp5Logic.writeLogic(logicOut);
            }
        }
        try (OutputStream metaOut = new FileOutputStream(new File(dir, "meta.smbpm"))) {
            Smbp5Logic.writeMeta(metaOut);
        }
        File dataDir = new File(dir, "DATA");
        dataDir.mkdirs();
        Smd3Logic.writeFiles(grid, dataDir, obj.getId());
    }

    private static void writeManifest(Scene scene, File file) throws IOException {
        Document doc = XMLUtils.newDocument();
        if (doc == null) {
            throw new IOException("could not create scene.xml document");
        }
        Element root = doc.createElement("smeditScene");
        root.setAttribute("version", Integer.toString(SCENE_VERSION));
        root.setAttribute("generator", "SMEdit3");
        doc.appendChild(root);

        Element meta = doc.createElement("meta");
        meta.setAttribute("name", nz(scene.getName()));
        meta.setAttribute("created", nz(scene.getCreated()));
        meta.setAttribute("author", nz(scene.getAuthor()));
        root.appendChild(meta);

        Element objects = doc.createElement("objects");
        root.appendChild(objects);
        for (SceneObject obj : scene.getObjects()) {
            Element oe = doc.createElement("object");
            oe.setAttribute("id", obj.getId());
            oe.setAttribute("name", nz(obj.getName()));
            oe.setAttribute("visible", Boolean.toString(obj.isVisible()));
            oe.setAttribute("locked", Boolean.toString(obj.isLocked()));
            oe.setAttribute("dataPath", OBJECTS_DIR + "/" + obj.getId());
            oe.setAttribute("dockParent", nz(obj.getDockParentId()));
            Element te = doc.createElement("transform");
            writeMatrix(te, obj.getTransform());
            oe.appendChild(te);
            if (obj.getSourceType() != null || obj.getSourceName() != null) {
                Element se = doc.createElement("source");
                se.setAttribute("type", nz(obj.getSourceType()));
                se.setAttribute("name", nz(obj.getSourceName()));
                oe.appendChild(se);
            }
            objects.appendChild(oe);
        }

        Element groups = doc.createElement("groups");
        root.appendChild(groups);
        for (SceneGroup g : scene.getGroups()) {
            Element ge = doc.createElement("group");
            ge.setAttribute("id", g.getId());
            ge.setAttribute("name", nz(g.getName()));
            ge.setAttribute("expanded", Boolean.toString(g.isExpanded()));
            for (String memberId : g.getMemberIds()) {
                Element me = doc.createElement("member");
                me.setAttribute("ref", memberId);
                ge.appendChild(me);
            }
            groups.appendChild(ge);
        }

        if (!XMLUtils.writeFile(doc, file)) {
            throw new IOException("failed to write scene.xml");
        }
    }

    // ---- reading ----

    /** Reads a {@code .smedit} file back into a {@link Scene}. */
    public static Scene readScene(File smeditFile) throws IOException {
        Path stage = Files.createTempDirectory("smedit-read-");
        try {
            unzip(smeditFile, stage.toFile());
            return parse(stage.toFile());
        } finally {
            deleteRecursively(stage.toFile());
        }
    }

    private static Scene parse(File stage) throws IOException {
        Scene scene = new Scene();
        Document doc = XMLUtils.readFile(new File(stage, "scene.xml"));
        if (doc == null) {
            throw new IOException("scene.xml missing or unreadable");
        }
        Node root = doc.getDocumentElement();

        Node meta = XMLUtils.findFirstNode(root, "meta");
        if (meta != null) {
            scene.setName(XMLUtils.getAttribute(meta, "name"));
            scene.setCreated(XMLUtils.getAttribute(meta, "created"));
            scene.setAuthor(XMLUtils.getAttribute(meta, "author"));
        }

        IPluginCallback cb = new NoopCallback();
        for (Node on : XMLUtils.findAllNodesRecursive(root, "object")) {
            SceneObject obj = new SceneObject(XMLUtils.getAttribute(on, "id"),
                    XMLUtils.getAttribute(on, "name"));
            obj.setVisible(parseBool(XMLUtils.getAttribute(on, "visible"), true));
            obj.setLocked(parseBool(XMLUtils.getAttribute(on, "locked"), false));
            String dockParent = XMLUtils.getAttribute(on, "dockParent");
            obj.setDockParentId(dockParent.isEmpty() ? null : dockParent);

            Node te = XMLUtils.findFirstNode(on, "transform");
            if (te != null) {
                readMatrix(te, obj.getTransform());
            }
            Node se = XMLUtils.findFirstNode(on, "source");
            if (se != null) {
                obj.setSourceType(emptyToNull(XMLUtils.getAttribute(se, "type")));
                obj.setSourceName(emptyToNull(XMLUtils.getAttribute(se, "name")));
            }

            String dataPath = XMLUtils.getAttribute(on, "dataPath");
            File objDir = new File(stage, dataPath);
            Blueprint bp = BlueprintLogic.readBlueprint(objDir, cb);
            obj.setGrid(ShipLogic.getBlocks(bp.getData()));
            obj.setLogic(bp.getLogic());

            scene.getObjects().add(obj);
        }

        for (Node gn : XMLUtils.findAllNodesRecursive(root, "group")) {
            SceneGroup g = new SceneGroup(XMLUtils.getAttribute(gn, "id"),
                    XMLUtils.getAttribute(gn, "name"));
            g.setExpanded(parseBool(XMLUtils.getAttribute(gn, "expanded"), true));
            for (Node mn : XMLUtils.findAllNodesRecursive(gn, "member")) {
                g.getMemberIds().add(XMLUtils.getAttribute(mn, "ref"));
            }
            scene.getGroups().add(g);
        }
        return scene;
    }

    // ---- matrix <-> XML ----

    private static void writeMatrix(Element e, Matrix4f m) {
        e.setAttribute("m00", Float.toString(m.m00));
        e.setAttribute("m01", Float.toString(m.m01));
        e.setAttribute("m02", Float.toString(m.m02));
        e.setAttribute("m03", Float.toString(m.m03));
        e.setAttribute("m10", Float.toString(m.m10));
        e.setAttribute("m11", Float.toString(m.m11));
        e.setAttribute("m12", Float.toString(m.m12));
        e.setAttribute("m13", Float.toString(m.m13));
        e.setAttribute("m20", Float.toString(m.m20));
        e.setAttribute("m21", Float.toString(m.m21));
        e.setAttribute("m22", Float.toString(m.m22));
        e.setAttribute("m23", Float.toString(m.m23));
        e.setAttribute("m30", Float.toString(m.m30));
        e.setAttribute("m31", Float.toString(m.m31));
        e.setAttribute("m32", Float.toString(m.m32));
        e.setAttribute("m33", Float.toString(m.m33));
    }

    private static void readMatrix(Node n, Matrix4f m) {
        m.m00 = f(n, "m00", 1);
        m.m01 = f(n, "m01", 0);
        m.m02 = f(n, "m02", 0);
        m.m03 = f(n, "m03", 0);
        m.m10 = f(n, "m10", 0);
        m.m11 = f(n, "m11", 1);
        m.m12 = f(n, "m12", 0);
        m.m13 = f(n, "m13", 0);
        m.m20 = f(n, "m20", 0);
        m.m21 = f(n, "m21", 0);
        m.m22 = f(n, "m22", 1);
        m.m23 = f(n, "m23", 0);
        m.m30 = f(n, "m30", 0);
        m.m31 = f(n, "m31", 0);
        m.m32 = f(n, "m32", 0);
        m.m33 = f(n, "m33", 1);
    }

    private static float f(Node n, String attr, float def) {
        String v = XMLUtils.getAttribute(n, attr);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ---- ZIP helpers ----

    private static void zipDir(File srcDir, File zipFile) throws IOException {
        Path root = srcDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)));
                Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String name = root.relativize(p).toString().replace(File.separatorChar, '/');
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }

    /** Unzips into {@code destRoot}, guarding against zip-slip path traversal. */
    private static void unzip(File zip, File destRoot) throws IOException {
        Path root = destRoot.toPath().toAbsolutePath().normalize();
        try (InputStream fis = Files.newInputStream(zip.toPath());
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Refusing zip entry outside target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // best-effort cleanup of a temp dir
        }
    }

    // ---- misc ----

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null || s.isEmpty()) {
            return def;
        }
        return Boolean.parseBoolean(s);
    }

    /** No-op progress callback for the blueprint reader (scene I/O has no UI). */
    private static final class NoopCallback implements IPluginCallback {
        @Override public void setStatus(String status) { }
        @Override public void startTask(int size) { }
        @Override public void workTask(int amnt) { }
        @Override public void endTask() { }
        @Override public void setErrorTitle(String title) { }
        @Override public void setErrorDescription(String desc) { }
        @Override public void setError(Throwable t) { }
        @Override public boolean isPleaseCancel() { return false; }
    }
}
