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
import java.util.UUID;

/**
 * An editor-only organizational folder in a {@link Scene}'s outliner. Groups let
 * the user collect objects for bulk visibility/rename operations; they are
 * <em>independent</em> of the docking tree (see {@link SceneObject#getDockParentId()}),
 * which is the game-meaningful parent/child relationship.
 */
public final class SceneGroup {

    private String id;
    private String name;
    private boolean expanded = true;
    private final List<String> memberIds = new ArrayList<>();

    public SceneGroup() {
        this(UUID.randomUUID().toString(), "");
    }

    public SceneGroup(String id, String name) {
        this.id = id;
        this.name = name;
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

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /** Mutable list of {@link SceneObject#getId()} values belonging to this group. */
    public List<String> getMemberIds() {
        return memberIds;
    }
}
