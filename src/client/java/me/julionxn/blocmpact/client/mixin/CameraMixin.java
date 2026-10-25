package me.julionxn.blocmpact.client.mixin;

import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.api.camera.CameraPose;
import me.julionxn.blocmpact.client.impl.FrameState;
import me.julionxn.blocmpact.client.impl.camera.CinematicDirector;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cutscene poses and camera offsets after vanilla placed the camera; FOV changes. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private boolean detached;
    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void blocmpact$pose(float partial, CallbackInfo ci) {
        CameraPose vanilla = new CameraPose(position(), yRot(), xRot(), Float.NaN, 0);
        CameraPose pose = CinematicDirector.INSTANCE.pose(vanilla, partial);
        if (pose != null) {
            setRotation(pose.yaw(), pose.pitch());
            setPosition(pose.position());
            if (entity != null && pose.position().distanceTo(entity.getEyePosition(partial)) > 0.9) detached = true;
        }
        CameraFx fx = FrameState.camera();
        if (fx.offsetX != 0 || fx.offsetY != 0 || fx.offsetZ != 0) setPosition(position().add(fx.offsetX, fx.offsetY, fx.offsetZ));
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void blocmpact$fov(float partial, CallbackInfoReturnable<Float> cir) {
        CameraFx fx = FrameState.camera();
        float fov = CinematicDirector.INSTANCE.fov(cir.getReturnValueF());
        fov = fov * fx.fovScale + fx.fovAdd;
        if (fov != cir.getReturnValueF()) cir.setReturnValue(Math.max(1f, Math.min(170f, fov)));
    }
}
