package me.julionxn.blocmpact.network;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Asks clients to stop an effect. {@code immediate = false} lets the effect play its outro
 * ({@code VisualEffect.onStopRequested}); {@code true} removes it right away. {@link #ALL} targets every effect.
 */
public record FxStopPayload(long instanceId, boolean immediate) implements CustomPacketPayload {
    public static final long ALL = 0L;
    public static final Type<FxStopPayload> TYPE = new Type<>(Blocmpact.id("stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxStopPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeLong(p.instanceId);
            buf.writeBoolean(p.immediate);
        },
        buf -> new FxStopPayload(buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
