package me.julionxn.blocmpact.client.impl;

import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.ClientFxHandle;
import me.julionxn.blocmpact.client.api.EffectFactory;
import me.julionxn.blocmpact.client.api.EffectSpawn;
import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.TickContext;
import me.julionxn.blocmpact.client.api.VisualEffect;
import me.julionxn.blocmpact.client.impl.data.DataEffects;
import me.julionxn.blocmpact.network.FxEventPayload;
import me.julionxn.blocmpact.network.FxSpawnPayload;
import me.julionxn.blocmpact.network.FxSpeedPayload;
import me.julionxn.blocmpact.network.FxStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Runs every client effect: creation, ticking, stop/events, scheduled sounds, silence and per-frame dispatch. */
public final class EffectManager {
    public static final EffectManager INSTANCE = new EffectManager();

    private final Map<Identifier, EffectFactory> factories = new ConcurrentHashMap<>();
    private final Map<Long, VisualEffect> active = new LinkedHashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final Set<Identifier> unknownReported = new HashSet<>();
    private long nextLocalId = -1, gameTicks;
    private boolean silenced;
    private ClientLevel lastLevel;

    private record Scheduled(long due, Runnable action) {
    }

    // ------------------------------------------------------------------ registry

    public void register(Identifier type, EffectFactory factory) {
        factories.put(type, factory);
    }

    public boolean isKnown(Identifier type) {
        return factories.containsKey(type) || DataEffects.INSTANCE.has(type);
    }

    public Set<Identifier> types() {
        Set<Identifier> s = new TreeSet<>(factories.keySet());
        s.addAll(DataEffects.INSTANCE.ids());
        return Collections.unmodifiableSet(s);
    }

    private EffectFactory factory(Identifier type) {
        EffectFactory f = factories.get(type);
        return f != null ? f : DataEffects.INSTANCE.factory(type);
    }

    // ------------------------------------------------------------------ starting

    public void onSpawnPayload(FxSpawnPayload p) {
        start(new EffectSpawn(p.effectType(), p.instanceId(), p.anchor(), p.seed(), p.speed(), p.startTick(), p.params()), null);
    }

    public void onStopPayload(FxStopPayload p) {
        if (p.instanceId() == FxStopPayload.ALL) stopAll(p.immediate());
        else stop(p.instanceId(), p.immediate());
    }

    public void onEventPayload(FxEventPayload p) {
        event(p.instanceId(), p.name(), p.data());
    }

    public void onSpeedPayload(FxSpeedPayload p) {
        speed(p.instanceId(), p.speed(), p.at());
    }

    public ClientFxHandle playLocal(EffectSpawn spawn, EffectFactory explicit) {
        long id;
        synchronized (active) {
            id = nextLocalId--;
        }
        return start(spawn.withId(id), explicit);
    }

    private ClientFxHandle start(EffectSpawn spawn, EffectFactory explicit) {
        Minecraft mc = Minecraft.getInstance();
        checkLevel(mc);
        if (mc.level == null) return ClientFxHandle.NONE;
        EffectFactory f = explicit != null ? explicit : factory(spawn.type());
        if (f == null) {
            if (unknownReported.add(spawn.type())) {
                FailureLog.report("unknown effect " + spawn.type(), "no Java factory and no assets/" + spawn.type().getNamespace()
                    + "/blocmpact/effects/" + spawn.type().getPath() + ".json");
            }
            return ClientFxHandle.NONE;
        }
        VisualEffect fx;
        try {
            fx = f.create(spawn);
            fx.internalStart(context(mc, fx, spawn.startTick()));
        } catch (RuntimeException e) {
            FailureLog.report("effect " + spawn.type() + " failed to start", e.toString(), e);
            return ClientFxHandle.NONE;
        }
        synchronized (active) {
            VisualEffect previous = active.put(spawn.id(), fx);
            if (previous != null) removed(previous);
            int max = Math.max(1, BlocmpactConfig.get().maxEffects);
            while (active.size() > max) {
                Iterator<VisualEffect> it = active.values().iterator();
                VisualEffect oldest = it.next();
                it.remove();
                removed(oldest);
            }
        }
        return new Handle(spawn.id());
    }

    private TickContext context(Minecraft mc, VisualEffect fx, int tick) {
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        return new TickContext(mc, mc.level, fx, tick, cam);
    }

    // ------------------------------------------------------------------ control

    public void stop(long id, boolean immediate) {
        VisualEffect fx;
        synchronized (active) {
            fx = active.get(id);
        }
        if (fx == null) return;
        Minecraft mc = Minecraft.getInstance();
        guard(fx, "stop", () -> fx.internalStop(context(mc, fx, fx.age()), immediate));
    }

    public void stopAll(boolean immediate) {
        for (VisualEffect fx : activeList()) stop(fx.id(), immediate);
    }

    public void event(long id, String name, FxParams data) {
        VisualEffect fx;
        synchronized (active) {
            fx = active.get(id);
        }
        if (fx == null) return;
        Minecraft mc = Minecraft.getInstance();
        guard(fx, "event " + name, () -> fx.internalEvent(context(mc, fx, fx.age()), name, data));
    }

    /** {@code at}: timeline position to move to first (the server's), or NaN to change at the current one. */
    public void speed(long id, float speed, double at) {
        VisualEffect fx;
        synchronized (active) {
            fx = active.get(id);
        }
        if (fx == null) return;
        guard(fx, "speed", () -> fx.internalSpeed(speed, at));
    }

    public ClientFxHandle handle(long id) {
        synchronized (active) {
            return active.containsKey(id) ? new Handle(id) : null;
        }
    }

    public List<VisualEffect> activeList() {
        synchronized (active) {
            return List.copyOf(active.values());
        }
    }

    public int count() {
        synchronized (active) {
            return active.size();
        }
    }

    /** Runs {@code action} after {@code ticks} game ticks. */
    public void schedule(int ticks, Runnable action) {
        synchronized (scheduled) {
            scheduled.add(new Scheduled(gameTicks + Math.max(0, ticks), action));
        }
    }

    // ------------------------------------------------------------------ ticking

    /** Effects belong to a level: when it changes (disconnect, dimension change) the old ones are dropped. */
    private void checkLevel(Minecraft mc) {
        if (mc.level != lastLevel) {
            clear();
            unsilence(mc);
            lastLevel = mc.level;
        }
    }

    public void tick(Minecraft mc) {
        checkLevel(mc);
        if (mc.level == null || mc.isPaused()) return;
        gameTicks++;
        runScheduled();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        for (VisualEffect fx : activeList()) {
            guard(fx, "tick", () -> fx.internalTick(new TickContext.Factory() {
                @Override
                public ClientLevel level() {
                    return mc.level;
                }

                @Override
                public TickContext at(int tick) {
                    return new TickContext(mc, mc.level, fx, tick, cam);
                }
            }));
        }
        synchronized (active) {
            Iterator<VisualEffect> it = active.values().iterator();
            while (it.hasNext()) {
                VisualEffect fx = it.next();
                if (fx.isDone()) {
                    it.remove();
                    removed(fx);
                }
            }
        }
        updateSilence(mc);
    }

    private void runScheduled() {
        List<Scheduled> due = new ArrayList<>();
        synchronized (scheduled) {
            Iterator<Scheduled> it = scheduled.iterator();
            while (it.hasNext()) {
                Scheduled s = it.next();
                if (s.due <= gameTicks) {
                    due.add(s);
                    it.remove();
                }
            }
        }
        for (Scheduled s : due) {
            try {
                s.action.run();
            } catch (RuntimeException e) {
                FailureLog.report("scheduled effect action failed", e.toString(), e);
            }
        }
    }

    private void updateSilence(Minecraft mc) {
        Frame f = FrameState.lastFrame();
        boolean silence = false;
        if (f != null) for (VisualEffect fx : activeList()) {
            if (inRange(fx, f)) {
                try {
                    if (fx.silencing(f)) {
                        silence = true;
                        break;
                    }
                } catch (RuntimeException e) {
                    fail(fx, "silencing", e);
                }
            }
        }
        if (silence) {
            mc.getSoundManager().pauseAllExcept(SoundSource.MASTER, SoundSource.PLAYERS, SoundSource.UI);
            silenced = true;
        } else unsilence(mc);
    }

    private void unsilence(Minecraft mc) {
        if (silenced) {
            silenced = false;
            mc.getSoundManager().resume();
        }
    }

    public void clear() {
        List<VisualEffect> all;
        synchronized (active) {
            all = new ArrayList<>(active.values());
            active.clear();
        }
        for (VisualEffect fx : all) removed(fx);
        synchronized (scheduled) {
            scheduled.clear();
        }
    }

    private void removed(VisualEffect fx) {
        try {
            fx.internalRemoved();
        } catch (RuntimeException e) {
            FailureLog.report("effect " + fx.type() + " failed in onRemoved", e.toString(), e);
        }
    }

    // ------------------------------------------------------------------ per-frame dispatch

    public static boolean inRange(VisualEffect fx, Frame f) {
        double r = fx.range();
        return r <= 0 || r == Double.POSITIVE_INFINITY || f.camera().distanceToSqr(fx.origin(f.partial())) <= r * r;
    }

    /** Calls {@code hook} on every effect in range of {@code frame}, isolating failures. */
    public void forEachInRange(Frame frame, String hookName, Consumer<VisualEffect> hook) {
        for (VisualEffect fx : activeList()) {
            if (fx.isDone()) continue;
            if (!inRange(fx, frame)) continue;
            try {
                hook.accept(fx);
            } catch (RuntimeException e) {
                fail(fx, hookName, e);
            }
        }
    }

    private void guard(VisualEffect fx, String hookName, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            fail(fx, hookName, e);
        }
    }

    private void fail(VisualEffect fx, String hookName, RuntimeException e) {
        fx.internalFail();
        FailureLog.report("effect " + fx.type() + " failed in " + hookName, e.toString(), e);
    }

    // ------------------------------------------------------------------ handle

    private final class Handle implements ClientFxHandle {
        private final long id;

        Handle(long id) {
            this.id = id;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean alive() {
            synchronized (active) {
                VisualEffect fx = active.get(id);
                return fx != null && !fx.isDone();
            }
        }

        @Override
        public void stop(boolean immediate) {
            EffectManager.this.stop(id, immediate);
        }

        @Override
        public void event(String name, FxParams data) {
            EffectManager.this.event(id, name, data);
        }

        @Override
        public float speed() {
            VisualEffect fx = effect();
            return fx != null ? fx.speed() : 0;
        }

        @Override
        public void speed(float speed) {
            EffectManager.this.speed(id, speed, Double.NaN);
        }

        @Override
        public VisualEffect effect() {
            synchronized (active) {
                return active.get(id);
            }
        }
    }
}
