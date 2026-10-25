package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.impl.env.EnvironmentState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Visual rain and thunder strength on the client level only. */
@Mixin(Level.class)
abstract class LevelWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void blocmpact$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            float v = EnvironmentState.rain(cir.getReturnValueF());
            if (v != cir.getReturnValueF()) cir.setReturnValue(v);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void blocmpact$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            float v = EnvironmentState.thunder(cir.getReturnValueF());
            if (v != cir.getReturnValueF()) cir.setReturnValue(v);
        }
    }
}
