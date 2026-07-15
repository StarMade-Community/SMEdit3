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
package smc.smedit.util.voxel;

import smc.smedit.data.SparseMatrix;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;

/**
 * Turns a {@link VoxelGrid} into placed {@link Block}s.
 *
 * <p>When {@code hollow} is set (the default for StarMade ships), a voxel whose
 * six face-neighbours are all occupied is skipped, leaving only the outer shell
 * — the same interior-culling the original binvox importer did, centralized
 * here. Combined with {@link MeshVoxelizer}'s solid fill this yields a clean,
 * watertight one-block-thick hull.
 *
 * @author SMEdit3
 **/
public final class VoxelBlockMapper {

    private VoxelBlockMapper() {
    }

    public static SparseMatrix<Block> toBlocks(VoxelGrid grid, boolean hollow, IPluginCallback cb) {
        SparseMatrix<Block> out = new SparseMatrix<>();
        cb.setStatus("Mapping voxels to blocks");
        cb.startTask(grid.getSizeX());
        for (int x = 0; x < grid.getSizeX(); x++) {
            cb.workTask(1);
            for (int y = 0; y < grid.getSizeY(); y++) {
                for (int z = 0; z < grid.getSizeZ(); z++) {
                    if (!grid.isSet(x, y, z)) {
                        continue;
                    }
                    if (hollow && surrounded(grid, x, y, z)) {
                        continue;
                    }
                    out.set(x, y, z, new Block(grid.getColor(x, y, z)));
                }
            }
            if (cb.isPleaseCancel()) {
                break;
            }
        }
        cb.endTask();
        return out;
    }

    /** True only when all six face-neighbours exist and are occupied. */
    private static boolean surrounded(VoxelGrid grid, int x, int y, int z) {
        return neighbour(grid, x - 1, y, z)
                && neighbour(grid, x + 1, y, z)
                && neighbour(grid, x, y - 1, z)
                && neighbour(grid, x, y + 1, z)
                && neighbour(grid, x, y, z - 1)
                && neighbour(grid, x, y, z + 1);
    }

    private static boolean neighbour(VoxelGrid grid, int x, int y, int z) {
        return grid.inBounds(x, y, z) && grid.isSet(x, y, z);
    }
}
