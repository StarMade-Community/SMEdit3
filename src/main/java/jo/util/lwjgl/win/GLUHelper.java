package jo.util.lwjgl.win;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import jo.vecmath.Matrix4f;
import jo.vecmath.logic.Matrix4fLogic;
import org.lwjgl.opengl.GL11;

/**
 * Helper class providing GLU-style utility functions for LWJGL 3.
 * Replaces deprecated GLU functionality from LWJGL 2.
 */
public class GLUHelper {

    /**
     * Setup a perspective projection matrix similar to gluPerspective.
     * 
     * @param fovy Field of view in degrees
     * @param aspect Aspect ratio (width/height)
     * @param zNear Near clipping plane
     * @param zFar Far clipping plane
     */
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        float fH = (float) Math.tan(fovy / 360.0f * Math.PI) * zNear;
        float fW = fH * aspect;
        GL11.glFrustum(-fW, fW, -fH, fH, zNear, zFar);
    }

    /**
     * Project object coordinates to window coordinates.
     * 
     * @param objX Object X coordinate
     * @param objY Object Y coordinate
     * @param objZ Object Z coordinate
     * @param modelView Modelview matrix
     * @param projection Projection matrix
     * @param viewport Viewport parameters
     * @param winPos Output buffer for window coordinates (x, y, z)
     * @return true if successful
     */
    public static boolean gluProject(float objX, float objY, float objZ,
                                     FloatBuffer modelView, FloatBuffer projection,
                                     IntBuffer viewport, FloatBuffer winPos) {
        // Transform object coordinates to eye coordinates
        float[] in = {objX, objY, objZ, 1.0f};
        float[] out = new float[4];
        
        // Multiply by modelview matrix
        multMatrixVec(modelView, in, out);
        
        // Multiply by projection matrix
        float[] out2 = new float[4];
        multMatrixVec(projection, out, out2);
        
        if (out2[3] == 0.0f) {
            return false;
        }
        
        // Perspective division
        out2[0] /= out2[3];
        out2[1] /= out2[3];
        out2[2] /= out2[3];
        
        // Map to window coordinates
        winPos.put(0, viewport.get(0) + (1.0f + out2[0]) * viewport.get(2) / 2.0f);
        winPos.put(1, viewport.get(1) + (1.0f + out2[1]) * viewport.get(3) / 2.0f);
        winPos.put(2, (1.0f + out2[2]) / 2.0f);
        
        return true;
    }

    /**
     * Unproject window coordinates to object coordinates.
     * 
     * @param winX Window X coordinate
     * @param winY Window Y coordinate
     * @param winZ Window Z coordinate (depth)
     * @param modelView Modelview matrix
     * @param projection Projection matrix
     * @param viewport Viewport parameters
     * @param objPos Output buffer for object coordinates (x, y, z)
     * @return true if successful
     */
    public static boolean gluUnProject(float winX, float winY, float winZ,
                                       FloatBuffer modelView, FloatBuffer projection,
                                       IntBuffer viewport, FloatBuffer objPos) {
        // Compute combined matrix
        float[] finalMatrix = new float[16];
        multMatrices(modelView, projection, finalMatrix);
        
        if (!invertMatrix(finalMatrix, finalMatrix)) {
            return false;
        }
        
        // Transform to normalized coordinates [-1, 1]
        float[] in = new float[4];
        in[0] = (winX - viewport.get(0)) * 2.0f / viewport.get(2) - 1.0f;
        in[1] = (winY - viewport.get(1)) * 2.0f / viewport.get(3) - 1.0f;
        in[2] = 2.0f * winZ - 1.0f;
        in[3] = 1.0f;
        
        float[] out = new float[4];
        multMatrixVec(finalMatrix, in, out);
        
        if (out[3] == 0.0f) {
            return false;
        }
        
        out[3] = 1.0f / out[3];
        objPos.put(0, out[0] * out[3]);
        objPos.put(1, out[1] * out[3]);
        objPos.put(2, out[2] * out[3]);
        
        return true;
    }

    private static void multMatrixVec(FloatBuffer matrix, float[] in, float[] out) {
        for (int i = 0; i < 4; i++) {
            out[i] = in[0] * matrix.get(i) +
                     in[1] * matrix.get(4 + i) +
                     in[2] * matrix.get(8 + i) +
                     in[3] * matrix.get(12 + i);
        }
    }

    private static void multMatrixVec(float[] matrix, float[] in, float[] out) {
        for (int i = 0; i < 4; i++) {
            out[i] = in[0] * matrix[i] +
                     in[1] * matrix[4 + i] +
                     in[2] * matrix[8 + i] +
                     in[3] * matrix[12 + i];
        }
    }

    private static void multMatrices(FloatBuffer a, FloatBuffer b, float[] result) {
        for (int i = 0; i < 4; i++) {
            int iOffset = i * 4;
            for (int j = 0; j < 4; j++) {
                int idx = iOffset + j;
                result[idx] = 0.0f;
                for (int k = 0; k < 4; k++) {
                    result[idx] += a.get(iOffset + k) * b.get(k * 4 + j);
                }
            }
        }
    }

    private static boolean invertMatrix(float[] m, float[] invOut) {
        float[] inv = new float[16];
        
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
                 m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
                 m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
                 m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
                  m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
                 m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
                 m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
                 m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
                  m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
                 m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
                 m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
                  m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
                  m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
                 m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
                 m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
                  m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
                  m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        
        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        
        if (det == 0.0f) {
            return false;
        }
        
        det = 1.0f / det;
        
        for (int i = 0; i < 16; i++) {
            invOut[i] = inv[i] * det;
        }
        
        return true;
    }
}
