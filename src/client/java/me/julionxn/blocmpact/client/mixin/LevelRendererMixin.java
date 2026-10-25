package me.julionxn.blocmpact.client.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import me.julionxn.blocmpact.client.impl.FrameState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the view at the start of the level render and runs the post chain at its end (before hand and GUI). */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void blocmpact$view(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera, GpuBufferSlice fog,
                                Vector4f fogColor, boolean sky, boolean consistentDepth, CallbackInfo ci) {
        FrameState.captureView(camera);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void blocmpact$post(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera, GpuBufferSlice fog,
                                Vector4f fogColor, boolean sky, boolean consistentDepth, CallbackInfo ci) {
        FrameState.renderPost();
    }
}
