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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Chunk;
import smc.smedit.ship.data.Data;
import smc.smedit.ship.data.Smd3Segment;
import smc.smedit.vecmath.Point3i;

/**
 * Reader for StarMade's current {@code .smd3} region/segment file format
 * (serialization <b>version 6</b>, 32³ segments). The legacy {@code .smd2}
 * (16³) format is still handled by {@link DataLogic}.
 *
 * <p>Layout (all container/record scalars big-endian; per-block values inside
 * the deflate payload little-endian):
 * <pre>
 * byte  version (=6)
 * 3     padding
 * 4096× { short offsetBiased; short size }     // index table (16388 bytes total)
 * then fixed 49152-byte sectors, one per present segment:
 *   byte version; long timestamp; int x; int y; int z;
 *   byte dataType (1=int-array blocks, 2=empty); int zipSize; byte[zipSize] zlib
 * </pre>
 * A stored {@code offsetBiased} of 0 means "absent"; otherwise the sector index
 * is {@code offsetBiased - 1}. A {@code size} of 0 means "empty". The zlib
 * payload inflates to exactly {@code 32768 * 3 = 98304} bytes.
 *
 * <p>Per-block 24-bit value: type = bits 0–10, hitpoints = bits 11–17,
 * active = bit 18, orientation = bits 19–23.
 */
public final class Smd3Logic {

    /** Container/serialization version this reader understands. */
    public static final int VERSION = 6;

    static final int REGION_DIM = 16;
    static final int SLOT_COUNT = REGION_DIM * REGION_DIM * REGION_DIM;   // 4096
    static final int HEADER_SIZE = Integer.BYTES + SLOT_COUNT * Short.BYTES * 2; // 16388
    static final int SECTOR_SIZE = 48 * 1024;                            // 49152
    static final int RECORD_HEADER_SIZE = 26;
    static final int SEG_DIM = Smd3Segment.DIM;                          // 32
    static final int BLOCKS_PER_SEGMENT = SEG_DIM * SEG_DIM * SEG_DIM;   // 32768
    static final int BYTES_PER_BLOCK = 3;
    static final int INFLATED_SIZE = BLOCKS_PER_SEGMENT * BYTES_PER_BLOCK; // 98304

    static final int DATA_TYPE_INT_ARRAY = 1;
    static final int DATA_TYPE_EMPTY = 2;

    private Smd3Logic() {
    }

    public static List<Smd3Segment> readSegments(InputStream is) throws IOException {
        return readSegments(is.readAllBytes());
    }

    /** Reads a whole {@code .smd3} file into the editor's {@link Data} model. */
    public static Data readData(InputStream is) throws IOException {
        return toData(readSegments(is));
    }

    /**
     * Converts decoded 32³ segments into the editor's 16³-chunk {@link Data}
     * model, splitting each segment into up to 8 chunks positioned by absolute
     * block coordinates. Empty sub-chunks are omitted.
     */
    public static Data toData(List<Smd3Segment> segments) {
        final int half = SEG_DIM / 2; // 16
        final List<Chunk> chunks = new ArrayList<>();
        for (final Smd3Segment seg : segments) {
            for (int cz = 0; cz < SEG_DIM; cz += half) {
                for (int cy = 0; cy < SEG_DIM; cy += half) {
                    for (int cx = 0; cx < SEG_DIM; cx += half) {
                        final Block[][][] sub = new Block[half][half][half];
                        boolean any = false;
                        for (int lx = 0; lx < half; lx++) {
                            for (int ly = 0; ly < half; ly++) {
                                for (int lz = 0; lz < half; lz++) {
                                    final Block b = seg.get(cx + lx, cy + ly, cz + lz);
                                    if (b != null) {
                                        sub[lx][ly][lz] = b;
                                        any = true;
                                    }
                                }
                            }
                        }
                        if (any) {
                            final Chunk chunk = new Chunk();
                            chunk.setPosition(new Point3i(seg.x + cx, seg.y + cy, seg.z + cz));
                            chunk.setBlocks(sub);
                            chunk.setTimestamp(seg.timestamp);
                            chunk.setType(1);
                            chunks.add(chunk);
                        }
                    }
                }
            }
        }
        final Data data = new Data();
        data.setChunks(chunks.toArray(new Chunk[0]));
        return data;
    }

    /** Parses a whole {@code .smd3} file (read into memory) into its segments. */
    public static List<Smd3Segment> readSegments(byte[] file) throws IOException {
        if (file.length < HEADER_SIZE) {
            throw new IOException(".smd3 file too small: " + file.length + " bytes");
        }
        final ByteBuffer buf = ByteBuffer.wrap(file); // big-endian by default
        final int version = buf.get() & 0xFF;
        if (version != VERSION) {
            throw new IOException("Unsupported .smd3 container version " + version
                    + " (expected " + VERSION + ")");
        }
        buf.position(Integer.BYTES); // skip the 3 padding bytes after the version byte

        final int[] offsetBiased = new int[SLOT_COUNT];
        final int[] size = new int[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            offsetBiased[i] = buf.getShort() & 0xFFFF;
            size[i] = buf.getShort() & 0xFFFF;
        }

        final List<Smd3Segment> segments = new ArrayList<>();
        final Inflater inflater = new Inflater();
        try {
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (offsetBiased[i] == 0 || size[i] == 0) {
                    continue; // absent or empty
                }
                final int sectorPos = HEADER_SIZE + (offsetBiased[i] - 1) * SECTOR_SIZE;
                final Smd3Segment seg = readRecord(file, sectorPos, inflater);
                if (seg != null) {
                    segments.add(seg);
                }
            }
        } finally {
            inflater.end();
        }
        return segments;
    }

    // ---- writing ----

    /**
     * Writes a {@link SparseMatrix} of blocks to {@code .smd3} region files in
     * {@code dataDir}, named {@code <baseName>.<fx>.<fy>.<fz>.smd3}. Existing
     * {@code .smd3}/{@code .smd2} files for this blueprint are removed first.
     */
    public static void writeFiles(SparseMatrix<Block> grid, File dataDir, String baseName) throws IOException {
        final File[] existing = dataDir.listFiles();
        if (existing != null) {
            for (final File f : existing) {
                if (f.getName().startsWith(baseName)
                        && (f.getName().endsWith(".smd3") || f.getName().endsWith(".smd2"))) {
                    f.delete();
                }
            }
        }
        final Map<Point3i, List<Smd3Segment>> byRegion = new HashMap<>();
        for (final Smd3Segment seg : segmentsFromGrid(grid)) {
            byRegion.computeIfAbsent(regionIndex(seg.x, seg.y, seg.z), k -> new ArrayList<>()).add(seg);
        }
        for (final Map.Entry<Point3i, List<Smd3Segment>> e : byRegion.entrySet()) {
            final Point3i f = e.getKey();
            final File out = new File(dataDir, baseName + "." + f.x + "." + f.y + "." + f.z + ".smd3");
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                writeRegionFile(e.getValue(), os);
            }
        }
    }

    /** Groups the grid's blocks into 32³ segments keyed by segment origin. */
    public static List<Smd3Segment> segmentsFromGrid(SparseMatrix<Block> grid) {
        final Map<Point3i, Block[][][]> byOrigin = new HashMap<>();
        for (final Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            final Point3i p = it.next();
            final int sx = Math.floorDiv(p.x, SEG_DIM) * SEG_DIM;
            final int sy = Math.floorDiv(p.y, SEG_DIM) * SEG_DIM;
            final int sz = Math.floorDiv(p.z, SEG_DIM) * SEG_DIM;
            byOrigin.computeIfAbsent(new Point3i(sx, sy, sz), k -> new Block[SEG_DIM][SEG_DIM][SEG_DIM])
                    [p.x - sx][p.y - sy][p.z - sz] = grid.get(p);
        }
        final List<Smd3Segment> segments = new ArrayList<>();
        for (final Map.Entry<Point3i, Block[][][]> e : byOrigin.entrySet()) {
            final Point3i o = e.getKey();
            segments.add(new Smd3Segment(o.x, o.y, o.z, 0L, e.getValue()));
        }
        return segments;
    }

    /** Writes one region file (up to 4096 segments sharing a region index). */
    public static void writeRegionFile(List<Smd3Segment> segments, OutputStream os) throws IOException {
        if (segments.size() > SLOT_COUNT) {
            throw new IOException("too many segments for one .smd3 region: " + segments.size());
        }
        final int[] slotOffset = new int[SLOT_COUNT]; // biased; 0 = absent
        final int[] slotSize = new int[SLOT_COUNT];
        final List<byte[]> records = new ArrayList<>();
        for (final Smd3Segment seg : segments) {
            final byte[] record = buildRecord(seg);
            final int slot = localSlotIndex(seg.x, seg.y, seg.z);
            slotOffset[slot] = records.size() + 1; // sector index + OFFSET_SHIFT
            slotSize[slot] = record.length;        // 26 + zipSize
            records.add(record);
        }

        final ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        header.put((byte) VERSION); // then 3 padding bytes (already zero)
        header.position(Integer.BYTES);
        for (int i = 0; i < SLOT_COUNT; i++) {
            header.putShort((short) slotOffset[i]);
            header.putShort((short) slotSize[i]);
        }
        os.write(header.array());

        final byte[] sectorPad = new byte[0];
        for (final byte[] record : records) {
            os.write(record);
            final int pad = SECTOR_SIZE - record.length;
            os.write(pad == 0 ? sectorPad : new byte[pad]); // pad each sector to the fixed stride
        }
    }

    private static byte[] buildRecord(Smd3Segment seg) throws IOException {
        final byte[] raw = new byte[INFLATED_SIZE];
        final Block[][][] b = seg.getBlocks();
        for (int index = 0; index < BLOCKS_PER_SEGMENT; index++) {
            final Block blk = b[index & 0x1F][(index >> 5) & 0x1F][(index >> 10) & 0x1F];
            int v = 0;
            if (blk != null && blk.getBlockID() > 0) {
                int hp = blk.getHitPoints();
                if (hp < 0) {
                    hp = 0;
                } else if (hp > 0x7F) {
                    hp = 0x7F;
                }
                v = (blk.getBlockID() & 0x7FF) | ((hp & 0x7F) << 11)
                        | ((blk.isActive() ? 1 : 0) << 18) | ((blk.getOrientation() & 0x1F) << 19);
            }
            final int di = index * BYTES_PER_BLOCK;
            raw[di] = (byte) v;
            raw[di + 1] = (byte) (v >> 8);
            raw[di + 2] = (byte) (v >> 16);
        }

        final Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        final ByteArrayOutputStream zos = new ByteArrayOutputStream();
        final byte[] tmp = new byte[8192];
        while (!deflater.finished()) {
            zos.write(tmp, 0, deflater.deflate(tmp));
        }
        deflater.end();
        final byte[] zip = zos.toByteArray();

        final ByteBuffer rec = ByteBuffer.allocate(RECORD_HEADER_SIZE + zip.length);
        rec.put((byte) VERSION);
        rec.putLong(seg.timestamp);
        rec.putInt(seg.x);
        rec.putInt(seg.y);
        rec.putInt(seg.z);
        rec.put((byte) DATA_TYPE_INT_ARRAY);
        rec.putInt(zip.length);
        rec.put(zip);
        return rec.array();
    }

    /** StarMade's region-file index for a block/segment origin coordinate. */
    static Point3i regionIndex(int blockX, int blockY, int blockZ) {
        return new Point3i(((blockX >> 5) + 8) >> 4, ((blockY >> 5) + 8) >> 4, ((blockZ >> 5) + 8) >> 4);
    }

    /** StarMade's header slot index (0..4095) for a block/segment origin. */
    static int localSlotIndex(int blockX, int blockY, int blockZ) {
        final int x = ((blockX >> 5) + 8) & 0xF;
        final int y = ((blockY >> 5) + 8) & 0xF;
        final int z = ((blockZ >> 5) + 8) & 0xF;
        return z * 256 + y * 16 + x;
    }

    private static Smd3Segment readRecord(byte[] file, int pos, Inflater inflater) throws IOException {
        if (pos + RECORD_HEADER_SIZE > file.length) {
            throw new IOException(".smd3 record header runs past end of file");
        }
        final ByteBuffer rec = ByteBuffer.wrap(file, pos, RECORD_HEADER_SIZE);
        rec.get();                       // per-record version (currently 6); migration TODO for < 6
        final long timestamp = rec.getLong();
        final int x = rec.getInt();
        final int y = rec.getInt();
        final int z = rec.getInt();
        final int dataType = rec.get() & 0xFF;
        final int zipSize = rec.getInt();

        final Block[][][] blocks = new Block[SEG_DIM][SEG_DIM][SEG_DIM];
        if (dataType == DATA_TYPE_EMPTY) {
            return new Smd3Segment(x, y, z, timestamp, blocks);
        }
        if (dataType != DATA_TYPE_INT_ARRAY) {
            return null; // legacy per-segment encodings not yet supported
        }
        if (pos + RECORD_HEADER_SIZE + zipSize > file.length) {
            throw new IOException(".smd3 record payload runs past end of file");
        }

        final byte[] raw = new byte[INFLATED_SIZE];
        inflater.reset();
        inflater.setInput(file, pos + RECORD_HEADER_SIZE, zipSize);
        try {
            final int n = inflater.inflate(raw);
            if (n != INFLATED_SIZE) {
                throw new IOException("segment inflated to " + n + " bytes, expected " + INFLATED_SIZE);
            }
        } catch (final DataFormatException e) {
            throw new IOException("segment decompression failed", e);
        }

        for (int index = 0; index < BLOCKS_PER_SEGMENT; index++) {
            final int di = index * BYTES_PER_BLOCK;
            final int v = (raw[di] & 0xFF) | ((raw[di + 1] & 0xFF) << 8) | ((raw[di + 2] & 0xFF) << 16);
            final int id = v & 0x7FF;
            if (id <= 0) {
                continue; // empty / air
            }
            final Block b = new Block();
            b.setBlockID((short) id);
            b.setHitPoints((short) ((v >> 11) & 0x7F));
            b.setActive(((v >> 18) & 0x1) == 1);
            b.setOrientation((short) ((v >> 19) & 0x1F));
            blocks[index & 0x1F][(index >> 5) & 0x1F][(index >> 10) & 0x1F] = b;
        }
        return new Smd3Segment(x, y, z, timestamp, blocks);
    }
}
