package me.julionxn.blocmpact.network;

import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.api.Anchor;
import me.julionxn.blocmpact.api.FxParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The only packet an effect needs: what to play, where, with which seed, speed and parameters.
 * {@code startTick} lets late joiners enter an effect that is already running without replaying its past.
 */
public record FxSpawnPayload(long instanceId, Identifier effectType, Anchor anchor, long seed, float speed, int startTick,
                             FxParams params) implements CustomPacketPayload {
    public static final Type<FxSpawnPayload> TYPE = new Type<>(Blocmpact.id("spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxSpawnPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeLong(p.instanceId);
            buf.writeIdentifier(p.effectType);
            Anchor.STREAM_CODEC.encode(buf, p.anchor);
            buf.writeLong(p.seed);
            buf.writeFloat(p.speed);
            buf.writeVarInt(p.startTick);
            FxParams.STREAM_CODEC.encode(buf, p.params);
        },
        buf -> new FxSpawnPayload(buf.readLong(), buf.readIdentifier(), Anchor.STREAM_CODEC.decode(buf), buf.readLong(),
            buf.readFloat(), buf.readVarInt(), FxParams.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
