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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.logic.ShipSpec;
import smc.smedit.vecmath.Matrix4f;

/**
 * The live, in-editor {@link Scene}: the multi-entity document the Scene outliner
 * edits. Analogous to {@code SelectionModel}/{@code LayerModel}, it wraps the
 * current scene, tracks the <em>active</em> object, exposes structural operations
 * (add/remove/rename/visibility/group), and notifies listeners.
 *
 * <p><b>Active object ↔ legacy single-grid document.</b> The editor's paint/select
 * tools still operate on one grid ({@code StarMadeLogic.getModel()}). That grid is,
 * by reference, the <em>active</em> scene object's grid. Activating a different
 * object swaps the editable grid to it (rendered at the origin); every other
 * visible object renders around it as read-only context. When something external
 * replaces the model grid (open/undo/plugin), the active object's grid is repointed
 * so the scene stays consistent.
 */
public final class SceneModel {

    public interface Listener {
        void sceneChanged();
    }

    /** Grid spacing used to stagger imported objects so they don't overlap. */
    private static final float IMPORT_SPACING = 32f;

    private final StarMade mStarMade;
    private Scene mScene = new Scene();
    private String mActiveId;
    /** The {@code .smedit} file this scene was last opened from / saved to, or {@code null}. */
    private File mSceneFile;
    private final List<Listener> mListeners = new ArrayList<>();
    /** True while this model is the one driving a {@code StarMadeLogic.setModel} call. */
    private boolean mInternalSwap;
    /** Last-seen current-model spec, to tell a genuine open from an in-place grid swap. */
    private ShipSpec mLastSpec;

    public SceneModel(StarMade starMade) {
        this.mStarMade = starMade;
        mLastSpec = starMade.getCurrentModel();
        // Mirror an already-loaded entity as the first object.
        SparseMatrix<Block> current = starMade.getModel();
        if (current != null) {
            mScene.getObjects().add(mirrorObject(current));
            mActiveId = mScene.getObjects().get(0).getId();
        }
        // Track external model replacements (open a blueprint, undo/redo, plugins).
        starMade.addPropertyChangeListener("model", ev -> {
            if (!mInternalSwap) {
                onExternalModelChanged();
            }
        });
    }

    // ---- listeners ----

    public void addListener(Listener l) {
        mListeners.add(l);
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    private void fireChanged() {
        for (Listener l : new ArrayList<>(mListeners)) {
            l.sceneChanged();
        }
    }

    // ---- queries ----

    public Scene getScene() {
        return mScene;
    }

    /** The file this scene is bound to (drives a no-prompt {@code Save}), or {@code null}. */
    public File getSceneFile() {
        return mSceneFile;
    }

    public void setSceneFile(File file) {
        mSceneFile = file;
    }

    public String getActiveId() {
        return mActiveId;
    }

    public SceneObject getActiveObject() {
        return mScene.objectById(mActiveId);
    }

    public boolean isActive(SceneObject o) {
        return o != null && o.getId().equals(mActiveId);
    }

    /** The group an object belongs to (first group listing it), or {@code null}. */
    public SceneGroup groupOf(SceneObject o) {
        for (SceneGroup g : mScene.getGroups()) {
            if (g.getMemberIds().contains(o.getId())) {
                return g;
            }
        }
        return null;
    }

    public List<SceneObject> objectsInGroup(SceneGroup g) {
        List<SceneObject> out = new ArrayList<>();
        for (String id : g.getMemberIds()) {
            SceneObject o = mScene.objectById(id);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }

    public List<SceneObject> ungroupedObjects() {
        List<SceneObject> out = new ArrayList<>();
        for (SceneObject o : mScene.getObjects()) {
            if (groupOf(o) == null) {
                out.add(o);
            }
        }
        return out;
    }

    // ---- active-object swap ----

    /**
     * Makes {@code o} the editable document: its grid becomes {@code StarMadeLogic}'s
     * model (rendered at the origin, fully editable). Other visible objects become
     * context. No-op if already active.
     */
    public void setActive(SceneObject o) {
        if (o == null || o.getId().equals(mActiveId)) {
            return;
        }
        mActiveId = o.getId();
        mInternalSwap = true;
        try {
            StarMadeLogic.setModel(o.getGrid());
        } finally {
            mInternalSwap = false;
        }
        fireChanged();
    }

    /** External code replaced the model grid: keep the active object pointing at it. */
    private void onExternalModelChanged() {
        SparseMatrix<Block> grid = mStarMade.getModel();
        ShipSpec spec = mStarMade.getCurrentModel();
        boolean newLoad = spec != null && spec != mLastSpec; // open, not an undo/redo/plugin swap
        mLastSpec = spec;
        if (newLoad) {
            // Opening a fresh blueprint starts a document not yet bound to a scene file.
            mSceneFile = null;
        }
        SceneObject active = getActiveObject();
        if (grid == null) {
            fireChanged();
            return;
        }
        if (active == null) {
            SceneObject o = mirrorObject(grid);
            mScene.getObjects().add(o);
            mActiveId = o.getId();
        } else {
            active.setGrid(grid);
            if (newLoad && spec.getName() != null && !spec.getName().isEmpty()) {
                active.setName(spec.getName()); // opening a blueprint into the active slot renames it
            }
        }
        fireChanged();
    }

    // ---- structural ops ----

    /**
     * Opens a freshly-loaded entity (blueprint / file / imported model) as a NEW
     * scene object and makes it the active editable document — the "add to scene"
     * counterpart to {@link #loadScene} (which replaces the whole scene). Staggers
     * the object in space so it doesn't overlap existing ones, points the current
     * spec at it, and activates it via an internal swap (so this doesn't re-enter
     * {@link #onExternalModelChanged}). Returns the new object.
     */
    public SceneObject openEntity(String name, SparseMatrix<Block> grid, ShipSpec spec) {
        if (grid == null) {
            return null;
        }
        // Reuse a lone empty placeholder (the startup blank document) rather than
        // leaving it as a stray beside the new object; otherwise add a fresh object,
        // staggered so it doesn't overlap the existing ones.
        SceneObject o = loneEmptyPlaceholder();
        if (o == null) {
            o = new SceneObject();
            o.getTransform().setIdentity();
            o.getTransform().m03 = IMPORT_SPACING * mScene.getObjects().size();
            mScene.getObjects().add(o);
        } else {
            o.getTransform().setIdentity();
        }
        o.setName(name == null || name.isEmpty() ? "Object " + mScene.getObjects().size() : name);
        o.setGrid(grid);
        if (spec != null) {
            mStarMade.setCurrentModel(spec);
            mLastSpec = spec;
        }
        // Make it the editable document via an internal swap (so this doesn't re-enter
        // onExternalModelChanged). setActive short-circuits if it's already active, so
        // handle the reused-placeholder case (already active) explicitly.
        if (o.getId().equals(mActiveId)) {
            mInternalSwap = true;
            try {
                StarMadeLogic.setModel(grid);
            } finally {
                mInternalSwap = false;
            }
            fireChanged();
        } else {
            setActive(o);
        }
        return o;
    }

    /** The sole object when it's an empty placeholder, else {@code null}. */
    private SceneObject loneEmptyPlaceholder() {
        if (mScene.getObjects().size() != 1) {
            return null;
        }
        SceneObject only = mScene.getObjects().get(0);
        return (only.getGrid() == null || only.getGrid().size() == 0) ? only : null;
    }

    /** Adds an imported object, staggered in space, and returns it (does not activate). */
    public SceneObject importObject(String name, SparseMatrix<Block> grid) {
        SceneObject o = new SceneObject();
        o.setName(name == null || name.isEmpty() ? "Object " + (mScene.getObjects().size() + 1) : name);
        o.setGrid(grid);
        Matrix4f t = o.getTransform();
        t.setIdentity();
        t.m03 = IMPORT_SPACING * mScene.getObjects().size();
        mScene.getObjects().add(o);
        fireChanged();
        return o;
    }

    public void removeObject(SceneObject o) {
        if (o == null) {
            return;
        }
        mScene.getObjects().remove(o);
        for (SceneGroup g : mScene.getGroups()) {
            g.getMemberIds().remove(o.getId());
        }
        if (o.getId().equals(mActiveId)) {
            List<SceneObject> rest = mScene.getObjects();
            if (rest.isEmpty()) {
                mActiveId = null;
            } else {
                // Reactivate another object so there is always an editable document.
                mActiveId = null;
                setActive(rest.get(0));
                return; // setActive already fired
            }
        }
        fireChanged();
    }

    public void renameObject(SceneObject o, String name) {
        if (o != null && name != null && !name.isEmpty()) {
            o.setName(name);
            fireChanged();
        }
    }

    public void setVisible(SceneObject o, boolean visible) {
        if (o != null) {
            o.setVisible(visible);
            fireChanged();
        }
    }

    public void setDockParent(SceneObject child, SceneObject parent) {
        if (child != null) {
            child.setDockParentId(parent == null ? null : parent.getId());
            fireChanged();
        }
    }

    // ---- groups ----

    public SceneGroup newGroup(String name) {
        SceneGroup g = new SceneGroup();
        g.setName(name == null || name.isEmpty() ? "Group " + (mScene.getGroups().size() + 1) : name);
        mScene.getGroups().add(g);
        fireChanged();
        return g;
    }

    public void renameGroup(SceneGroup g, String name) {
        if (g != null && name != null && !name.isEmpty()) {
            g.setName(name);
            fireChanged();
        }
    }

    /** Removes the group; its members become ungrouped (objects are kept). */
    public void removeGroup(SceneGroup g) {
        if (g != null) {
            mScene.getGroups().remove(g);
            fireChanged();
        }
    }

    /** Puts {@code o} in exactly {@code group} (or none), removing it from any other group. */
    public void setObjectGroup(SceneObject o, SceneGroup group) {
        if (o == null) {
            return;
        }
        for (SceneGroup g : mScene.getGroups()) {
            g.getMemberIds().remove(o.getId());
        }
        if (group != null && !group.getMemberIds().contains(o.getId())) {
            group.getMemberIds().add(o.getId());
        }
        fireChanged();
    }

    // ---- whole-scene replace (Open Scene) ----

    /** Replaces the scene and activates its first object (swapping the editable grid). */
    public void loadScene(Scene scene) {
        mScene = scene != null ? scene : new Scene();
        mActiveId = null;
        SceneObject first = null;
        if (!mScene.getObjects().isEmpty()) {
            List<SceneObject> roots = mScene.roots();
            first = roots.isEmpty() ? mScene.getObjects().get(0) : roots.get(0);
        }
        if (first != null) {
            setActive(first); // fires + swaps the model
        } else {
            fireChanged();
        }
    }

    // ---- helpers ----

    private SceneObject mirrorObject(SparseMatrix<Block> grid) {
        ShipSpec spec = mStarMade.getCurrentModel();
        String name = spec != null && spec.getName() != null && !spec.getName().isEmpty()
                ? spec.getName() : "Object 1";
        SceneObject o = new SceneObject();
        o.setName(name);
        o.setGrid(grid);
        return o;
    }
}
