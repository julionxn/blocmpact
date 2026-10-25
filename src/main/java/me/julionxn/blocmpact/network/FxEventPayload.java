package me.julionxn.blocmpact.network;

import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.api.FxParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A named event with data, delivered to a running effect (a shield being hit, a phase change, a new target...). */
public record FxEventPayload(long instanceId, String name, FxParams data) implements CustomPacketPayload {
    public static final Type<FxEventPayload> TYPE = new Type<>(Blocmpact.id("event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxEventPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeLong(p.instanceId);
            buf.writeUtf(p.name, 256);
            FxParams.STREAM_CODEC.encode(buf, p.data);
        },
        buf -> new FxEventPayload(buf.readLong(), buf.readUtf(256), FxParams.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
