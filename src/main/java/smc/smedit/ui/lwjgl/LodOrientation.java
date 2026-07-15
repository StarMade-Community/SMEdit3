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
package smc.smedit.ui.lwjgl;

/**
 * Per-orientation rotation for StarMade LOD block models, ported verbatim from
 * StarMade's {@code Oriencube} shape algorithms.
 *
 * <p>StarMade always draws a LOD mesh through {@code BlockShapeAlgorithm.getAlgo(6,
 * orientation)} — i.e. the block's {@code orientation} byte (mod 24) selects one
 * of the 24 "orientcube" algorithms, whose transform is applied to the mesh
 * before placement. That transform is {@code primary · secondary} where:
 * <ul>
 * <li><b>primary</b> is a fixed rotation per primary face (the group of 4):
 * FRONT={@code rotX(+90°)}, BACK={@code rotX(-90°)}, BOTTOM={@code rotZ(-180°)},
 * TOP=identity, RIGHT={@code rotZ(+90°)}, LEFT={@code rotZ(-90°)};</li>
 * <li><b>secondary</b> is {@code rotY(k·90°)} — the spin around the primary axis,
 * per the concrete orientcube.</li>
 * </ul>
 * The 24 array positions (= the orientation byte) and their secondary angles are
 * taken directly from {@code BlockShapeAlgorithm}'s orientcube array. Rotation
 * matrices follow {@code javax.vecmath.Matrix3f} conventions (v' = R·v).
 *
 * <p>Note orientation 14 (TopFront) is identity, and orientation 0 (FrontBottom)
 * is {@code rotX(90°)} — not identity — so an un-rotated model must still be
 * transformed by its stored orientation to match the game.
 */
public final class LodOrientation {

    private LodOrientation() {
    }

    /** Primary face per orientation group of 4 (array order). */
    private static final int FRONT = 0, BACK = 1, BOTTOM = 2, TOP = 3, RIGHT = 4, LEFT = 5;
    private static final int[] GROUP_PRIMARY = {FRONT, BACK, BOTTOM, TOP, RIGHT, LEFT};

    /**
     * Secondary rotY angle (in 90° steps: 0/1/2/3) per orientation 0..23, in the
     * exact array order StarMade registers the orientcubes.
     */
    private static final int[] SECONDARY_STEP = {
        0, 1, 2, 3, // Front:  Bottom, Left, Top, Right
        2, 1, 0, 3, // Back:   Bottom, Left, Top, Right
        2, 3, 0, 1, // Bottom: Back, Left, Front, Right
        2, 1, 0, 3, // Top:    Back, Left, Front, Right
        0, 1, 2, 3, // Right:  Front, Top, Back, Bottom
        0, 3, 2, 1, // Left:   Front, Top, Back, Bottom
    };

    /** Precomputed 3x3 row-major rotation matrix (9 floats) per orientation 0..23. */
    private static final float[][] ROT = new float[24][];

    static {
        for (int o = 0; o < 24; o++) {
            float[] primary = primaryMatrix(GROUP_PRIMARY[o / 4]);
            float[] secondary = rotY(SECONDARY_STEP[o] * (float) (Math.PI / 2.0));
            ROT[o] = mul(primary, secondary);
        }
    }

    /** Rotates {@code (x,y,z)} by the given block orientation into {@code out} (length &ge; 3). */
    public static void rotate(int orientation, float x, float y, float z, float[] out) {
        float[] m = ROT[Math.floorMod(orientation, 24)];
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    private static float[] primaryMatrix(int primary) {
        switch (primary) {
            case FRONT: return rotX((float) (Math.PI / 2.0));
            case BACK: return rotX((float) (-Math.PI / 2.0));
            case BOTTOM: return rotZ((float) -Math.PI);
            case RIGHT: return rotZ((float) (Math.PI / 2.0));
            case LEFT: return rotZ((float) (-Math.PI / 2.0));
            default: return identity(); // TOP
        }
    }

    private static float[] identity() {
        return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    private static float[] rotX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static float[] rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static float[] rotZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
    }

    /** 3x3 row-major matrix product a·b. */
    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }
}
