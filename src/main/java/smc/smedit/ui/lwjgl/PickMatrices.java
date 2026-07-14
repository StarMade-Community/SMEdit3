package smc.smedit.ui.lwjgl;

import java.nio.FloatBuffer;

/**
 * Thread-safe handoff of the GL modelview/projection/viewport used to draw the
 * block group. The render thread {@link #capture}s them each frame; the EDT reads
 * an immutable {@link Snapshot} to unproject screen clicks into world-space rays
 * (see {@link RaycastPicker}) without needing a GL context.
 */
public final class PickMatrices {

    /** Immutable copy of one frame's matrices, published atomically. */
    public static final class Snapshot {
        public final float[] modelview; // column-major, 16
        public final float[] projection; // column-major, 16
        public final int[] viewport; // x, y, width, height

        Snapshot(float[] modelview, float[] projection, int[] viewport) {
            this.modelview = modelview;
            this.projection = projection;
            this.viewport = viewport;
        }
    }

    private volatile Snapshot mSnapshot;

    /** Called on the render thread with freshly-read GL matrix buffers. */
    public void capture(FloatBuffer modelview, FloatBuffer projection, FloatBuffer viewport) {
        float[] mv = new float[16];
        float[] proj = new float[16];
        int[] vp = new int[4];
        for (int i = 0; i < 16; i++) {
            mv[i] = modelview.get(i);
            proj[i] = projection.get(i);
        }
        for (int i = 0; i < 4; i++) {
            vp[i] = (int) viewport.get(i);
        }
        mSnapshot = new Snapshot(mv, proj, vp);
    }

    /** Latest snapshot, or {@code null} if nothing has been rendered yet. */
    public Snapshot snapshot() {
        return mSnapshot;
    }
}
