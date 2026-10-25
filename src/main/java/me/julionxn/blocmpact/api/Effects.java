package me.julionxn.blocmpact.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Server entry point: start an effect for the players around it.
 *
 * <pre>{@code
 * FxHandle h = Effects.spawn(level, Identifier.fromNamespaceAndPath("mymod", "shockwave"))
 *     .at(hit.getLocation())
 *     .param("radius", 12)
 *     .param("color", "#FFAA33")
 *     .send();
 * }</pre>
 *
 * Only the type, anchor, seed, speed and parameters cross the network; every client derives the rest.
 */
public final class Effects {
    private Effects() {
    }

    public static Spawn spawn(ServerLevel level, Identifier type) {
        return new Spawn(level, type);
    }

    /** A handle to a running server effect, if it is still tracked. */
    public static Optional<FxHandle> find(long instanceId) {
        return Optional.ofNullable(EffectTracker.INSTANCE.find(instanceId));
    }

    /** Stops every effect started from this server. */
    public static void stopAll(MinecraftServer server, boolean immediate) {
        EffectTracker.INSTANCE.stopAll(server, immediate);
    }

    /** Ids of the effects the server is tracking. */
    public static List<Long> running() {
        return EffectTracker.INSTANCE.ids();
    }

    @ApiStatus.Internal
    public static void bootstrap() {
        EffectTracker.INSTANCE.bootstrap();
    }

    public static final class Spawn {
        final ServerLevel level;
        final Identifier type;
        Anchor anchor;
        long seed;
        float speed = 1f;
        double range = 512;
        final FxParams.Builder params = FxParams.builder();
        List<ServerPlayer> recipients;
        boolean tracked;
        int lifetime = -1;
        ServerEffect server;
        boolean registeredServer = true;

        private Spawn(ServerLevel level, Identifier type) {
            this.level = level;
            this.type = type;
            this.seed = level.getRandom().nextLong();
        }

        public Spawn at(Vec3 position) {
            this.anchor = Anchor.at(position);
            return this;
        }

        public Spawn at(double x, double y, double z) {
            return at(new Vec3(x, y, z));
        }

        public Spawn at(BlockPos pos) {
            return at(Vec3.atBottomCenterOf(pos));
        }

        /** Follows an entity's feet. */
        public Spawn on(Entity entity) {
            this.anchor = Anchor.on(entity);
            return this;
        }

        public Spawn on(Entity entity, Anchor.Point point, Vec3 offset) {
            this.anchor = Anchor.on(entity, point, offset);
            return this;
        }

        public Spawn anchor(Anchor anchor) {
            this.anchor = anchor;
            return this;
        }

        /** Fixes the seed (otherwise random). Same seed = same effect on every client. */
        public Spawn seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** Timeline speed: 0.5 plays everything in slow motion, 2 twice as fast. */
        public Spawn speed(float speed) {
            this.speed = speed;
            return this;
        }

        /** Players farther than this from the anchor do not receive the effect (default 512). */
        public Spawn range(double blocks) {
            this.range = blocks;
            return this;
        }

        public Spawn param(String key, double value) {
            params.put(key, value);
            return this;
        }

        public Spawn param(String key, boolean value) {
            params.put(key, value);
            return this;
        }

        public Spawn param(String key, String value) {
            params.put(key, value);
            return this;
        }

        public Spawn param(String key, Vec3 value) {
            params.put(key, value);
            return this;
        }

        public Spawn param(String key, double... value) {
            params.put(key, value);
            return this;
        }

        public Spawn color(String key, int argb) {
            params.color(key, argb);
            return this;
        }

        public Spawn params(FxParams values) {
            params.putAll(values);
            return this;
        }

        /** Sends only to these players instead of everyone in range. */
        public Spawn to(ServerPlayer... players) {
            return to(List.of(players));
        }

        public Spawn to(Collection<ServerPlayer> players) {
            this.recipients = new ArrayList<>(players);
            return this;
        }

        /**
         * Keeps the effect alive on the server so players who arrive (or load in) later also see it, entering the
         * timeline at its current tick. Lasts until {@link FxHandle#stop()}, or {@code ticks} game ticks if > 0.
         */
        public Spawn tracked(int ticks) {
            this.tracked = true;
            this.lifetime = ticks;
            return this;
        }

        public Spawn tracked() {
            return tracked(-1);
        }

        /**
         * How long (game ticks) the server keeps this effect addressable through its handle when it is not tracked
         * and has no server part. Default: 12000 (10 minutes).
         */
        public Spawn lifetime(int ticks) {
            this.lifetime = ticks;
            return this;
        }

        /** Server logic that runs on the same timeline. */
        public Spawn server(ServerEffect effect) {
            this.server = effect;
            return this;
        }

        /** Ignore any {@link ServerEffects} factory registered for this type. */
        public Spawn noServerLogic() {
            this.registeredServer = false;
            return this;
        }

        public FxHandle send() {
            if (anchor == null) throw new IllegalStateException("effect " + type + " has no position: call at(...) or on(...)");
            return EffectTracker.INSTANCE.start(this);
        }
    }
}
