package smc.smedit.ship.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;

class Smbp5LogicTest {

    @Test
    void writesValidV5Header() throws IOException {
        SparseMatrix<Block> grid = new SparseMatrix<>();
        grid.set(1, 2, 3, blk(1));       // id 1
        grid.set(1, 2, 4, blk(5));       // id 5
        grid.set(2, 2, 3, blk(5));       // id 5 again
        grid.set(-3, 0, 10, blk(3));     // id 3, extends bounds

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Smbp5Logic.writeHeader(grid, baos);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        assertEquals(5, in.readInt(), "dataVersion");
        in.readUTF();                              // game-version string
        assertEquals(0, in.readInt(), "entity type = ship");
        assertEquals(0, in.readInt(), "classification");
        // bounding box min then max
        assertEquals(-3f, in.readFloat());
        assertEquals(0f, in.readFloat());
        assertEquals(3f, in.readFloat());
        assertEquals(2f, in.readFloat());
        assertEquals(2f, in.readFloat());
        assertEquals(10f, in.readFloat());
        // element-count manifest, ascending block id
        assertEquals(3, in.readInt(), "distinct block ids");
        assertEquals(1, in.readShort()); assertEquals(1, in.readInt());
        assertEquals(3, in.readShort()); assertEquals(1, in.readInt());
        assertEquals(5, in.readShort()); assertEquals(2, in.readInt());
        assertFalse(in.readBoolean(), "no score block");
        assertEquals(0, in.available(), "header fully consumed");
    }

    @Test
    void writesMinimalValidV5Meta() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Smbp5Logic.writeMeta(baos);
        // int metaVersion=5 (big-endian) + FINISH byte
        assertArrayEquals(new byte[]{0, 0, 0, 5, 1}, baos.toByteArray());
    }

    @Test
    void writesValidV0Logic() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Smbp5Logic.writeLogic(baos);
        // int structureVersion=0, int -(1024+2)=-1026 (0xFFFFFBFE), int 0 (empty map)
        assertArrayEquals(new byte[]{
            0, 0, 0, 0,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFB, (byte) 0xFE,
            0, 0, 0, 0
        }, baos.toByteArray());
    }

    private static Block blk(int id) {
        Block b = new Block();
        b.setBlockID((short) id);
        return b;
    }
}
