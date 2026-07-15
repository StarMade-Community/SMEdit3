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

import java.util.ArrayList;
import java.util.List;

/**
 * A Blender-style scene: many {@link SceneObject}s laid out in space, each with
 * its own local grid and world transform, optionally organized into editor
 * {@link SceneGroup}s. Serialized to/from the {@code .smedit} container by
 * {@link SceneLogic}.
 */
public final class Scene {

    private String name = "";
    private String author = "";
    /** Creation timestamp (ISO-8601 string); free-form, informational only. */
    private String created = "";

    private final List<SceneObject> objects = new ArrayList<>();
    private final List<SceneGroup> groups = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public List<SceneObject> getObjects() {
        return objects;
    }

    public List<SceneGroup> getGroups() {
        return groups;
    }

    /** @return the object with the given id, or {@code null} if absent. */
    public SceneObject objectById(String id) {
        if (id == null) {
            return null;
        }
        for (SceneObject o : objects) {
            if (id.equals(o.getId())) {
                return o;
            }
        }
        return null;
    }

    /** @return the objects directly docked to {@code parentId}. */
    public List<SceneObject> childrenOf(String parentId) {
        List<SceneObject> kids = new ArrayList<>();
        for (SceneObject o : objects) {
            if (parentId == null ? o.getDockParentId() == null : parentId.equals(o.getDockParentId())) {
                kids.add(o);
            }
        }
        return kids;
    }

    /** @return the objects with no dock parent (docking-tree roots). */
    public List<SceneObject> roots() {
        List<SceneObject> roots = new ArrayList<>();
        for (SceneObject o : objects) {
            String parent = o.getDockParentId();
            if (parent == null || parent.isEmpty() || objectById(parent) == null) {
                roots.add(o);
            }
        }
        return roots;
    }
}
