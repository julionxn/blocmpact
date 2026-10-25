package me.julionxn.blocmpact.client.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.ClientEffects;
import me.julionxn.blocmpact.client.api.ClientFxHandle;
import me.julionxn.blocmpact.client.api.EffectSpawn;
import me.julionxn.blocmpact.client.api.camera.Cinematics;
import me.julionxn.blocmpact.api.Anchor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /blocmpactc}: local testing without server permissions (only you see the effects).
 * <pre>
 * /blocmpactc play &lt;type&gt; [params]       at the block you look at (64 blocks) or your feet
 * /blocmpactc play_on_me &lt;type&gt; [params] following you
 * /blocmpactc stop                        stops every effect on this client
 * /blocmpactc list                        known types (Java and JSON)
 * /blocmpactc debug                       toggles the debug overlay
 * /blocmpactc speed &lt;type&gt; &lt;speed&gt;     plays at another timeline speed
 * /blocmpactc skip                        skips the current cutscene
 * </pre>
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("blocmpactc")
            .then(literal("play").then(argument("type", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(ClientEffects.types(), b))
                .executes(c -> play(c, null, false, 1f))
                .then(argument("params", CompoundTagArgument.compoundTag())
                    .executes(c -> play(c, CompoundTagArgument.getCompoundTag(c, "params"), false, 1f)))))
            .then(literal("play_on_me").then(argument("type", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(ClientEffects.types(), b))
                .executes(c -> play(c, null, true, 1f))
                .then(argument("params", CompoundTagArgument.compoundTag())
                    .executes(c -> play(c, CompoundTagArgument.getCompoundTag(c, "params"), true, 1f)))))
            .then(literal("speed").then(argument("type", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(ClientEffects.types(), b))
                .then(argument("speed", FloatArgumentType.floatArg(0.05f, 8f))
                    .executes(c -> play(c, null, false, FloatArgumentType.getFloat(c, "speed"))))))
            .then(literal("stop").executes(c -> {
                ClientEffects.stopAll(false);
                c.getSource().sendFeedback(Component.literal("Stopped every effect"));
                return 1;
            }))
            .then(literal("list").executes(c -> {
                c.getSource().sendFeedback(Component.literal("Effect types: " + ClientEffects.types()));
                return 1;
            }))
            .then(literal("skip").executes(c -> {
                Cinematics.skip();
                return 1;
            }))
            .then(literal("debug").executes(c -> {
                BlocmpactConfig.get().debugOverlay = !BlocmpactConfig.get().debugOverlay;
                BlocmpactConfig.save();
                c.getSource().sendFeedback(Component.literal("Debug overlay " + (BlocmpactConfig.get().debugOverlay ? "on" : "off")));
                return 1;
            })));
    }

    private static int play(CommandContext<FabricClientCommandSource> c, CompoundTag nbt, boolean onMe, float speed) {
        Identifier type = c.getArgument("type", Identifier.class);
        Player player = c.getSource().getPlayer();
        FxParams params = nbt == null ? FxParams.EMPTY : FxParams.fromNbt(nbt);
        Anchor anchor;
        if (onMe) anchor = Anchor.on(player);
        else {
            HitResult hit = player.pick(64, 1f, false);
            Vec3 pos = hit.getType() == HitResult.Type.MISS ? player.position() : hit.getLocation();
            anchor = Anchor.at(pos);
        }
        ClientFxHandle h = ClientEffects.play(EffectSpawn.local(type, anchor, params).withSpeed(speed));
        c.getSource().sendFeedback(Component.literal(h.alive() ? "Playing " + type + " (#" + h.id() + ")" : "Could not play " + type));
        return h.alive() ? 1 : 0;
    }
}
