package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.impl.env.CloudCutouts;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/** Holes in the cloud layer: skips the 12×12 cells that fall inside one. Optional if the methods change. */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Shadow
    private boolean needsRebuild;

    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), require = 0)
    private void blocmpact$prepare(int color, CloudStatus status, float height, int range, Vec3 camera, long gameTime, float partial,
                                   CallbackInfo ci) {
        if (CloudCutouts.prepare(camera.x, camera.z, gameTime, partial)) needsRebuild = true;
    }

    @Inject(method = "tryBuildCell", at = @At("HEAD"), cancellable = true, require = 0)
    private void blocmpact$hole(@Coerce Object relativePos, ByteBuffer buffer, int cellX, int cellZ, boolean fancy, int dx, int width,
                                int dz, int height, long[] cells, CallbackInfo ci) {
        if (CloudCutouts.skip(dx, dz, width, height)) ci.cancel();
    }
}
