package me.julionxn.blocmpact.network;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Changes the timeline speed of a running effect (0 pauses). {@code at} is the server's clock position when the
 * change was made: clients move there before applying it, so the change lands on the same point of the timeline
 * everywhere whatever the network jitter.
 */
public record FxSpeedPayload(long instanceId, float speed, double at) implements CustomPacketPayload {
    public static final Type<FxSpeedPayload> TYPE = new Type<>(Blocmpact.id("speed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxSpeedPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeLong(p.instanceId);
            buf.writeFloat(p.speed);
            buf.writeDouble(p.at);
        },
        buf -> new FxSpeedPayload(buf.readLong(), buf.readFloat(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
