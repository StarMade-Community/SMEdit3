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

import java.util.UUID;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Logic;
import smc.smedit.vecmath.Matrix4f;

/**
 * A single entity placed in a {@link Scene}. Each object owns its <em>local</em>
 * block grid (origin-relative, exactly like StarMade gives every entity its own
 * grid) and a world {@link Matrix4f} transform that positions it in the scene.
 *
 * <p>Objects may form a docking tree via {@link #getDockParentId()} (the id of a
 * parent object, or {@code null}/empty for a root). That relationship is the
 * game-meaningful one and gates whether a multi-object selection can be exported
 * as one docked blueprint. Editor-only grouping lives separately in
 * {@link SceneGroup}.
 */
public final class SceneObject {

    private String id;
    private String name;
    private boolean visible = true;
    private boolean locked = false;
    /** Id of the object this one is docked to, or {@code null} for a root. */
    private String dockParentId;
    private final Matrix4f transform = new Matrix4f();

    /** The object's own origin-relative block grid. */
    private SparseMatrix<Block> grid = new SparseMatrix<>();
    /** Preserved control map, when this object came from a blueprint. May be {@code null}. */
    private Logic logic;

    /** Provenance (mirrors {@code ShipSpec}): where this object was imported from. */
    private String sourceType;
    private String sourceName;

    public SceneObject() {
        this(UUID.randomUUID().toString(), "");
    }

    public SceneObject(String id, String name) {
        this.id = id;
        this.name = name;
        transform.setIdentity();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getDockParentId() {
        return dockParentId;
    }

    public void setDockParentId(String dockParentId) {
        this.dockParentId = dockParentId;
    }

    /** The live world transform (mutate in place or {@link #setTransform}). */
    public Matrix4f getTransform() {
        return transform;
    }

    public void setTransform(Matrix4f m) {
        transform.set(m);
    }

    public SparseMatrix<Block> getGrid() {
        return grid;
    }

    public void setGrid(SparseMatrix<Block> grid) {
        this.grid = grid;
    }

    public Logic getLogic() {
        return logic;
    }

    public void setLogic(Logic logic) {
        this.logic = logic;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
