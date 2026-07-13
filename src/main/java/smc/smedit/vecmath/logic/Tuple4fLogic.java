package smc.smedit.vecmath.logic;

import java.nio.FloatBuffer;

import smc.smedit.logic.utils.BufferLogic;
import smc.smedit.vecmath.Tuple4f;

public class Tuple4fLogic extends MathUtils {

    public static float[] toFloatArray(Tuple4f v) {
        return new float[]{v.x, v.y, v.z, v.w};
    }

    public static FloatBuffer toFloatBuffer(Tuple4f v) {
        return BufferLogic.create(toFloatArray(v));
    }
}
