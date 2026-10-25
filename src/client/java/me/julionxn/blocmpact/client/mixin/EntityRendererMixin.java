package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.impl.entity.EntityPoses;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Outlines and part poses requested by effects are stored in the entity's render state. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blocmpact$extract(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        EntityPoses.extract(entity, state);
    }
}
