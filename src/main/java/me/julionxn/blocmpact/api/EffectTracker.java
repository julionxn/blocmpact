package me.julionxn.blocmpact.api;

import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.network.FxEventPayload;
import me.julionxn.blocmpact.network.FxSpawnPayload;
import me.julionxn.blocmpact.network.FxSpeedPayload;
import me.julionxn.blocmpact.network.FxStopPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server-side bookkeeping of running effects: recipients, late joiners, server parts, stop and events. */
final class EffectTracker {
    static final EffectTracker INSTANCE = new EffectTracker();
    private static final int DEFAULT_RETENTION = 12000, JOIN_CHECK_INTERVAL = 10;

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Running> running = new LinkedHashMap<>();
    private boolean booted;

    void bootstrap() {
        if (booted) return;
        booted = true;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (running) {
                running.clear();
            }
        });
    }

    FxHandle start(Effects.Spawn spawn) {
        long id = nextId.getAndIncrement();
        FxParams params = spawn.params.build();
        ServerEffect server = spawn.server;
        if (server == null && spawn.registeredServer) {
            ServerEffects.Factory f = ServerEffects.get(spawn.type);
            if (f != null) server = f.create(params);
        }
        Running r = new Running(id, spawn.level, spawn.type, spawn.anchor, spawn.seed, spawn.speed, params, spawn.range,
            spawn.recipients, spawn.tracked, spawn.lifetime, server);
        if (server != null) server.bind(spawn.level, r, spawn.anchor, spawn.seed, spawn.speed, params);
        synchronized (running) {
            running.put(id, r);
        }
        r.sendToNewRecipients();
        return r;
    }

    FxHandle find(long id) {
        synchronized (running) {
            return running.get(id);
        }
    }

    List<Long> ids() {
        synchronized (running) {
            return new ArrayList<>(running.keySet());
        }
    }

    void stopAll(MinecraftServer server, boolean immediate) {
        List<Running> all;
        synchronized (running) {
            all = new ArrayList<>(running.values());
        }
        for (Running r : all) r.stop(immediate);
        FxStopPayload stop = new FxStopPayload(FxStopPayload.ALL, immediate);
        for (ServerPlayer p : PlayerLookup.all(server)) send(p, stop);
    }

    private void tick(MinecraftServer server) {
        List<Running> snapshot;
        synchronized (running) {
            snapshot = new ArrayList<>(running.values());
        }
        for (Running r : snapshot) {
            boolean keep;
            try {
                keep = r.tick();
            } catch (RuntimeException e) {
                Blocmpact.LOGGER.error("Server part of effect {} (#{}) failed and was removed", r.type, r.id, e);
                keep = false;
            }
            if (!keep) {
                r.alive = false;
                r.finishServer();
                synchronized (running) {
                    running.remove(r.id);
                }
            }
        }
    }

    /** Players without Blocmpact (vanilla clients on a server that has it) cannot receive its packets. */
    private static void send(ServerPlayer p, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(p, payload.type())) ServerPlayNetworking.send(p, payload);
    }

    private static final class Running implements FxHandle {
        final long id;
        final ServerLevel level;
        final Identifier type;
        final Anchor anchor;
        final long seed;
        final FxParams params;
        final double range;
        final List<ServerPlayer> fixedRecipients;
        final boolean tracked;
        final int lifetime;
        final ServerEffect server;
        final FxClock clock;
        final Set<UUID> sentTo = new HashSet<>();
        boolean serverDone, alive = true, stopped;
        int age;

        Running(long id, ServerLevel level, Identifier type, Anchor anchor, long seed, float speed, FxParams params, double range,
                List<ServerPlayer> fixedRecipients, boolean tracked, int lifetime, ServerEffect server) {
            this.id = id;
            this.level = level;
            this.type = type;
            this.anchor = anchor;
            this.seed = seed;
            this.params = params;
            this.range = range;
            this.fixedRecipients = fixedRecipients;
            this.tracked = tracked;
            this.lifetime = lifetime;
            this.server = server;
            this.clock = new FxClock(speed);
            this.serverDone = server == null;
        }

        /** Returns false when the record can be dropped. */
        boolean tick() {
            age++;
            clock.advance(t -> {
            });
            if (!serverDone && server.tickInternal()) finishServer();
            if (stopped) return !serverDone;
            if (tracked && age % JOIN_CHECK_INTERVAL == 0) sendToNewRecipients();
            int limit = lifetime > 0 ? lifetime : tracked ? Integer.MAX_VALUE : DEFAULT_RETENTION;
            if (age >= limit) {
                if (tracked) stop(false);
                return !serverDone;
            }
            return true;
        }

        void sendToNewRecipients() {
            if (stopped) return;
            // Start at the clock's position, not at age(): when paused (or with speed != 1) the tick at the current
            // position may have run already, and the newcomer must hold on that same tick. The server part's clock
            // is not used because it stops advancing once that part is done.
            int startTick = (int) Math.floor(clock.clock() + 1e-6);
            FxSpawnPayload payload = new FxSpawnPayload(id, type, anchor, seed, clock.speed(), startTick, params);
            Iterable<ServerPlayer> candidates = fixedRecipients != null ? fixedRecipients : PlayerLookup.around(level, position(), range);
            for (ServerPlayer p : candidates) {
                if (p.level() != level || sentTo.contains(p.getUUID()) || !ServerPlayNetworking.canSend(p, FxSpawnPayload.TYPE)) continue;
                sentTo.add(p.getUUID());
                ServerPlayNetworking.send(p, payload);
            }
        }

        void finishServer() {
            if (serverDone) return;
            serverDone = true;
            server.onRemoved();
        }

        Vec3 position() {
            return anchor.resolve(level, 1f);
        }

        private void broadcast(CustomPacketPayload payload) {
            MinecraftServer srv = level.getServer();
            for (UUID uuid : sentTo) {
                ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                if (p != null) send(p, payload);
            }
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean alive() {
            return alive && !stopped;
        }

        @Override
        public void stop(boolean immediate) {
            if (stopped) return;
            stopped = true;
            if (clock.paused()) immediate = true;      // a frozen outro would never end
            broadcast(new FxStopPayload(id, immediate));
            if (server != null && !serverDone) {
                if (immediate) finishServer();
                else server.stopInternal();
            }
        }

        @Override
        public float speed() {
            return clock.speed();
        }

        @Override
        public void speed(float speed) {
            if (stopped) return;
            double at = clock.clock();
            clock.setSpeed(speed);
            if (server != null && !serverDone) server.speedInternal(speed);
            broadcast(new FxSpeedPayload(id, clock.speed(), at));
        }

        @Override
        public void event(String name, FxParams data) {
            if (stopped) return;
            broadcast(new FxEventPayload(id, name, data));
            if (server != null && !serverDone) server.eventInternal(name, data);
        }
    }
}
