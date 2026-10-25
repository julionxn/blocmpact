package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.impl.env.EnvironmentState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every visual attribute the client reads (fog, sky, clouds, light, sun/moon/stars) goes through the camera's probe. */
@Mixin(EnvironmentAttributeProbe.class)
abstract class EnvironmentAttributeProbeMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private <V> void blocmpact$override(EnvironmentAttribute<V> attribute, float partial, CallbackInfoReturnable<V> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || !mc.isSameThread() || (Object) this != mc.gameRenderer.mainCamera().attributeProbe()) return;
        V vanilla = cir.getReturnValue();
        V value = EnvironmentState.apply(attribute, vanilla);
        if (value != vanilla) cir.setReturnValue(value);
    }
}
