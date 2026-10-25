package me.julionxn.blocmpact.api;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional registry of server logic per effect type. When an effect of a registered type is spawned without an
 * explicit {@link ServerEffect} (for example from {@code /blocmpact play}), the factory creates one.
 * Purely visual effects do not need to be registered here.
 */
public final class ServerEffects {
    private static final Map<Identifier, Factory> FACTORIES = new ConcurrentHashMap<>();

    private ServerEffects() {
    }

    @FunctionalInterface
    public interface Factory {
        /** Creates the server part. The effect's context (level, anchor, seed, params) is available from its first tick. */
        ServerEffect create(FxParams params);
    }

    public static void register(Identifier type, Factory factory) {
        FACTORIES.put(type, factory);
    }

    public static Factory get(Identifier type) {
        return FACTORIES.get(type);
    }
}
