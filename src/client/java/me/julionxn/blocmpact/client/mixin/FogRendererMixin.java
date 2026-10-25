package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.impl.env.EnvironmentState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Final fog override, whatever the camera is in (air, water, lava, blindness...). */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void blocmpact$fog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                               CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data != null) EnvironmentState.applyFog(data);
    }
}
