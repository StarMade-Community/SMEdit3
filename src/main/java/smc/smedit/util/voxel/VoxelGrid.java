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

import java.util.BitSet;

/**
 * A dense 3D occupancy grid with a StarMade block id per voxel. This is the
 * in-memory result of {@link MeshVoxelizer} and the input to
 * {@link VoxelBlockMapper}.
 *
 * <p>Occupancy is stored in a {@link BitSet} (one bit per cell) and the chosen
 * block id in a parallel {@code short[]} (0 = empty). A voxel is addressed by
 * {@code index = x*sy*sz + y*sz + z}. This is the pure-Java, in-house
 * replacement for the external <em>binvox</em> tool's {@code .binvox} grid.
 *
 * @author SMEdit3
 **/
public final class VoxelGrid {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet occupied;
    private final short[] color;

    public VoxelGrid(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "Voxel grid dimensions must be positive: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        int cells = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        this.occupied = new BitSet(cells);
        this.color = new short[cells];
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    /** Total number of cells (occupied or not). */
    public int cellCount() {
        return color.length;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private int index(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + z;
    }

    /** Marks a voxel occupied and records the block id it should become. */
    public void set(int x, int y, int z, short blockId) {
        int i = index(x, y, z);
        occupied.set(i);
        color[i] = blockId;
    }

    public boolean isSet(int x, int y, int z) {
        return occupied.get(index(x, y, z));
    }

    /** The block id stored at a voxel; only meaningful when {@link #isSet}. */
    public short getColor(int x, int y, int z) {
        return color[index(x, y, z)];
    }

    /** Number of occupied voxels. */
    public int occupiedCount() {
        return occupied.cardinality();
    }
}
