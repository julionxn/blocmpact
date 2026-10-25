package me.julionxn.blocmpact;

import me.julionxn.blocmpact.api.Effects;
import me.julionxn.blocmpact.api.particle.FxParticles;
import me.julionxn.blocmpact.impl.BlocmpactCommands;
import me.julionxn.blocmpact.network.FxEventPayload;
import me.julionxn.blocmpact.network.FxSpawnPayload;
import me.julionxn.blocmpact.network.FxSpeedPayload;
import me.julionxn.blocmpact.network.FxStopPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: network payloads, the configurable particle type, server tracking and {@code /blocmpact}. */
public class Blocmpact implements ModInitializer {
    public static final String MOD_ID = "blocmpact";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(FxSpawnPayload.TYPE, FxSpawnPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FxStopPayload.TYPE, FxStopPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FxEventPayload.TYPE, FxEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FxSpeedPayload.TYPE, FxSpeedPayload.CODEC);
        FxParticles.bootstrap();
        Effects.bootstrap();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BlocmpactCommands.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
