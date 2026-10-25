package me.julionxn.blocmpact.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.julionxn.blocmpact.client.impl.entity.EntityPoses;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies part poses right after vanilla animated any model, just before it is drawn. */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void blocmpact$pose(Model<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        EntityPoses.apply(model, state);
    }
}
