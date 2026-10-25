package me.julionxn.blocmpact.client.api;

import com.google.gson.JsonObject;
import me.julionxn.blocmpact.api.Anchor;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.impl.EffectManager;
import me.julionxn.blocmpact.client.impl.data.DataEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Client entry point: register effect types and play effects locally.
 *
 * <pre>{@code
 * // ClientModInitializer
 * ClientEffects.register(Identifier.fromNamespaceAndPath("mymod", "shockwave"), ShockwaveFx::new);
 *
 * // anywhere on the client (no server round trip, only this player sees it)
 * ClientEffects.play(Identifier.fromNamespaceAndPath("mymod", "shockwave"), pos, FxParams.builder().put("radius", 8).build());
 * }</pre>
 *
 * Types without a Java factory are looked up among the data-driven effects
 * ({@code assets/<ns>/blocmpact/effects/<path>.json}).
 */
public final class ClientEffects {
    private ClientEffects() {
    }

    /** Registers the client side of an effect type. Call from your {@code ClientModInitializer}. */
    public static void register(Identifier type, EffectFactory factory) {
        EffectManager.INSTANCE.register(type, factory);
    }

    /**
     * Builds a factory from a data-driven definition (same schema as {@code assets/<ns>/blocmpact/effects/*.json})
     * that does not come from a resource pack: generated in code, read from a config file, received over the network...
     * The definition is parsed now and copied, so later changes to {@code json} do not affect it. F3+T does not
     * reload it (the shaders and textures it names are reloaded, since they are looked up by id).
     *
     * <pre>{@code
     * ClientEffects.register(id, ClientEffects.fromJson(id, json));   // a Java factory wins over a pack JSON with the same id
     * ClientEffects.play(EffectSpawn.local(id, pos, params), ClientEffects.fromJson(id, json));   // one-off, unregistered
     * }</pre>
     *
     * @param id names the definition in error messages ({@code mymod:x.timeline[2].do[0]...}); each spawn still runs
     *           as its own {@link EffectSpawn#type()}
     * @throws IllegalArgumentException if the definition is invalid; the message carries the JSON path
     */
    public static EffectFactory fromJson(Identifier id, JsonObject json) {
        return DataEffects.parse(id, json);
    }

    /** True if a Java factory or a JSON definition exists for {@code type}. */
    public static boolean isKnown(Identifier type) {
        return EffectManager.INSTANCE.isKnown(type);
    }

    /** All known types (Java and data). */
    public static Set<Identifier> types() {
        return EffectManager.INSTANCE.types();
    }

    public static ClientFxHandle play(Identifier type, Vec3 position) {
        return play(type, Anchor.at(position), FxParams.EMPTY);
    }

    public static ClientFxHandle play(Identifier type, Vec3 position, FxParams params) {
        return play(type, Anchor.at(position), params);
    }

    public static ClientFxHandle play(Identifier type, Anchor anchor, FxParams params) {
        return play(EffectSpawn.local(type, anchor, params));
    }

    /** Plays a spawn description locally (it gets a negative instance id). */
    public static ClientFxHandle play(EffectSpawn spawn) {
        return EffectManager.INSTANCE.playLocal(spawn, null);
    }

    /** Plays with an explicit factory, without registering a type. */
    public static ClientFxHandle play(EffectSpawn spawn, EffectFactory factory) {
        return EffectManager.INSTANCE.playLocal(spawn, factory);
    }

    public static Optional<ClientFxHandle> find(long id) {
        return Optional.ofNullable(EffectManager.INSTANCE.handle(id));
    }

    public static void stop(long id, boolean immediate) {
        EffectManager.INSTANCE.stop(id, immediate);
    }

    public static void stopAll(boolean immediate) {
        EffectManager.INSTANCE.stopAll(immediate);
    }

    public static void event(long id, String name, FxParams data) {
        EffectManager.INSTANCE.event(id, name, data);
    }

    /** Changes the timeline speed of a running effect on this client (0 pauses). See {@link ClientFxHandle#speed(float)}. */
    public static void speed(long id, float speed) {
        EffectManager.INSTANCE.speed(id, speed, Double.NaN);
    }

    /** Snapshot of the running effects, oldest first. */
    public static List<VisualEffect> active() {
        return EffectManager.INSTANCE.activeList();
    }
}
