package me.julionxn.blocmpact.client;

import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.geometry.FxRenderTypes;
import me.julionxn.blocmpact.client.api.geometry.GeometryContext;
import me.julionxn.blocmpact.client.impl.BlocmpactConfig;
import me.julionxn.blocmpact.client.impl.ClientCommands;
import me.julionxn.blocmpact.client.impl.EffectManager;
import me.julionxn.blocmpact.client.impl.FrameState;
import me.julionxn.blocmpact.client.impl.HudLayer;
import me.julionxn.blocmpact.client.impl.camera.CinematicDirector;
import me.julionxn.blocmpact.client.impl.data.DataEffects;
import me.julionxn.blocmpact.client.impl.particle.FxParticle;
import me.julionxn.blocmpact.network.FxEventPayload;
import me.julionxn.blocmpact.network.FxSpawnPayload;
import me.julionxn.blocmpact.network.FxSpeedPayload;
import me.julionxn.blocmpact.network.FxStopPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

/**
 * Client entrypoint: receives effects, ticks them, and wires every layer into Minecraft's frame
 * (environment and camera at frame start, geometry during the level render, post-processing at its end, HUD last).
 */
public class BlocmpactClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlocmpactConfig.load();
        FxRenderTypes.bootstrap();
        FxParticle.bootstrap();
        FxParticle.registerProviders();

        ClientPlayNetworking.registerGlobalReceiver(FxSpawnPayload.TYPE, (p, ctx) -> EffectManager.INSTANCE.onSpawnPayload(p));
        ClientPlayNetworking.registerGlobalReceiver(FxStopPayload.TYPE, (p, ctx) -> EffectManager.INSTANCE.onStopPayload(p));
        ClientPlayNetworking.registerGlobalReceiver(FxEventPayload.TYPE, (p, ctx) -> EffectManager.INSTANCE.onEventPayload(p));
        ClientPlayNetworking.registerGlobalReceiver(FxSpeedPayload.TYPE, (p, ctx) -> EffectManager.INSTANCE.onSpeedPayload(p));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            EffectManager.INSTANCE.clear();
            CinematicDirector.INSTANCE.stop();
        }));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            EffectManager.INSTANCE.tick(mc);
            CinematicDirector.INSTANCE.tick(mc);
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            Frame frame = FrameState.renderFrame();
            if (frame == null) return;
            GeometryContext g = new GeometryContext(frame, ctx.poseStack(), ctx.submitNodeCollector());
            EffectManager.INSTANCE.forEachInRange(frame, "geometry", fx -> fx.geometry(g));
        });

        HudLayer.register(Blocmpact.id("effects"));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(DataEffects.ID, DataEffects.INSTANCE);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> ClientCommands.register(dispatcher));
    }
}
