package me.julionxn.blocmpact.client.api.post;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A full-screen shader pass. An effect does not draw: it describes passes and the engine runs them.
 * <p>
 * The program {@code ns:path} is the fragment shader {@code assets/ns/fx/path.fsh}; Blocmpact prepends
 * {@code #version 150} and its core library ({@code blocmpact:lib/core.glsl}) and expands {@code #include}s.
 *
 * <h3>Stages</h3>
 * {@link Stage#SCENE} passes (default) chain in ping-pong over the scene at the dynamic effect resolution: each one
 * reads the previous result in {@code DiffuseSampler}. Their light feeds the bloom. {@link Stage#OVERLAY} passes
 * run after the compositor at full resolution, for things that must not bloom or be graded.
 *
 * <h3>Named targets</h3>
 * {@link #to(String)} renders into a named texture instead of the chain (masks, blur buffers, data);
 * other passes read it with {@link Uniforms#target}. {@link #toPersistent(String)} keeps the texture between frames
 * and double-buffers it, so a pass can read last frame's content while writing the new one (trails, feedback,
 * accumulation, simulations).
 */
public final class PostPass {
    public enum Stage {SCENE, OVERLAY}

    private final Identifier program;
    private final Consumer<Uniforms> setup;
    private Stage stage = Stage.SCENE;
    private String output;
    private boolean persistent, hdr = true;
    private float scale = 1f;
    private Map<String, String> defines = Map.of();

    private PostPass(Identifier program, Consumer<Uniforms> setup) {
        this.program = program;
        this.setup = setup == null ? u -> {
        } : setup;
    }

    public static PostPass of(Identifier program, Consumer<Uniforms> setup) {
        return new PostPass(program, setup);
    }

    public static PostPass of(Identifier program) {
        return new PostPass(program, null);
    }

    /** Runs after the compositor at full resolution. */
    public PostPass overlay() {
        this.stage = Stage.OVERLAY;
        return this;
    }

    /** Renders into the named target (valid only during this frame) instead of the chain. */
    public PostPass to(String target) {
        this.output = target;
        this.persistent = false;
        return this;
    }

    /** Renders into a named target that survives between frames (double-buffered: read the old, write the new). */
    public PostPass toPersistent(String target) {
        this.output = target;
        this.persistent = true;
        return this;
    }

    /** Named targets are RGBA16F by default; this makes the target RGBA8 (half the memory). */
    public PostPass lowPrecision() {
        this.hdr = false;
        return this;
    }

    /** Resolution of the named target relative to the effect resolution (0.5 for a cheap blur buffer). */
    public PostPass scale(float factor) {
        this.scale = Math.max(0.03125f, Math.min(1f, factor));
        return this;
    }

    /** Adds {@code #define NAME value} at the top of the program (each set of defines is compiled once). */
    public PostPass define(String name, Object value) {
        if (defines.isEmpty()) defines = new TreeMap<>();
        defines.put(name, String.valueOf(value));
        return this;
    }

    public PostPass define(String name) {
        return define(name, "1");
    }

    public Identifier program() {
        return program;
    }

    public Consumer<Uniforms> setup() {
        return setup;
    }

    public Stage stage() {
        return stage;
    }

    public String output() {
        return output;
    }

    public boolean persistent() {
        return persistent;
    }

    public boolean hdr() {
        return hdr;
    }

    public float scale() {
        return scale;
    }

    public Map<String, String> defines() {
        return Collections.unmodifiableMap(defines);
    }
}
