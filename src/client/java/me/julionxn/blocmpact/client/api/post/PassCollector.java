package me.julionxn.blocmpact.client.api.post;

import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/** Receives the passes of every effect for this frame, in order. */
public interface PassCollector {
    void add(PostPass pass);

    default PostPass add(Identifier program, Consumer<Uniforms> setup) {
        PostPass p = PostPass.of(program, setup);
        add(p);
        return p;
    }
}
