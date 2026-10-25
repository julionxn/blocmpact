package me.julionxn.blocmpact.client.impl.post;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Snapshot of every piece of OpenGL state the engine touches. Minecraft's renderer caches GL state: anything left
 * changed breaks its next draw far away from our code. Capture before, restore in a {@code finally}.
 */
final class GlState {
    private static final int UNITS = 16;
    private int drawFbo, readFbo, program, vao, activeTex, unpack, pack, arrayBuf, uniformBuf;
    private final int[] tex = new int[UNITS], samplers = new int[UNITS], viewport = new int[4];
    private boolean depth, blend, cull, scissor, stencil, depthMask, framebufferSrgb;
    private final boolean[] colorMask = new boolean[4];

    static GlState capture() {
        GlState s = new GlState();
        s.drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        s.readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        s.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        s.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        s.activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        s.unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        s.pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        s.arrayBuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        s.uniformBuf = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        for (int i = 0; i < UNITS; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            s.tex[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            s.samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        }
        GL13.glActiveTexture(s.activeTex);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, s.viewport);
        s.depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        s.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        s.stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        s.framebufferSrgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        s.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try (MemoryStack st = MemoryStack.stackPush()) {
            ByteBuffer m = st.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, m);
            for (int i = 0; i < 4; i++) s.colorMask[i] = m.get(i) != 0;
        }
        return s;
    }

    /** State every pass expects. */
    static void prepareForPasses() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0); // otherwise glTexImage2D(null) reads from a PBO
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    void restore() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        for (int i = 0; i < UNITS; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
            GL33.glBindSampler(i, samplers[i]);
        }
        GL13.glActiveTexture(activeTex);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuf);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBuf);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        set(GL11.GL_DEPTH_TEST, depth);
        set(GL11.GL_BLEND, blend);
        set(GL11.GL_CULL_FACE, cull);
        set(GL11.GL_SCISSOR_TEST, scissor);
        set(GL11.GL_STENCIL_TEST, stencil);
        set(GL30.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
        GL11.glDepthMask(depthMask);
        GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
    }

    private static void set(int cap, boolean on) {
        if (on) GL11.glEnable(cap);
        else GL11.glDisable(cap);
    }
}
