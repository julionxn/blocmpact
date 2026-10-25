package me.julionxn.blocmpact.client.impl.data;

import com.google.gson.JsonElement;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.EffectSpawn;
import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.TickContext;
import me.julionxn.blocmpact.client.api.VisualEffect;
import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.api.data.Expr;
import me.julionxn.blocmpact.client.api.entity.EntityFx;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.api.geometry.GeometryContext;
import me.julionxn.blocmpact.client.api.hud.Canvas;
import me.julionxn.blocmpact.client.api.hud.HudContext;
import me.julionxn.blocmpact.client.api.post.PassCollector;
import me.julionxn.blocmpact.client.api.post.PostPass;
import me.julionxn.blocmpact.client.api.post.ScreenFx;
import me.julionxn.blocmpact.client.impl.FailureLog;
import me.julionxn.blocmpact.client.impl.FrameState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Runs a {@link DataEffectDef}. */
final class DataEffect extends VisualEffect {
    private final DataEffectDef def;
    private final FxParams params;
    private final Map<String, Double> paramValues = new HashMap<>();
    private final Map<String, Double> state = new HashMap<>();
    private final Map<JsonElement, ParticleOptions> particles = new IdentityHashMap<>();
    private final int duration;
    private int end = Integer.MAX_VALUE;
    private boolean finishRequested;

    DataEffect(EffectSpawn spawn, DataEffectDef def) {
        super(spawn);
        this.def = def;
        this.params = spawn.params().withDefaults(def.defaults);
        flattenParams();
        Ctx c = new Ctx(0, 0, null, Minecraft.getInstance().gameRenderer.mainCamera().position(), spawn.anchor().position());
        for (Map.Entry<String, Expr> e : def.state.entrySet()) state.put(e.getKey(), e.getValue().eval(c));
        int d = (int) Math.round(def.duration.eval(c));
        this.duration = d;
    }

    private void flattenParams() {
        for (String k : params.keys()) {
            Object v = params.raw(k);
            if (v instanceof Double d) paramValues.put(k, d);
            else if (v instanceof Boolean b) paramValues.put(k, b ? 1.0 : 0.0);
            else if (v instanceof double[] a) {
                String[] xyz = {"x", "y", "z", "w"}, rgba = {"r", "g", "b", "a"};
                for (int i = 0; i < a.length; i++) {
                    paramValues.put(k + "_" + i, a[i]);
                    if (i < 4) {
                        paramValues.put(k + "_" + xyz[i], a[i]);
                        paramValues.put(k + "_" + rgba[i], a[i]);
                    }
                }
                paramValues.put(k, a.length > 0 ? a[0] : 0);
            } else if (v instanceof String s) {
                Integer col = FxParams.tryParseColor(s);
                if (col != null) {
                    paramValues.put(k + "_r", (double) FxMath.red(col));
                    paramValues.put(k + "_g", (double) FxMath.green(col));
                    paramValues.put(k + "_b", (double) FxMath.blue(col));
                    paramValues.put(k + "_a", (double) FxMath.alpha(col));
                } else {
                    try {
                        paramValues.put(k, Double.parseDouble(s));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public int duration() {
        int d = duration < 0 ? Integer.MAX_VALUE : duration;
        return Math.min(d, end) == Integer.MAX_VALUE ? -1 : Math.min(d, end);
    }

    @Override
    public boolean isDone() {
        return finishRequested || super.isDone();
    }

    @Override
    public double range() {
        return def.range;
    }

    void finishNow() {
        finishRequested = true;
    }

    void setState(String key, double value) {
        state.put(key, value);
    }

    @Override
    protected void onTick(TickContext t, int tick) {
        Ctx c = new Ctx(tick, 1f, null, t.camera(), origin());
        c.computeVars();
        for (DataEffectDef.Entry e : def.timeline) {
            if (e.matches(c, tick)) for (DataEffectDef.Action a : e.actions()) a.run(c, t);
        }
    }

    @Override
    protected void onEvent(TickContext t, String name, FxParams data) {
        List<DataEffectDef.Action> actions = def.events.get(name);
        if (actions == null) return;
        Ctx c = new Ctx(t.tick(), 1f, null, t.camera(), origin());
        for (String k : data.keys()) c.extra.put("event_" + k, data.getDouble(k, 0));
        c.computeVars();
        for (DataEffectDef.Action a : actions) a.run(c, t);
    }

    @Override
    protected void onStopRequested(TickContext t) {
        Ctx c = new Ctx(t.tick(), 1f, null, t.camera(), origin());
        c.computeVars();
        for (DataEffectDef.Action a : def.onStop) a.run(c, t);
        int outro = (int) Math.round(def.outro.eval(c));
        if (outro <= 0) finish();
        else end = age() + outro;
    }

    // ------------------------------------------------------------------ frame hooks

    private Ctx frameCtx(Frame f) {
        Ctx c = new Ctx(time(f.partial()), f.partial(), f, f.camera(), origin(f.partial()));
        c.computeVars();
        return c;
    }

    @Override
    public void passes(Frame frame, PassCollector out) {
        if (def.passes.isEmpty()) return;
        Ctx c = frameCtx(frame);
        List<PostPass> list = new ArrayList<>();
        for (DataEffectDef.PassOp op : def.passes) op.add(c, list);
        list.forEach(out::add);
    }

    @Override
    public void screen(Frame frame, ScreenFx screen) {
        if (def.screen.isEmpty()) return;
        Ctx c = frameCtx(frame);
        for (DataEffectDef.ScreenOp op : def.screen) op.apply(c, screen);
    }

    @Override
    public void environment(Frame frame, EnvironmentFx env) {
        if (def.environment.isEmpty()) return;
        Ctx c = frameCtx(frame);
        for (DataEffectDef.EnvOp op : def.environment) op.apply(c, env);
    }

    @Override
    public void camera(Frame frame, CameraFx camera) {
        if (def.camera.isEmpty()) return;
        Ctx c = frameCtx(frame);
        for (DataEffectDef.CameraOp op : def.camera) op.apply(c, camera);
    }

    @Override
    public void entities(Frame frame, EntityFx entities) {
        if (def.entities.isEmpty()) return;
        Ctx c = frameCtx(frame);
        for (DataEffectDef.EntityOp op : def.entities) op.apply(c, entities);
    }

    @Override
    public void geometry(GeometryContext g) {
        if (def.geometry.isEmpty()) return;
        Ctx c = frameCtx(g.frame());
        for (DataEffectDef.GeoOp op : def.geometry) op.draw(c, g);
    }

    @Override
    public void hud(HudContext h) {
        if (def.hud.isEmpty()) return;
        Frame f = h.frame() != null ? h.frame() : FrameState.currentFrame(h.partial());
        try (Canvas cv = h.canvas(def.hudHeight)) {
            Ctx c = new Ctx(time(h.partial()), h.partial(), f, f.camera(), origin(h.partial()));
            c.extra.put("W", (double) cv.width());
            c.extra.put("H", (double) cv.height());
            c.computeVars();
            for (DataEffectDef.HudOp op : def.hud) op.draw(c, cv);
        }
    }

    @Override
    public boolean silencing(Frame frame) {
        return def.silence != null && def.silence.test(frameCtx(frame));
    }

    // ------------------------------------------------------------------ particles

    ParticleOptions particle(JsonElement json, String where) {
        ParticleOptions p = particles.get(json);
        if (p == null && !particles.containsKey(json)) {
            try {
                p = DataEffectDef.decodeParticle(json, params, where);
            } catch (RuntimeException e) {
                FailureLog.report("data effect " + def.id + ": " + e.getMessage(), e.toString());
            }
            particles.put(json, p);
        }
        return p;
    }

    // ------------------------------------------------------------------ evaluation context

    /** Variables for one evaluation: built-ins, params, state, extras and the definition's vars. */
    final class Ctx implements Expr.Env {
        final DataEffect effect = DataEffect.this;
        final Map<String, Double> extra = new HashMap<>();
        private final Map<String, Double> vars = new HashMap<>();
        private final float t, partial;
        private final Frame frame;
        private final Vec3 camera, origin;

        Ctx(float t, float partial, Frame frame, Vec3 camera, Vec3 origin) {
            this.t = t;
            this.partial = partial;
            this.frame = frame;
            this.camera = camera;
            this.origin = origin;
        }

        void computeVars() {
            for (Map.Entry<String, Expr> e : def.vars.entrySet()) vars.put(e.getKey(), e.getValue().eval(this));
        }

        float t() {
            return t;
        }

        Frame frame() {
            return frame != null ? frame : FrameState.currentFrame(partial);
        }

        FxParams params() {
            return params;
        }

        Level level() {
            return Minecraft.getInstance().level;
        }

        Vec3 pos(Specs.VecSpec v) {
            return v.eval(this, origin, camera, params);
        }

        Vec3 vec(Specs.VecSpec v) {
            return v.eval(this, Vec3.ZERO, camera, params);
        }

        int color(Specs.ColorSpec c, Expr alpha) {
            int col = c.eval(this, params);
            float a = alpha.evalf(this);
            return a == 1f ? col : FxMath.fadeAlpha(col, a);
        }

        /** Screen uv of a point (y up), or the center. */
        float[] focus(Specs.VecSpec at) {
            if (at == null) return new float[]{0.5f, 0.5f};
            Vector3f p = frame().projectUv(pos(at));
            return p.z > 0 ? new float[]{p.x, p.y} : new float[]{0.5f, 0.5f};
        }

        @Override
        public long seed() {
            return effect.seed();
        }

        @Override
        public double get(String name) {
            Double v = vars.get(name);
            if (v != null) return v;
            v = extra.get(name);
            if (v != null) return v;
            v = state.get(name);
            if (v != null) return v;
            v = paramValues.get(name);
            if (v != null) return v;
            switch (name) {
                case "t":
                    return t;
                case "tick":
                    return Math.floor(t);
                case "s":
                    return t / 20.0;
                case "age":
                    return age();
                case "duration":
                    return duration;
                case "speed":
                    return speed();
                case "seed":
                    return seedF();
                case "partial":
                    return partial;
                case "ox":
                    return origin.x;
                case "oy":
                    return origin.y;
                case "oz":
                    return origin.z;
                case "cx":
                    return camera.x;
                case "cy":
                    return camera.y;
                case "cz":
                    return camera.z;
                case "dist":
                    return camera.distanceTo(origin);
                case "stopping":
                    return stopping() ? 1 : 0;
                case "stop_t":
                    return stopping() ? t - stopTick() : -1;
                case "local":
                    return spawn().isLocal() ? 1 : 0;
                default:
                    FailureLog.report("data effect " + def.id + " uses unknown variable '" + name + "'", "it evaluates to NaN");
                    return Double.NaN;
            }
        }
    }
}
