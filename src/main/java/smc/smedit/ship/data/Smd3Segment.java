/**
 * Copyright 2014 SMEdit
 * https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
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
 */
package smc.smedit.ship.data;

/**
 * A single decoded 32×32×32 segment from a modern StarMade {@code .smd3} region
 * file (serialization version 6).
 *
 * <p>{@link #x}/{@link #y}/{@link #z} are the segment's absolute origin in block
 * coordinates (multiples of 32), read verbatim from the segment record.
 * {@link #blocks} is indexed {@code [x][y][z]} within the segment (0–31); a
 * {@code null} entry is empty space (air / block id ≤ 0).
 */
public final class Smd3Segment {

    /** Segment edge length for the current (32³) format. */
    public static final int DIM = 32;

    public final int x;
    public final int y;
    public final int z;
    public final long timestamp;
    private final Block[][][] blocks;

    public Smd3Segment(int x, int y, int z, long timestamp, Block[][][] blocks) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
        this.blocks = blocks;
    }

    /** Local-coordinate block array, {@code [x][y][z]} in 0..31; {@code null} = empty. */
    public Block[][][] getBlocks() {
        return blocks;
    }

    /** Block at local segment coordinates (0..31), or {@code null} if empty. */
    public Block get(int lx, int ly, int lz) {
        return blocks[lx][ly][lz];
    }

    /** Number of non-empty blocks in this segment. */
    public int blockCount() {
        int n = 0;
        for (int lx = 0; lx < DIM; lx++) {
            for (int ly = 0; ly < DIM; ly++) {
                for (int lz = 0; lz < DIM; lz++) {
                    if (blocks[lx][ly][lz] != null) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
