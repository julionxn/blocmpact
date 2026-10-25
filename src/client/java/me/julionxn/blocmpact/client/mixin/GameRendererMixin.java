package me.julionxn.blocmpact.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.impl.BlocmpactConfig;
import me.julionxn.blocmpact.client.impl.FrameState;
import me.julionxn.blocmpact.client.impl.camera.CinematicDirector;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame start, this frame's matrices for the HUD, the real projection, camera shake/roll on the view, no walking bob
 * in cutscenes, and hiding the hand during cutscenes.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    public abstract GameRenderState gameRenderState();

    @Shadow
    protected abstract void bobHurt(CameraRenderState camera, PoseStack pose);

    @Shadow
    protected abstract void bobView(CameraRenderState camera, PoseStack pose);

    @Inject(method = "update", at = @At("HEAD"))
    private void blocmpact$beginFrame(DeltaTracker delta, CallbackInfo ci) {
        FrameState.beginFrame(delta);
    }

    @Inject(method = "extract", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"))
    private void blocmpact$hudFrame(DeltaTracker delta, boolean render, CallbackInfo ci) {
        GameRenderState state = gameRenderState();
        PoseStack pose = new PoseStack();
        if (state.shouldRenderLevel) {
            // Same calls, same order as renderLevel.
            bobHurt(state.levelRenderState.cameraRenderState, pose);
            if (state.optionsRenderState.bobView) bobView(state.levelRenderState.cameraRenderState, pose);
        }
        FrameState.captureHud(state, pose.last().pose(), delta.getGameTimeDeltaPartialTick(false));
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private Matrix4f blocmpact$projection(Matrix4f projection) {
        FrameState.captureProjection(projection);
        return projection;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void blocmpact$shake(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        CameraFx fx = FrameState.camera();
        float roll = fx.roll + CinematicDirector.INSTANCE.roll();
        float shake = fx.shake * BlocmpactConfig.get().shakeScale;
        if (shake > 0) {
            float t = FrameState.shakeSeconds() * fx.shakeFrequency;
            pose.rotateDegrees(Axis.XP, ((FxMath.noise(11, t) - 0.5f) * 2 * shake));
            pose.rotateDegrees(Axis.YP, ((FxMath.noise(23, t) - 0.5f) * 2 * shake));
            roll += (FxMath.noise(37, t) - 0.5f) * shake;
        }
        if (fx.pitch != 0) pose.rotateDegrees(Axis.XP, (fx.pitch));
        if (fx.yaw != 0) pose.rotateDegrees(Axis.YP, (fx.yaw));
        if (roll != 0) pose.rotateDegrees(Axis.ZP, (roll));
    }

    /** A cutscene camera does not walk: fade the player's walking bob out with the cutscene's weight. */
    @ModifyExpressionValue(method = "bobView", at = @At(value = "FIELD", target =
        "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F"))
    private float blocmpact$cutsceneBob(float bob) {
        return bob * (1f - CinematicDirector.INSTANCE.weight());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void blocmpact$hideHand(CameraRenderState camera, PlayerRenderState player, GpuTextureView target, CallbackInfo ci) {
        if (CinematicDirector.INSTANCE.hideHand()) ci.cancel();
    }
}
