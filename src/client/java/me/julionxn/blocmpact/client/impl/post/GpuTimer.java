package me.julionxn.blocmpact.client.impl.post;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Measures the GPU time of the effect chain with timestamp queries read a few frames later (no {@code glFinish}),
 * and turns it into a resolution factor in steps of 1/16 so textures are not recreated every frame.
 */
final class GpuTimer {
    private final int[] queries = new int[8];
    private final boolean[] pending = new boolean[4];
    private int slot = -1, frame;
    private float factor = 1f, lastMs;

    float factor() {
        return factor;
    }

    float lastMs() {
        return lastMs;
    }

    void begin() {
        if (queries[0] == 0) GL15.glGenQueries(queries);
        int s = frame & 3;
        slot = pending[s] ? -1 : s;
        if (slot >= 0) GL33.glQueryCounter(queries[s * 2], GL33.GL_TIMESTAMP);
    }

    void end() {
        if (slot >= 0) {
            GL33.glQueryCounter(queries[slot * 2 + 1], GL33.GL_TIMESTAMP);
            pending[slot] = true;
        }
        frame++;
    }

    void poll(float budgetMs) {
        if (queries[0] == 0) return;
        for (int s = 0; s < 4; s++) {
            if (!pending[s] || GL15.glGetQueryObjecti(queries[s * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) continue;
            long t0 = GL33.glGetQueryObjecti64(queries[s * 2], GL15.GL_QUERY_RESULT);
            long t1 = GL33.glGetQueryObjecti64(queries[s * 2 + 1], GL15.GL_QUERY_RESULT);
            pending[s] = false;
            float ms = (t1 - t0) / 1e6f;
            lastMs = ms;
            if (ms > budgetMs) factor = (float) Math.floor(Math.max(0.25f, factor * budgetMs / ms) * 16) / 16;
            else if (ms < budgetMs * 0.75f && factor < 1f) factor = (float) Math.ceil((factor + (1 - factor) * 0.33f) * 16) / 16;
            factor = Math.max(0.25f, Math.min(1f, factor));
        }
    }

    void delete() {
        if (queries[0] != 0) GL15.glDeleteQueries(queries);
        java.util.Arrays.fill(queries, 0);
        java.util.Arrays.fill(pending, false);
    }
}
