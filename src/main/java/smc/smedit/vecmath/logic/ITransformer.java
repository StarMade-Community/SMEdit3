package smc.smedit.vecmath.logic;

import smc.smedit.vecmath.Matrix4f;

public interface ITransformer {

    public Matrix4f calcTransform(Matrix4f transform);
}
