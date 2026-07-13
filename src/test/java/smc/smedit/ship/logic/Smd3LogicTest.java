package smc.smedit.ship.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import smc.smedit.data.SparseMatrix;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Data;
import smc.smedit.ship.data.Smd3Segment;
import smc.smedit.vecmath.Point3i;

class Smd3LogicTest {

    // ---- hermetic: build a synthetic .smd3 and read known values back ----

    @Test
    void decodesKnownBlocksFromSyntheticSmd3() throws IOException {
        // rows: {localX, localY, localZ, id, hp, active(0/1), orientation}
        byte[] file = singleSegmentSmd3(0, 0, 0, new int[][] {
            {0, 0, 0, 1, 50, 0, 0},     // core-ish, hp 50, orient 0
            {1, 0, 0, 5, 127, 1, 23},   // max hp (7 bits), active, orient 23
            {0, 1, 0, 2047, 0, 0, 31},  // max id (11 bits), orient 31 (5 bits)
        });

        List<Smd3Segment> segs = Smd3Logic.readSegments(file);
        assertEquals(1, segs.size(), "one present segment");

        Smd3Segment s = segs.get(0);
        assertEquals(0, s.x);
        assertEquals(0, s.y);
        assertEquals(0, s.z);
        assertEquals(3, s.blockCount(), "non-empty blocks");

        Block core = s.get(0, 0, 0);
        assertNotNull(core);
        assertEquals(1, core.getBlockID());
        assertEquals(50, core.getHitPoints());
        assertFalse(core.isActive());
        assertEquals(0, core.getOrientation());

        Block armor = s.get(1, 0, 0);
        assertEquals(5, armor.getBlockID());
        assertEquals(127, armor.getHitPoints());
        assertTrue(armor.isActive());
        assertEquals(23, armor.getOrientation());

        Block maxId = s.get(0, 1, 0);
        assertEquals(2047, maxId.getBlockID());
        assertEquals(0, maxId.getHitPoints());
        assertEquals(31, maxId.getOrientation());

        assertNull(s.get(5, 5, 5), "air stays null");
    }

    @Test
    void rejectsWrongContainerVersion() {
        byte[] file = new byte[Smd3Logic.HEADER_SIZE];
        file[0] = 2; // .smd2-era container version
        IOException e = assertThrows(IOException.class, () -> Smd3Logic.readSegments(file));
        assertTrue(e.getMessage().toLowerCase().contains("version"), e.getMessage());
    }

    @Test
    void mapsSegmentsToAbsoluteBlocksViaModel() throws IOException {
        // Segment at absolute origin (32, 0, 64); one block in the base sub-chunk
        // and one in the +16 x sub-chunk, to exercise the 32³ -> 8×16³ split.
        byte[] file = singleSegmentSmd3(32, 0, 64, new int[][] {
            {1, 2, 3, 5, 100, 0, 7},    // local (1,2,3)  -> abs (33, 2, 67)
            {17, 0, 0, 8, 50, 1, 2},    // local (17,0,0) -> abs (49, 0, 64), sub-chunk cx=16
        });

        Data data = Smd3Logic.readData(new ByteArrayInputStream(file));
        Map<Point3i, Data> map = new HashMap<>();
        map.put(new Point3i(1, 0, 2), data); // map key is unused by getBlocks
        SparseMatrix<Block> grid = ShipLogic.getBlocks(map);

        Block b1 = grid.get(33, 2, 67);
        assertNotNull(b1, "block at abs (33,2,67)");
        assertEquals(5, b1.getBlockID());
        assertEquals(7, b1.getOrientation());

        Block b2 = grid.get(49, 0, 64);
        assertNotNull(b2, "block at abs (49,0,64) from the +16 sub-chunk");
        assertEquals(8, b2.getBlockID());
        assertTrue(b2.isActive());

        assertNull(grid.get(0, 0, 0), "empty space stays empty");
    }

    @Test
    void roundTripsGridThroughSmd3() throws IOException {
        SparseMatrix<Block> grid = new SparseMatrix<>();
        grid.set(0, 0, 0, block(1, 25, false, 0));    // segment origin (0,0,0)
        grid.set(1, 2, 3, block(5, 100, false, 7));   // same segment
        grid.set(40, 0, 0, block(8, 50, true, 2));    // segment origin (32,0,0)

        List<Smd3Segment> segs = Smd3Logic.segmentsFromGrid(grid);
        assertEquals(2, segs.size(), "two distinct 32³ segments");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Smd3Logic.writeRegionFile(segs, baos);

        SparseMatrix<Block> rt = ShipLogic.getBlocks(Map.of(new Point3i(0, 0, 0),
                Smd3Logic.toData(Smd3Logic.readSegments(baos.toByteArray()))));

        assertEquals(grid.size(), rt.size(), "block count preserved");
        assertBlock(rt.get(0, 0, 0), 1, 25, false, 0);
        assertBlock(rt.get(1, 2, 3), 5, 100, false, 7);
        assertBlock(rt.get(40, 0, 0), 8, 50, true, 2);
        assertNull(rt.get(9, 9, 9));
    }

    @Test
    void writeFilesThenReadFilesRoundTripsAcrossRegions(@TempDir Path tmp) throws IOException {
        SparseMatrix<Block> grid = new SparseMatrix<>();
        grid.set(1, 1, 1, block(3, 60, false, 4));      // region (0,0,0)
        grid.set(600, 0, 0, block(5, 20, true, 1));     // segment 18 -> region (1,0,0)

        java.io.File dir = tmp.toFile();
        Smd3Logic.writeFiles(grid, dir, "TestShip");

        java.io.File[] smd3 = dir.listFiles((d, n) -> n.endsWith(".smd3"));
        assertNotNull(smd3);
        assertEquals(2, smd3.length, "two region files for two regions");

        Map<Point3i, Data> data = DataLogic.readFiles(dir, "TestShip", new NoopCallback());
        SparseMatrix<Block> rt = ShipLogic.getBlocks(data);

        assertEquals(grid.size(), rt.size());
        assertBlock(rt.get(1, 1, 1), 3, 60, false, 4);
        assertBlock(rt.get(600, 0, 0), 5, 20, true, 1);
    }

    private static Block block(int id, int hp, boolean active, int orient) {
        Block b = new Block();
        b.setBlockID((short) id);
        b.setHitPoints((short) hp);
        b.setActive(active);
        b.setOrientation((short) orient);
        return b;
    }

    private static void assertBlock(Block b, int id, int hp, boolean active, int orient) {
        assertNotNull(b);
        assertEquals(id, b.getBlockID());
        assertEquals(hp, b.getHitPoints());
        assertEquals(active, b.isActive());
        assertEquals(orient, b.getOrientation());
    }

    // ---- integration: real StarMade fixture, skipped when the source isn't present ----

    @Test
    void readsRealIsanthFixtureIfAvailable() throws IOException {
        File fixture = new File("/home/videogoose/Projects/StarMade/src/main/resources/"
                + "blueprints-default/Isanth Type-PNR-25-B/DATA/Isanth Type-PNR-25-B.0.0.0.smd3");
        assumeTrue(fixture.isFile(), "StarMade fixture not present; skipping");

        List<Smd3Segment> segs;
        try (InputStream in = new FileInputStream(fixture)) {
            segs = Smd3Logic.readSegments(in);
        }
        assertEquals(2, segs.size(), "Isanth has two present segments");
        assertTrue(segs.stream().anyMatch(s -> s.x == 0 && s.y == 0 && s.z == 0), "segment at (0,0,0)");
        assertTrue(segs.stream().anyMatch(s -> s.x == 0 && s.y == 0 && s.z == 32), "segment at (0,0,32)");

        int total = 0;
        for (Smd3Segment s : segs) {
            total += s.blockCount();
            for (Block[][] plane : s.getBlocks()) {
                for (Block[] col : plane) {
                    for (Block b : col) {
                        if (b != null) {
                            assertTrue(b.getBlockID() >= 1 && b.getBlockID() <= 2047,
                                    "block id out of range: " + b.getBlockID());
                            assertTrue(b.getOrientation() >= 0 && b.getOrientation() <= 31,
                                    "orientation out of range: " + b.getOrientation());
                        }
                    }
                }
            }
        }
        assertTrue(total > 10, "expected a real ship, got " + total + " blocks");
    }

    // ---- synthetic .smd3 builder ----

    private static byte[] singleSegmentSmd3(int segX, int segY, int segZ, int[][] blocks) {
        byte[] raw = new byte[Smd3Logic.INFLATED_SIZE];
        for (int[] blk : blocks) {
            int index = blk[2] * 1024 + blk[1] * 32 + blk[0];
            int v = (blk[3] & 0x7FF) | ((blk[4] & 0x7F) << 11) | ((blk[5] & 0x1) << 18) | ((blk[6] & 0x1F) << 19);
            int di = index * 3;
            raw[di] = (byte) v;
            raw[di + 1] = (byte) (v >> 8);
            raw[di + 2] = (byte) (v >> 16);
        }

        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream zos = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        while (!deflater.finished()) {
            zos.write(tmp, 0, deflater.deflate(tmp));
        }
        deflater.end();
        byte[] zip = zos.toByteArray();

        ByteBuffer rec = ByteBuffer.allocate(Smd3Logic.RECORD_HEADER_SIZE + zip.length);
        rec.put((byte) Smd3Logic.VERSION);
        rec.putLong(0L);              // timestamp
        rec.putInt(segX);
        rec.putInt(segY);
        rec.putInt(segZ);
        rec.put((byte) Smd3Logic.DATA_TYPE_INT_ARRAY);
        rec.putInt(zip.length);
        rec.put(zip);
        byte[] record = rec.array();

        byte[] file = new byte[Smd3Logic.HEADER_SIZE + record.length];
        ByteBuffer fb = ByteBuffer.wrap(file);
        fb.put((byte) Smd3Logic.VERSION); // container version
        fb.position(Integer.BYTES);        // skip padding to the index table
        int slot = localIndex(segX, segY, segZ);
        fb.position(Integer.BYTES + slot * 4);
        fb.putShort((short) 1);            // offsetBiased = 1 -> sector 0
        fb.putShort((short) record.length); // size = 26 + zipSize
        System.arraycopy(record, 0, file, Smd3Logic.HEADER_SIZE, record.length);
        return file;
    }

    /** StarMade's header slot index for a segment's world origin. */
    private static int localIndex(int wx, int wy, int wz) {
        int x = ((wx >> 5) + 8) & 0xF;
        int y = ((wy >> 5) + 8) & 0xF;
        int z = ((wz >> 5) + 8) & 0xF;
        return z * 256 + y * 16 + x;
    }

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
