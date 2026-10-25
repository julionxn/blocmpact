package me.julionxn.blocmpact.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.julionxn.blocmpact.api.Effects;
import me.julionxn.blocmpact.api.FxHandle;
import me.julionxn.blocmpact.api.FxParams;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code /blocmpact}: play, stop and poke effects from chat, command blocks and datapacks.
 * <pre>
 * /blocmpact play &lt;type&gt; [pos] [params]
 * /blocmpact play &lt;type&gt; on &lt;entities&gt; [params]
 * /blocmpact stop &lt;id&gt; [immediate] | /blocmpact stop all [immediate]
 * /blocmpact event &lt;id&gt; &lt;name&gt; [params]
 * /blocmpact speed &lt;id&gt; &lt;speed&gt;            0 pauses, any other value resumes
 * /blocmpact list
 * </pre>
 * Params are SNBT: {@code {radius:12, color:"#FF8800", dir:[0.0, 1.0, 0.0]}}. Reserved keys: {@code _seed} (long),
 * {@code _speed}, {@code _range}, {@code _tracked} (ticks, or -1 until stopped).
 */
public final class BlocmpactCommands {
    private BlocmpactCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("blocmpact").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("play")
                .then(Commands.argument("type", IdentifierArgument.id())
                    .executes(c -> play(c, c.getSource().getPosition(), null))
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), null))
                        .then(Commands.argument("params", CompoundTagArgument.compoundTag())
                            .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), CompoundTagArgument.getCompoundTag(c, "params")))))
                    .then(Commands.literal("on")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(c -> playOn(c, EntityArgument.getEntities(c, "targets"), null))
                            .then(Commands.argument("params", CompoundTagArgument.compoundTag())
                                .executes(c -> playOn(c, EntityArgument.getEntities(c, "targets"), CompoundTagArgument.getCompoundTag(c, "params"))))))))
            .then(Commands.literal("stop")
                .then(Commands.literal("all")
                    .executes(c -> stopAll(c, false))
                    .then(Commands.argument("immediate", BoolArgumentType.bool())
                        .executes(c -> stopAll(c, BoolArgumentType.getBool(c, "immediate")))))
                .then(Commands.argument("id", LongArgumentType.longArg(1))
                    .executes(c -> stop(c, false))
                    .then(Commands.argument("immediate", BoolArgumentType.bool())
                        .executes(c -> stop(c, BoolArgumentType.getBool(c, "immediate"))))))
            .then(Commands.literal("event")
                .then(Commands.argument("id", LongArgumentType.longArg(1))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> event(c, null))
                        .then(Commands.argument("params", CompoundTagArgument.compoundTag())
                            .executes(c -> event(c, CompoundTagArgument.getCompoundTag(c, "params")))))))
            .then(Commands.literal("speed")
                .then(Commands.argument("id", LongArgumentType.longArg(1))
                    .then(Commands.argument("speed", FloatArgumentType.floatArg(0f))
                        .executes(BlocmpactCommands::speed))))
            .then(Commands.literal("list").executes(BlocmpactCommands::list)));
    }

    private static int play(CommandContext<CommandSourceStack> c, Vec3 pos, CompoundTag nbt) {
        Effects.Spawn spawn = Effects.spawn(c.getSource().getLevel(), IdentifierArgument.getId(c, "type")).at(pos);
        FxHandle h = applyOptions(spawn, nbt).send();
        c.getSource().sendSuccess(() -> Component.literal("Effect #" + h.id() + " started"), false);
        return (int) h.id();
    }

    private static int playOn(CommandContext<CommandSourceStack> c, Collection<? extends Entity> targets, CompoundTag nbt) {
        Identifier type = IdentifierArgument.getId(c, "type");
        int n = 0;
        long last = 0;
        for (Entity e : targets) {
            Effects.Spawn spawn = Effects.spawn(c.getSource().getLevel(), type).on(e);
            last = applyOptions(spawn, nbt).send().id();
            n++;
        }
        int count = n;
        long lastId = last;
        c.getSource().sendSuccess(() -> Component.literal(count == 1 ? "Effect #" + lastId + " started" : count + " effects started"), false);
        return n;
    }

    private static Effects.Spawn applyOptions(Effects.Spawn spawn, CompoundTag nbt) {
        if (nbt == null) return spawn;
        FxParams all = FxParams.fromNbt(nbt);
        FxParams.Builder params = all.toBuilder();
        for (String reserved : List.of("_seed", "_speed", "_range", "_tracked")) params.remove(reserved);
        if (all.has("_seed")) spawn.seed(all.getLong("_seed", 0));
        if (all.has("_speed")) spawn.speed(all.getFloat("_speed", 1f));
        if (all.has("_range")) spawn.range(all.getDouble("_range", 512));
        if (all.has("_tracked")) spawn.tracked(all.getInt("_tracked", -1));
        return spawn.params(params.build());
    }

    private static int stop(CommandContext<CommandSourceStack> c, boolean immediate) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(c, "id");
        Optional<FxHandle> h = Effects.find(id);
        if (h.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No running effect #" + id));
            return 0;
        }
        h.get().stop(immediate);
        c.getSource().sendSuccess(() -> Component.literal("Effect #" + id + " stopped"), false);
        return 1;
    }

    private static int stopAll(CommandContext<CommandSourceStack> c, boolean immediate) {
        Effects.stopAll(c.getSource().getServer(), immediate);
        c.getSource().sendSuccess(() -> Component.literal("All effects stopped"), false);
        return 1;
    }

    private static int event(CommandContext<CommandSourceStack> c, CompoundTag nbt) {
        long id = LongArgumentType.getLong(c, "id");
        String name = StringArgumentType.getString(c, "name");
        Optional<FxHandle> h = Effects.find(id);
        if (h.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No running effect #" + id));
            return 0;
        }
        h.get().event(name, nbt == null ? FxParams.EMPTY : FxParams.fromNbt(nbt));
        c.getSource().sendSuccess(() -> Component.literal("Event '" + name + "' sent to #" + id), false);
        return 1;
    }

    private static int speed(CommandContext<CommandSourceStack> c) {
        long id = LongArgumentType.getLong(c, "id");
        float speed = FloatArgumentType.getFloat(c, "speed");
        Optional<FxHandle> h = Effects.find(id);
        if (h.isEmpty() || !h.get().alive()) {
            c.getSource().sendFailure(Component.literal("No running effect #" + id));
            return 0;
        }
        h.get().speed(speed);
        c.getSource().sendSuccess(() -> Component.literal(speed == 0 ? "Effect #" + id + " paused" : "Effect #" + id + " at speed " + speed), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<Long> ids = Effects.running();
        c.getSource().sendSuccess(() -> Component.literal(ids.isEmpty() ? "No tracked effects" : "Tracked effects: " + ids), false);
        return ids.size();
    }
}
