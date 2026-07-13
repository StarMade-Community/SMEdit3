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
package smc.smedit.ship.logic;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

/**
 * Writers for the modern StarMade blueprint metadata files
 * {@code header.smbph} (dataVersion 5) and {@code meta.smbpm} (metaVersion 5),
 * matching what the current game reads. All scalars are big-endian; strings are
 * Java modified-UTF-8.
 *
 * <p>Reverse-engineered from StarMade's {@code BlueprintEntry} and verified
 * against the Isanth fixture. Combined with the {@code .smd3} block data from
 * {@link Smd3Logic}, this is what a StarMade-loadable save needs (aside from
 * {@code logic.smbpl}, whose v0 format still uses the legacy writer).
 */
public final class Smbp5Logic {

    public static final int HEADER_VERSION = 5;
    public static final int META_VERSION = 5;
    public static final int STRUCTURE_VERSION = 0;
    /** ControlElementMap disk serialization version (marker = -(1024 + this)). */
    private static final int CONTROL_MAP_SERIALIZATION_VERSION = 2;

    /** Entity type ordinal for a ship. */
    private static final int ENTITY_TYPE_SHIP = 0;
    private static final int CLASSIFICATION_GENERAL = 0;
    private static final byte META_FINISH = 1;

    /**
     * Game-version string stamped into the header. StarMade stores this for
     * informational/compat purposes; the exact value isn't critical to loading.
     */
    private static String gameVersion = "0.203.0";

    private Smbp5Logic() {
    }

    public static void setGameVersion(String version) {
        if (version != null && !version.isEmpty()) {
            gameVersion = version;
        }
    }

    /**
     * Writes a v5 {@code header.smbph} for the given block grid: bounding box +
     * per-block-id element counts. No score block is emitted (StarMade derives
     * mass/price from the element counts).
     */
    public static void writeHeader(SparseMatrix<Block> grid, OutputStream os) throws IOException {
        final DataOutputStream out = new DataOutputStream(os);

        // Tally element counts (ascending block id) and the bounding box.
        final Map<Short, Integer> counts = new TreeMap<>();
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        boolean first = true;
        for (final Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            final Point3i p = it.next();
            final Block b = grid.get(p);
            if (b == null || b.getBlockID() <= 0) {
                continue;
            }
            counts.merge(b.getBlockID(), 1, Integer::sum);
            if (first) {
                minX = maxX = p.x;
                minY = maxY = p.y;
                minZ = maxZ = p.z;
                first = false;
            } else {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }
        }

        out.writeInt(HEADER_VERSION);
        out.writeUTF(gameVersion);
        out.writeInt(ENTITY_TYPE_SHIP);
        out.writeInt(CLASSIFICATION_GENERAL);
        out.writeFloat(minX);
        out.writeFloat(minY);
        out.writeFloat(minZ);
        out.writeFloat(maxX);
        out.writeFloat(maxY);
        out.writeFloat(maxZ);
        out.writeInt(counts.size());
        for (final Map.Entry<Short, Integer> e : counts.entrySet()) {
            out.writeShort(e.getKey());
            out.writeInt(e.getValue());
        }
        out.writeBoolean(false); // no score block
        out.flush();
    }

    /**
     * Writes a minimal valid v5 {@code meta.smbpm}: {@code int version=5} then a
     * {@code FINISH} token. StarMade's meta reader accepts this (no docks, cargo,
     * rails, AI, or manager tag).
     *
     * <p>NOTE: a fully spawn-ready ship normally also carries a {@code SEG_MANAGER}
     * manager tag (power/shield/etc. state). If StarMade's spawn path rejects a
     * manager-less meta, this needs escalating to the "safe empty" token stream —
     * a follow-up to verify against the running game.
     */
    public static void writeMeta(OutputStream os) throws IOException {
        final DataOutputStream out = new DataOutputStream(os);
        out.writeInt(META_VERSION);
        out.writeByte(META_FINISH);
        out.flush();
    }

    /**
     * Writes a valid v0 {@code logic.smbpl} with an <em>empty</em> control-element
     * map: {@code int structureVersion(0)}, the disk marker
     * {@code -(1024 + serializationVersion)}, then {@code int 0} (no entries).
     *
     * <p>NOTE: this loads in StarMade but leaves systems unlinked (no
     * weapon-computer→module, logic-wiring, etc. connections). Preserving the
     * actual control-element map from the edited ship is a follow-up enhancement.
     */
    public static void writeLogic(OutputStream os) throws IOException {
        final DataOutputStream out = new DataOutputStream(os);
        out.writeInt(STRUCTURE_VERSION);
        out.writeInt(-(1024 + CONTROL_MAP_SERIALIZATION_VERSION));
        out.writeInt(0); // empty control-element map
        out.flush();
    }
}
