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
package smc.smedit.ship.logic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.data.BlockTypes;
import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.IOLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.ControllerEntry;
import smc.smedit.ship.data.GroupEntry;
import smc.smedit.ship.data.Logic;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.Point3s;


public class LogicLogic {
    private static final Logger log = Logger.getLogger(LogicLogic.class.getName());

    public static Logic readFile(InputStream is, boolean close) throws IOException {
        DataInputStream dis;
        if (is instanceof DataInputStream) {
            dis = (DataInputStream) is;
        } else {
            dis = new DataInputStream(is);
        }
        Logic logic = new Logic();
        // structureVersion (0). In the legacy format this was labelled "unknown1".
        logic.setUnknown1(dis.readInt());
        // Modern StarMade writes an on-disk marker here: -(1024 + serializationVersion).
        // The real controller count then follows. Legacy files have no marker, so a
        // non-negative value IS the count. (See StarMade ControlElementMap#serializeForDisk.)
        int markerOrCount = dis.readInt();
        int numControllers;
        if (markerOrCount < 0) {
            logic.setControlMapMarker(markerOrCount);
            numControllers = dis.readInt();
        } else {
            logic.setControlMapMarker(0); // legacy: no marker present
            numControllers = markerOrCount;
        }
        for (int i = 0; i < numControllers; i++) {
            ControllerEntry controller = new ControllerEntry();
            controller.setPosition(IOLogic.readPoint3s(dis));
            int numGroups = dis.readInt();
            for (int j = 0; j < numGroups; j++) {
                GroupEntry group = new GroupEntry();
                group.setBlockID(dis.readShort());
                int numBlocks = dis.readInt();
                for (int k = 0; k < numBlocks; k++) {
                    group.getBlocks().add(IOLogic.readPoint3s(dis));
                }
                controller.getGroups().add(group);
            }
            logic.getControllers().add(controller);
        }
        if (close) {
            dis.close();
        }
        return logic;
    }

    public static void writeFile(Logic logic, OutputStream os, boolean close) throws IOException {
        DataOutputStream dos;
        if (os instanceof DataOutputStream) {
            dos = (DataOutputStream) os;
        } else {
            dos = new DataOutputStream(os);
        }
        dos.writeInt(logic.getUnknown1());
        // Emit the modern on-disk marker when present, so this is a faithful
        // inverse of readFile (a byte-exact round-trip for modern files). A
        // marker of 0 means the source was legacy/markerless.
        if (logic.getControlMapMarker() != 0) {
            dos.writeInt(logic.getControlMapMarker());
        }
        dos.writeInt(logic.getControllers().size());
        for (ControllerEntry controller : logic.getControllers()) {
            IOLogic.writePoint3s(dos, controller.getPosition());
            dos.writeInt(controller.getGroups().size());
            for (GroupEntry group : controller.getGroups()) {
                dos.writeShort(group.getBlockID());
                dos.writeInt(group.getBlocks().size());
                for (Point3s block : group.getBlocks()) {
                    IOLogic.writePoint3s(dos, block);
                }
            }
        }
        if (close) {
            dos.close();
        }
    }

    public static Logic make(SparseMatrix<Block> grid) {
        Logic logic = new Logic();
        logic.setUnknown1(0);
        Point3i coreLocation = ShipLogic.findCore(grid);
        if (coreLocation == null) {
            throw new IllegalArgumentException("No core!");
        }
        ControllerEntry coreController = new ControllerEntry();
        coreController.setPosition(new Point3s((short) coreLocation.x, (short) coreLocation.y, (short) coreLocation.z));
        logic.getControllers().add(coreController);
        // controlled blocks
        List<Point3i> controllerBlocks = findAllControllerBlocks(grid);
        for (Point3i controllerBlockPosition : controllerBlocks) {
            Block controllerBlock = grid.get(controllerBlockPosition);
            GroupEntry controllerGroup = new GroupEntry();
            controllerGroup.setBlockID(controllerBlock.getBlockID());
            coreController.getGroups().add(controllerGroup);
            controllerGroup.getBlocks().add(new Point3s(controllerBlockPosition));

            ControllerEntry controlledEntry = new ControllerEntry();
            logic.getControllers().add(controlledEntry);
            controlledEntry.setPosition(new Point3s(controllerBlockPosition));
            GroupEntry controlledGroup = new GroupEntry();
            controlledEntry.getGroups().add(controlledGroup);
            short controlledBlockID = BlockTypes.CONTROLLER_IDS.get(controllerBlock.getBlockID());
            controlledGroup.setBlockID(controlledBlockID);
            List<Point3i> controlledBlocks = ShipLogic.findBlocks(grid, controlledBlockID);
            for (Point3i block : controlledBlocks) {
                controlledGroup.getBlocks().add(new Point3s(block));
            }
        }
        // uncontrolled blocks
        for (short controlledBlockID : new short[]{BlockTypes.THRUSTER_ID}) {
            List<Point3i> controlledBlocks = ShipLogic.findBlocks(grid, controlledBlockID);
            if (controlledBlocks.isEmpty()) {
                continue;
            }
            GroupEntry controlledGroup = new GroupEntry();
            coreController.getGroups().add(controlledGroup);
            controlledGroup.setBlockID(controlledBlockID);
            for (Point3i p : controlledBlocks) {
                controlledGroup.getBlocks().add(new Point3s(p));
            }
        }
        return logic;
    }

    private static List<Point3i> findAllControllerBlocks(SparseMatrix<Block> grid) {
        List<Point3i> blocks = new ArrayList<>();
        for (Iterator<Point3i> i = grid.iteratorNonNull(); i.hasNext();) {
            Point3i pp = i.next();
            Block b = grid.get(pp);
            if (BlockTypes.isController(b.getBlockID())) {
                blocks.add(pp);
            }
        }
        return blocks;
    }

    public static void dump(Logic logic, SparseMatrix<Block> grid) {
        log.log(Level.INFO, "Logic, unknown=" + logic.getUnknown1() + ", #controllers=" + logic.getControllers().size());
        //System.out.println("Logic, unknown=" + logic.getUnknown1() + ", #controllers=" + logic.getControllers().size());
        for (ControllerEntry controller : logic.getControllers()) {
            Block controllerBlock = grid.get(controller.getPosition());
            log.log(Level.INFO, "  Controller at " + controller.getPosition() + " (" + BlockTypes.BLOCK_NAMES.get(controllerBlock.getBlockID()) + "), #groups=" + controller.getGroups().size());
            //System.out.println("  Controller at " + controller.getPosition() + " (" + BlockTypes.BLOCK_NAMES.get(controllerBlock.getBlockID()) + "), #groups=" + controller.getGroups().size());
            for (GroupEntry group : controller.getGroups()) {
                log.log(Level.INFO, "    Group of " + group.getBlocks().size() + " " + BlockTypes.BLOCK_NAMES.get(group.getBlockID()));
                //System.out.println("    Group of " + group.getBlocks().size() + " " + BlockTypes.BLOCK_NAMES.get(group.getBlockID()));
//                for (Point3s block : group.getBlocks())
//                {
//                    Block groupBlock = grid.get(block);
//                    System.out.println("      "+block+" ("+BlockTypes.BLOCK_NAMES.get(groupBlock.getBlockID())+")");
//                }
            }
        }
    }
}
