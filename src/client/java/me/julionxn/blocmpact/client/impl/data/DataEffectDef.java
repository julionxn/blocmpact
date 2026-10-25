package me.julionxn.blocmpact.client.impl.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.TickContext;
import me.julionxn.blocmpact.client.api.camera.CameraFx;
import me.julionxn.blocmpact.client.api.data.Expr;
import me.julionxn.blocmpact.client.api.entity.EntityFx;
import me.julionxn.blocmpact.client.api.entity.PartPose;
import me.julionxn.blocmpact.client.api.env.EnvironmentFx;
import me.julionxn.blocmpact.client.api.geometry.FxRenderTypes;
import me.julionxn.blocmpact.client.api.geometry.FxTextures;
import me.julionxn.blocmpact.client.api.geometry.GeometryContext;
import me.julionxn.blocmpact.client.api.hud.Canvas;
import me.julionxn.blocmpact.client.api.post.ImpactFrame;
import me.julionxn.blocmpact.client.api.post.PostPass;
import me.julionxn.blocmpact.client.api.post.ScreenFx;
import me.julionxn.blocmpact.client.api.post.Uniforms;
import me.julionxn.blocmpact.client.api.world.Shape;
import me.julionxn.blocmpact.client.api.world.Velocity;
import me.julionxn.blocmpact.client.impl.data.Specs.ColorSpec;
import me.julionxn.blocmpact.client.impl.data.Specs.DataException;
import me.julionxn.blocmpact.client.impl.data.Specs.VecSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.julionxn.blocmpact.client.impl.data.Specs.array;
import static me.julionxn.blocmpact.client.impl.data.Specs.bool;
import static me.julionxn.blocmpact.client.impl.data.Specs.expr;
import static me.julionxn.blocmpact.client.impl.data.Specs.object;
import static me.julionxn.blocmpact.client.impl.data.Specs.string;
import static me.julionxn.blocmpact.client.impl.data.Specs.when;

/**
 * Parsed form of {@code assets/<ns>/blocmpact/effects/<path>.json}. Every layer of the Java API is reachable:
 * timeline actions (particles, sounds, child effects, state), passes, compositor, environment, camera, entities, HUD
 * and geometry, with expressions for any number. The schema is documented at https://julionxn.github.io/blocmpact/data.html.
 */
final class DataEffectDef {
    final Identifier id;
    Expr duration = Expr.constant(-1), outro = Expr.constant(0), silence;
    double range = 512;
    float hudHeight = 720;
    FxParams defaults = FxParams.EMPTY;
    final Map<String, Expr> vars = new LinkedHashMap<>(), state = new LinkedHashMap<>();
    final List<Entry> timeline = new ArrayList<>();
    final Map<String, List<Action>> events = new LinkedHashMap<>();
    final List<Action> onStop = new ArrayList<>();
    final List<PassOp> passes = new ArrayList<>();
    final List<ScreenOp> screen = new ArrayList<>();
    final List<EnvOp> environment = new ArrayList<>();
    final List<CameraOp> camera = new ArrayList<>();
    final List<EntityOp> entities = new ArrayList<>();
    final List<HudOp> hud = new ArrayList<>();
    final List<GeoOp> geometry = new ArrayList<>();

    // ------------------------------------------------------------------ op types

    interface Action {
        void run(DataEffect.Ctx c, TickContext t);
    }

    interface PassOp {
        void add(DataEffect.Ctx c, List<PostPass> out);
    }

    interface ScreenOp {
        void apply(DataEffect.Ctx c, ScreenFx s);
    }

    interface EnvOp {
        void apply(DataEffect.Ctx c, EnvironmentFx e);
    }

    interface CameraOp {
        void apply(DataEffect.Ctx c, CameraFx cam);
    }

    interface EntityOp {
        void apply(DataEffect.Ctx c, EntityFx e);
    }

    interface HudOp {
        void draw(DataEffect.Ctx c, Canvas canvas);
    }

    interface GeoOp {
        void draw(DataEffect.Ctx c, GeometryContext g);
    }

    record Entry(Expr at, Expr from, Expr to, Expr every, Expr when, List<Action> actions) {
        boolean matches(DataEffect.Ctx c, int tick) {
            if (at != null && Math.round(at.eval(c)) != tick) return false;
            if (from != null && tick < from.eval(c)) return false;
            if (to != null && tick > to.eval(c)) return false;
            if (every != null) {
                long k = Math.max(1, Math.round(every.eval(c)));
                long start = from != null ? Math.round(from.eval(c)) : 0;
                if ((tick - start) % k != 0) return false;
            }
            return when == null || when.test(c);
        }
    }

    // ------------------------------------------------------------------ parsing

    DataEffectDef(Identifier id) {
        this.id = id;
    }

    static DataEffectDef parse(Identifier id, JsonObject json) {
        DataEffectDef d = new DataEffectDef(id);
        String w = id.toString();
        if (json.has("duration")) d.duration = expr(json.get("duration"), w + ".duration");
        if (json.has("range")) d.range = json.get("range").getAsDouble();
        if (json.has("hud_height")) d.hudHeight = json.get("hud_height").getAsFloat();
        if (json.has("defaults")) d.defaults = FxParams.fromJson(object(json.get("defaults"), w + ".defaults"));
        if (json.has("vars")) object(json.get("vars"), w + ".vars").entrySet().forEach(e -> d.vars.put(e.getKey(), expr(e.getValue(), w + ".vars." + e.getKey())));
        if (json.has("state")) object(json.get("state"), w + ".state").entrySet().forEach(e -> d.state.put(e.getKey(), expr(e.getValue(), w + ".state." + e.getKey())));
        if (json.has("silence")) d.silence = expr(json.get("silence"), w + ".silence");
        if (json.has("stop")) {
            JsonObject s = object(json.get("stop"), w + ".stop");
            d.outro = Specs.expr(s, "outro", 0, w + ".stop");
            if (s.has("do")) d.onStop.addAll(actions(s.get("do"), w + ".stop.do"));
        }
        int i = 0;
        for (JsonElement e : array(json.get("timeline"))) d.timeline.add(entry(object(e, w + ".timeline[" + i + "]"), w + ".timeline[" + i++ + "]"));
        if (json.has("events")) object(json.get("events"), w + ".events").entrySet()
            .forEach(e -> d.events.put(e.getKey(), actions(e.getValue(), w + ".events." + e.getKey())));
        i = 0;
        for (JsonElement e : array(json.get("passes"))) d.passes.add(pass(object(e, w + ".passes[" + i + "]"), w + ".passes[" + i++ + "]"));
        if (json.has("screen")) d.screen.addAll(screen(object(json.get("screen"), w + ".screen"), w + ".screen"));
        if (json.has("environment")) d.environment.addAll(environment(object(json.get("environment"), w + ".environment"), w + ".environment"));
        if (json.has("camera")) d.camera.addAll(camera(object(json.get("camera"), w + ".camera"), w + ".camera"));
        if (json.has("entity")) d.entities.add(entity(object(json.get("entity"), w + ".entity"), w + ".entity"));
        i = 0;
        for (JsonElement e : array(json.get("hud"))) d.hud.add(hud(object(e, w + ".hud[" + i + "]"), w + ".hud[" + i++ + "]"));
        i = 0;
        for (JsonElement e : array(json.get("geometry"))) d.geometry.add(geometry(object(e, w + ".geometry[" + i + "]"), w + ".geometry[" + i++ + "]"));
        return d;
    }

    private static Entry entry(JsonObject o, String w) {
        Expr at = o.has("at") ? expr(o.get("at"), w + ".at") : null;
        Expr from = o.has("from") ? expr(o.get("from"), w + ".from") : null;
        Expr to = o.has("to") ? expr(o.get("to"), w + ".to") : null;
        Expr every = o.has("every") ? expr(o.get("every"), w + ".every") : null;
        if (at == null && from == null && to == null && every == null && !o.has("when")) at = Expr.constant(0);
        return new Entry(at, from, to, every, when(o, w), actions(o.get("do"), w + ".do"));
    }

    static List<Action> actions(JsonElement e, String w) {
        List<Action> out = new ArrayList<>();
        int i = 0;
        for (JsonElement a : array(e)) out.add(action(object(a, w + "[" + i + "]"), w + "[" + i++ + "]"));
        return out;
    }

    private static Action action(JsonObject o, String w) {
        Expr cond = when(o, w);
        Action a;
        if (o.has("particles")) a = particles(object(o.get("particles"), w + ".particles"), w + ".particles");
        else if (o.has("sound")) a = sound(object(o.get("sound"), w + ".sound"), w + ".sound");
        else if (o.has("spawn")) a = spawn(object(o.get("spawn"), w + ".spawn"), w + ".spawn");
        else if (o.has("set")) {
            Map<String, Expr> sets = new LinkedHashMap<>();
            object(o.get("set"), w + ".set").entrySet().forEach(e -> sets.put(e.getKey(), expr(e.getValue(), w + ".set." + e.getKey())));
            a = (c, t) -> sets.forEach((k, v) -> c.effect.setState(k, v.eval(c)));
        } else if (o.has("finish")) a = (c, t) -> c.effect.finishNow();
        else throw new DataException("unknown action at " + w + " (particles, sound, spawn, set, finish)");
        if (cond == null) return a;
        Action inner = a;
        return (c, t) -> {
            if (cond.test(c)) inner.run(c, t);
        };
    }

    // ---- particles

    private static Action particles(JsonObject o, String w) {
        JsonElement particleJson = o.get("particle");
        if (particleJson == null) throw new DataException("missing particle at " + w);
        JsonObject shape = o.has("shape") ? object(o.get("shape"), w + ".shape") : new JsonObject();
        ShapeFn shapeFn = shape(shape, w + ".shape");
        Expr count = Specs.expr(o, "count", 1, w);
        VelocityFn vel = o.has("velocity") ? velocity(object(o.get("velocity"), w + ".velocity"), w + ".velocity") : (c, center) -> Velocity.NONE;
        Expr lod = o.has("lod") ? expr(o.get("lod"), w + ".lod") : null;
        VecSpec at = VecSpec.parse(o.get("at"), w + ".at");
        return (c, t) -> {
            ParticleOptions p = c.effect.particle(particleJson, w);
            if (p == null) return;
            Vec3 center = c.pos(at);
            int n = (int) Math.max(0, Math.round(count.eval(c)));
            if (lod != null) n = t.particles().lod(n, center, lod.eval(c));
            t.particles().emit(p, shapeFn.make(c, center), n, vel.make(c, center));
        };
    }

    interface ShapeFn {
        Shape make(DataEffect.Ctx c, Vec3 center);
    }

    interface VelocityFn {
        Velocity make(DataEffect.Ctx c, Vec3 center);
    }

    private static ShapeFn shape(JsonObject o, String w) {
        String type = string(o, "type", "point").toLowerCase(Locale.ROOT);
        Expr radius = Specs.expr(o, "radius", 1, w), phase = Specs.expr(o, "phase", 0, w);
        VecSpec normal = VecSpec.raw(o.get("normal"), new Vec3(0, 1, 0), w + ".normal");
        Expr jitter = o.has("jitter") ? expr(o.get("jitter"), w + ".jitter") : null;
        Expr ground = o.has("ground") ? expr(o.get("ground"), w + ".ground") : null;
        ShapeFn base = switch (type) {
            case "point" -> (c, p) -> Shape.point(p);
            case "ring" -> (c, p) -> Shape.ring(p, c.vec(normal), radius.eval(c), phase.eval(c));
            case "disc" -> (c, p) -> Shape.disc(p, c.vec(normal), radius.eval(c));
            case "sphere" -> (c, p) -> Shape.sphere(p, radius.eval(c));
            case "dome" -> (c, p) -> Shape.dome(p, radius.eval(c));
            case "ball" -> (c, p) -> Shape.ball(p, radius.eval(c));
            case "helix" -> {
                Expr height = Specs.expr(o, "height", 2, w), turns = Specs.expr(o, "turns", 2, w);
                VecSpec axis = VecSpec.raw(o.get("axis"), new Vec3(0, 1, 0), w + ".axis");
                yield (c, p) -> Shape.helix(p, c.vec(axis), radius.eval(c), height.eval(c), turns.eval(c), phase.eval(c));
            }
            case "line" -> {
                VecSpec to = VecSpec.parse(o.get("to"), w + ".to");
                yield (c, p) -> Shape.line(p, c.pos(to));
            }
            case "arc" -> {
                Expr yaw = Specs.expr(o, "yaw", 0, w), from = Specs.expr(o, "from", -45, w), to = Specs.expr(o, "to", 45, w);
                yield (c, p) -> Shape.arc(p, yaw.evalf(c), radius.eval(c), from.eval(c), to.eval(c));
            }
            case "cone" -> {
                VecSpec dir = VecSpec.raw(o.get("direction"), new Vec3(0, 1, 0), w + ".direction");
                Expr angle = Specs.expr(o, "angle", 30, w);
                yield (c, p) -> Shape.cone(p, c.vec(dir), angle.eval(c));
            }
            case "box" -> {
                VecSpec size = VecSpec.raw(o.get("size"), new Vec3(1, 1, 1), w + ".size");
                yield (c, p) -> {
                    Vec3 s = c.vec(size).scale(0.5);
                    return Shape.box(new AABB(p.subtract(s), p.add(s)));
                };
            }
            case "spiral" -> {
                Expr r0 = Specs.expr(o, "inner", 0, w), turns = Specs.expr(o, "turns", 2, w);
                yield (c, p) -> Shape.spiral(p, c.vec(normal), r0.eval(c), radius.eval(c), turns.eval(c), phase.eval(c));
            }
            default -> throw new DataException("unknown shape '" + type + "' at " + w);
        };
        return (c, p) -> {
            Shape s = base.make(c, p);
            if (jitter != null) s = s.jitter(jitter.eval(c));
            if (ground != null) s = s.onGround(c.level(), ground.eval(c));
            return s;
        };
    }

    private static VelocityFn velocity(JsonObject o, String w) {
        String type = string(o, "type", "none").toLowerCase(Locale.ROOT);
        Expr speed = Specs.expr(o, "speed", 0.1, w);
        VelocityFn base = switch (type) {
            case "none" -> (c, p) -> Velocity.NONE;
            case "along" -> {
                Expr max = o.has("max") ? expr(o.get("max"), w + ".max") : null;
                yield (c, p) -> max == null ? Velocity.along(speed.eval(c)) : Velocity.along(speed.eval(c), max.eval(c));
            }
            case "fixed" -> {
                VecSpec v = VecSpec.raw(o.get("vector"), Vec3.ZERO, w + ".vector");
                yield (c, p) -> Velocity.fixed(c.vec(v));
            }
            case "random" -> (c, p) -> Velocity.random(speed.eval(c));
            case "towards" -> {
                VecSpec target = VecSpec.parse(o.get("target"), w + ".target");
                Expr ticks = Specs.expr(o, "ticks", 20, w);
                yield (c, p) -> Velocity.towards(c.pos(target), ticks.eval(c));
            }
            case "away" -> (c, p) -> Velocity.awayFrom(p, speed.eval(c));
            case "swirl" -> {
                VecSpec axis = VecSpec.raw(o.get("axis"), new Vec3(0, 1, 0), w + ".axis");
                yield (c, p) -> Velocity.swirl(p, c.vec(axis), speed.eval(c));
            }
            default -> throw new DataException("unknown velocity '" + type + "' at " + w);
        };
        if (!o.has("plus")) return base;
        VelocityFn plus = velocity(object(o.get("plus"), w + ".plus"), w + ".plus");
        return (c, p) -> base.make(c, p).plus(plus.make(c, p));
    }

    // ---- sounds and children

    private static Action sound(JsonObject o, String w) {
        Identifier id = Identifier.parse(string(o, "id", "minecraft:block.note_block.chime"));
        Expr volume = Specs.expr(o, "volume", 1, w), pitch = Specs.expr(o, "pitch", 1, w);
        String mode = string(o, "mode", "at").toLowerCase(Locale.ROOT);
        VecSpec at = VecSpec.parse(o.get("at"), w + ".at");
        Expr radius = Specs.expr(o, "radius", 0, w);
        return (c, t) -> {
            float v = volume.evalf(c), p = pitch.evalf(c);
            Vec3 pos = c.pos(at);
            switch (mode) {
                case "head" -> t.sounds().head(id, v, p);
                case "edge" -> t.sounds().edge(id, pos, radius.eval(c), v, p);
                case "speed_of_sound" -> t.sounds().atSpeedOfSound(id, pos, v, p);
                default -> t.sounds().at(id, pos, v, p);
            }
        };
    }

    private static Action spawn(JsonObject o, String w) {
        Identifier type = Identifier.parse(string(o, "type", ""));
        VecSpec at = VecSpec.parse(o.get("at"), w + ".at");
        Map<String, Expr> numeric = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        if (o.has("params")) object(o.get("params"), w + ".params").entrySet().forEach(e -> {
            if (e.getValue() instanceof JsonPrimitive p && p.isString() && p.getAsString().startsWith("#"))
                strings.put(e.getKey(), p.getAsString());
            else numeric.put(e.getKey(), expr(e.getValue(), w + ".params." + e.getKey()));
        });
        boolean inherit = bool(o, "inherit_params", false);
        return (c, t) -> {
            FxParams.Builder b = inherit ? c.params().toBuilder() : FxParams.builder();
            numeric.forEach((k, v) -> b.put(k, v.eval(c)));
            strings.forEach(b::put);
            t.spawn(type, c.pos(at), b.build());
        };
    }

    // ---- passes

    private static PassOp pass(JsonObject o, String w) {
        Identifier program = Identifier.parse(string(o, "program", ""));
        Expr cond = when(o, w);
        boolean overlay = "overlay".equalsIgnoreCase(string(o, "stage", "scene"));
        String output = string(o, "output", null);
        boolean persistent = bool(o, "persistent", false), hdr = bool(o, "hdr", true);
        float scale = o.has("scale") ? o.get("scale").getAsFloat() : 1f;
        Map<String, String> defines = new LinkedHashMap<>();
        if (o.has("defines")) object(o.get("defines"), w + ".defines").entrySet().forEach(e -> defines.put(e.getKey(), e.getValue().getAsString()));
        List<UniformOp> uniforms = new ArrayList<>();
        if (o.has("uniforms")) object(o.get("uniforms"), w + ".uniforms").entrySet()
            .forEach(e -> uniforms.add(uniform(e.getKey(), e.getValue(), w + ".uniforms." + e.getKey())));
        return (c, out) -> {
            if (cond != null && !cond.test(c)) return;
            PostPass p = PostPass.of(program, u -> {
                for (UniformOp op : uniforms) op.set(c, u);
            });
            if (overlay) p.overlay();
            if (output != null) {
                if (persistent) p.toPersistent(output);
                else p.to(output);
            }
            if (!hdr) p.lowPrecision();
            p.scale(scale);
            defines.forEach(p::define);
            out.add(p);
        };
    }

    interface UniformOp {
        void set(DataEffect.Ctx c, Uniforms u);
    }

    private static UniformOp uniform(String name, JsonElement e, String w) {
        if (e instanceof JsonPrimitive p && p.isString() && p.getAsString().startsWith("#")) {
            ColorSpec col = ColorSpec.parse(e, -1, w);
            return (c, u) -> u.color(name, col.eval(c, c.params()));
        }
        if (e instanceof JsonPrimitive) {
            Expr x = expr(e, w);
            return (c, u) -> u.f1(name, x.evalf(c));
        }
        if (e.isJsonArray()) {
            List<Expr> xs = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) xs.add(expr(x, w));
            if (xs.size() < 1 || xs.size() > 4) throw new DataException("uniform vectors have 1–4 components at " + w);
            return (c, u) -> {
                float[] v = new float[xs.size()];
                for (int i = 0; i < v.length; i++) v[i] = xs.get(i).evalf(c);
                switch (v.length) {
                    case 1 -> u.f1(name, v[0]);
                    case 2 -> u.f2(name, v[0], v[1]);
                    case 3 -> u.f3(name, v[0], v[1], v[2]);
                    default -> u.f4(name, v[0], v[1], v[2], v[3]);
                }
            };
        }
        JsonObject o = object(e, w);
        if (o.has("world")) {
            VecSpec v = VecSpec.parse(o.get("world"), w + ".world");
            return (c, u) -> u.world(name, c.pos(v));
        }
        if (o.has("screen")) {
            VecSpec v = VecSpec.parse(o.get("screen"), w + ".screen");
            return (c, u) -> u.screenPoint(name, c.pos(v));
        }
        if (o.has("color")) {
            ColorSpec col = ColorSpec.parse(o.get("color"), -1, w + ".color");
            return (c, u) -> u.color(name, col.eval(c, c.params()));
        }
        if (o.has("texture")) {
            Identifier tex = Identifier.parse(string(o, "texture", ""));
            boolean linear = bool(o, "linear", true), repeat = bool(o, "repeat", true);
            return (c, u) -> u.texture(name, tex, linear, repeat);
        }
        if (o.has("target")) {
            String target = string(o, "target", "");
            return (c, u) -> u.target(name, target);
        }
        if (o.has("history")) {
            Expr ago = expr(o.get("history"), w + ".history");
            return (c, u) -> u.history(name, ago.evalf(c));
        }
        if (o.has("points")) {
            List<VecSpec> pts = new ArrayList<>();
            List<Expr> ws = new ArrayList<>();
            int i = 0;
            for (JsonElement pe : array(o.get("points"))) {
                JsonObject po = pe instanceof JsonObject obj ? obj : null;
                pts.add(VecSpec.parse(po != null ? po.get("at") : pe, w + ".points[" + i + "]"));
                ws.add(po != null && po.has("w") ? expr(po.get("w"), w + ".points[" + i + "].w") : Expr.constant(0));
                i++;
            }
            return (c, u) -> {
                float[] data = new float[pts.size() * 4];
                for (int k = 0; k < pts.size(); k++) {
                    Vector3f r = c.frame().relf(c.pos(pts.get(k)));
                    data[k * 4] = r.x;
                    data[k * 4 + 1] = r.y;
                    data[k * 4 + 2] = r.z;
                    data[k * 4 + 3] = ws.get(k).evalf(c);
                }
                u.vec4s(name, data);
            };
        }
        throw new DataException("unknown uniform form at " + w + " (number, [..], #color, world, screen, color, texture, target, history, points)");
    }

    // ---- screen

    private static List<ScreenOp> screen(JsonObject o, String w) {
        List<ScreenOp> ops = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey(), kw = w + "." + k;
            JsonElement v = e.getValue();
            ops.add(switch (k) {
                case "shake" -> scalar(v, kw, (s, x) -> s.shake(x));
                case "aberration" -> scalar(v, kw, (s, x) -> s.aberration(x));
                case "threshold_bloom" -> scalar(v, kw, (s, x) -> s.thresholdBloom(x));
                case "adaptation" -> scalar(v, kw, (s, x) -> s.adaptation(x));
                case "exposure" -> scalar(v, kw, (s, x) -> s.exposure(x));
                case "saturation" -> scalar(v, kw, (s, x) -> s.saturation(x));
                case "gamma" -> scalar(v, kw, (s, x) -> s.gamma(x));
                case "hue_shift" -> scalar(v, kw, (s, x) -> s.hueShift(x));
                case "grain" -> scalar(v, kw, (s, x) -> s.grain(x));
                case "hold_fps" -> scalar(v, kw, (s, x) -> s.holdFps(x));
                case "contrast" -> {
                    JsonObject c = v instanceof JsonObject obj ? obj : wrap("value", v);
                    Expr val = Specs.expr(c, "value", 1, kw), pivot = Specs.expr(c, "pivot", 0.5, kw);
                    Expr cond = when(c, kw);
                    yield (ctx, s) -> {
                        if (cond == null || cond.test(ctx)) s.contrast(val.evalf(ctx), pivot.evalf(ctx));
                    };
                }
                case "tint", "lift", "gray_tint" -> {
                    ColorSpec col = ColorSpec.parse(v, -1, kw);
                    yield (ctx, s) -> {
                        int c = col.eval(ctx, ctx.params());
                        float r = FxMath.red(c), g = FxMath.green(c), b = FxMath.blue(c);
                        if (k.equals("tint")) s.tint(r, g, b);
                        else if (k.equals("lift")) s.lift(r, g, b);
                        else s.grayTint(r, g, b);
                    };
                }
                case "bloom" -> {
                    JsonObject c = object(v, kw);
                    Expr st = Specs.expr(c, "strength", 0.7, kw), rad = Specs.expr(c, "radius", 1, kw);
                    Expr cond = when(c, kw);
                    yield (ctx, s) -> {
                        if (cond == null || cond.test(ctx)) s.bloom(st.evalf(ctx), rad.evalf(ctx));
                    };
                }
                case "keep_color" -> {
                    JsonObject c = object(v, kw);
                    Expr am = Specs.expr(c, "amount", 1, kw), th = Specs.expr(c, "threshold", 0.75, kw);
                    yield (ctx, s) -> s.keepColor(am.evalf(ctx), th.evalf(ctx));
                }
                case "vignette", "flash", "fade" -> {
                    JsonObject c = object(v, kw);
                    Expr am = Specs.expr(c, "amount", 0, kw);
                    ColorSpec col = ColorSpec.parse(c.get("color"), k.equals("flash") ? 0xFFFFFFFF : 0xFF000000, kw + ".color");
                    Expr inner = c.has("inner") ? expr(c.get("inner"), kw) : null, outer = c.has("outer") ? expr(c.get("outer"), kw) : null;
                    Expr cond = when(c, kw);
                    yield (ctx, s) -> {
                        if (cond != null && !cond.test(ctx)) return;
                        int cc = col.eval(ctx, ctx.params());
                        float a = am.evalf(ctx), r = FxMath.red(cc), g = FxMath.green(cc), b = FxMath.blue(cc);
                        switch (k) {
                            case "vignette" -> {
                                s.vignette(a, r, g, b);
                                if (inner != null && outer != null) s.vignetteShape(inner.evalf(ctx), outer.evalf(ctx));
                            }
                            case "flash" -> s.flash(a, r, g, b);
                            default -> s.fade(a, r, g, b);
                        }
                    };
                }
                case "zoom_blur", "zoom" -> {
                    JsonObject c = object(v, kw);
                    Expr am = Specs.expr(c, k.equals("zoom") ? "scale" : "amount", k.equals("zoom") ? 1 : 0, kw);
                    VecSpec at = c.has("at") ? VecSpec.parse(c.get("at"), kw + ".at") : null;
                    yield (ctx, s) -> {
                        float u = 0.5f, vv = 0.5f;
                        if (at != null) {
                            Vector3f p = ctx.frame().projectUv(ctx.pos(at));
                            if (p.z > 0) {
                                u = p.x;
                                vv = p.y;
                            }
                        }
                        if (k.equals("zoom")) s.zoom(am.evalf(ctx), u, vv);
                        else s.zoomBlur(am.evalf(ctx), u, vv);
                    };
                }
                case "impact" -> impact(object(v, kw), kw);
                case "impact_cycle" -> impactCycle(object(v, kw), kw);
                default -> throw new DataException("unknown screen key '" + k + "' at " + w);
            });
        }
        return ops;
    }

    interface ScalarSetter {
        void set(ScreenFx s, float x);
    }

    private static ScreenOp scalar(JsonElement v, String w, ScalarSetter setter) {
        if (v instanceof JsonObject o) {
            Expr val = Specs.expr(o, "value", 0, w);
            Expr cond = when(o, w);
            return (c, s) -> {
                if (cond == null || cond.test(c)) setter.set(s, val.evalf(c));
            };
        }
        Expr x = expr(v, w);
        return (c, s) -> setter.set(s, x.evalf(c));
    }

    private static JsonObject wrap(String key, JsonElement v) {
        JsonObject o = new JsonObject();
        o.add(key, v);
        return o;
    }

    private static ImpactFrame impactStyle(JsonObject o, String w) {
        String mode = string(o, "mode", "two_tone").toUpperCase(Locale.ROOT);
        ImpactFrame.Mode m;
        try {
            m = ImpactFrame.Mode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new DataException("unknown impact mode '" + mode + "' at " + w);
        }
        ImpactFrame.Builder b = ImpactFrame.builder(m);
        if (o.has("paper")) b.paper(ColorSpec.parse(o.get("paper"), -1, w).literalOr(-1));
        if (o.has("ink")) b.ink(ColorSpec.parse(o.get("ink"), -1, w).literalOr(0xFF000000));
        if (o.has("highlight")) b.highlight(ColorSpec.parse(o.get("highlight"), -1, w).literalOr(-1));
        if (o.has("bias")) b.bias(o.get("bias").getAsFloat());
        if (o.has("edges")) b.edges(o.get("edges").getAsFloat());
        if (o.has("cell")) b.cell(o.get("cell").getAsFloat());
        if (o.has("glitch")) b.glitch(o.get("glitch").getAsFloat());
        if (o.has("spin")) b.spin(o.get("spin").getAsFloat());
        if (o.has("invert")) b.invert(o.get("invert").getAsBoolean());
        return b.build();
    }

    private static ScreenOp impact(JsonObject o, String w) {
        ImpactFrame style = impactStyle(o, w);
        Expr cond = when(o, w), time = Specs.expr(o, "time", 0, w);
        VecSpec at = o.has("at") ? VecSpec.parse(o.get("at"), w + ".at") : null;
        return (c, s) -> {
            if (cond != null && !cond.test(c)) return;
            float[] uv = c.focus(at);
            s.impact(style, time.evalf(c), uv[0], uv[1]);
        };
    }

    private static ScreenOp impactCycle(JsonObject o, String w) {
        List<ImpactFrame> frames = new ArrayList<>();
        int i = 0;
        for (JsonElement e : array(o.get("frames"))) frames.add(impactStyle(object(e, w + ".frames[" + i + "]"), w + ".frames[" + i++ + "]"));
        if (frames.isEmpty()) throw new DataException("impact_cycle needs frames at " + w);
        ImpactFrame[] arr = frames.toArray(new ImpactFrame[0]);
        Expr start = Specs.expr(o, "start", 0, w), duration = Specs.expr(o, "duration", 0.05, w);
        VecSpec at = o.has("at") ? VecSpec.parse(o.get("at"), w + ".at") : null;
        return (c, s) -> {
            float since = (c.t() - start.evalf(c)) / 20f;
            float[] uv = c.focus(at);
            s.impactCycle(since, duration.evalf(c), uv[0], uv[1], arr);
        };
    }

    // ---- environment

    private static List<EnvOp> environment(JsonObject o, String w) {
        List<EnvOp> ops = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey(), kw = w + "." + k;
            JsonObject c = object(e.getValue(), kw);
            Expr weight = Specs.expr(c, "weight", 1, kw);
            Expr cond = when(c, kw);
            EnvOp op = switch (k) {
                case "fog_color", "sky_color", "cloud_color", "sunrise_color", "sky_light_color", "ambient_light", "block_light_tint",
                     "night_vision_color", "water_fog_color", "fog_final_color" -> {
                    ColorSpec col = ColorSpec.parse(c.get("color"), -1, kw + ".color");
                    yield (ctx, env) -> {
                        int cc = col.eval(ctx, ctx.params());
                        float wt = weight.evalf(ctx);
                        switch (k) {
                            case "fog_color" -> env.fogColor(cc, wt);
                            case "sky_color" -> env.skyColor(cc, wt);
                            case "cloud_color" -> env.cloudColor(cc, wt);
                            case "sunrise_color" -> env.sunriseColor(cc, wt);
                            case "sky_light_color" -> env.skyLightColor(cc, wt);
                            case "ambient_light" -> env.ambientLight(cc, wt);
                            case "block_light_tint" -> env.blockLightTint(cc, wt);
                            case "night_vision_color" -> env.nightVisionColor(cc, wt);
                            case "water_fog_color" -> env.waterFogColor(cc, wt);
                            default -> env.fogFinalColor(cc, wt);
                        }
                    };
                }
                case "fog_distance", "fog", "water_fog_distance" -> {
                    Expr start = Specs.expr(c, "start", 0, kw), end = Specs.expr(c, "end", 64, kw);
                    yield (ctx, env) -> {
                        float st = start.evalf(ctx), en = end.evalf(ctx), wt = weight.evalf(ctx);
                        switch (k) {
                            case "fog_distance" -> env.fogDistance(st, en, wt);
                            case "water_fog_distance" -> env.waterFogDistance(st, en, wt);
                            default -> env.fog(st, en, wt);
                        }
                    };
                }
                case "sky_fog_end", "cloud_fog_end", "fog_sky_end", "fog_cloud_end", "sun_angle", "moon_angle", "star_angle", "star_brightness",
                     "cloud_height", "sky_light_factor", "rain", "thunder" -> {
                    Expr val = Specs.expr(c, "value", 0, kw);
                    yield (ctx, env) -> {
                        float v = val.evalf(ctx), wt = weight.evalf(ctx);
                        switch (k) {
                            case "sky_fog_end" -> env.skyFogEnd(v, wt);
                            case "cloud_fog_end" -> env.cloudFogEnd(v, wt);
                            case "fog_sky_end" -> env.fogSkyEnd(v, wt);
                            case "fog_cloud_end" -> env.fogCloudEnd(v, wt);
                            case "sun_angle" -> env.sunAngle(v, wt);
                            case "moon_angle" -> env.moonAngle(v, wt);
                            case "star_angle" -> env.starAngle(v, wt);
                            case "star_brightness" -> env.starBrightness(v, wt);
                            case "cloud_height" -> env.cloudHeight(v, wt);
                            case "sky_light_factor" -> env.skyLightFactor(v, wt);
                            case "rain" -> env.rain(v, wt);
                            default -> env.thunder(v, wt);
                        }
                    };
                }
                case "cloud_hole" -> {
                    Expr radius = Specs.expr(c, "radius", 32, kw), ragged = Specs.expr(c, "ragged", 0, kw);
                    VecSpec at = VecSpec.parse(c.get("at"), kw + ".at");
                    yield (ctx, env) -> {
                        Vec3 p = ctx.pos(at);
                        env.cloudHole(p.x, p.z, radius.eval(ctx), ragged.evalf(ctx), ctx.effect.seed());
                    };
                }
                default -> throw new DataException("unknown environment key '" + k + "' at " + w);
            };
            ops.add((ctx, env) -> {
                if (cond == null || cond.test(ctx)) op.apply(ctx, env);
            });
        }
        return ops;
    }

    // ---- camera and entity

    private static List<CameraOp> camera(JsonObject o, String w) {
        List<CameraOp> ops = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey(), kw = w + "." + k;
            if (k.equals("offset")) {
                VecSpec v = VecSpec.raw(e.getValue(), Vec3.ZERO, kw);
                ops.add((c, cam) -> cam.offset(c.vec(v)));
                continue;
            }
            Expr x = expr(e.getValue(), kw);
            ops.add(switch (k) {
                case "shake" -> (c, cam) -> cam.shake(x.evalf(c));
                case "shake_frequency" -> (c, cam) -> cam.shakeFrequency(x.evalf(c));
                case "roll" -> (c, cam) -> cam.roll(x.evalf(c));
                case "yaw" -> (c, cam) -> cam.rotate(x.evalf(c), 0);
                case "pitch" -> (c, cam) -> cam.rotate(0, x.evalf(c));
                case "fov_scale" -> (c, cam) -> cam.fovScale(x.evalf(c));
                case "fov" -> (c, cam) -> cam.fov(x.evalf(c));
                default -> throw new DataException("unknown camera key '" + k + "' at " + w);
            });
        }
        return ops;
    }

    private static EntityOp entity(JsonObject o, String w) {
        Expr cond = when(o, w);
        ColorSpec outline = o.has("outline") ? ColorSpec.parse(o.get("outline"), -1, w + ".outline") : null;
        Map<String, PoseSpec> poses = new LinkedHashMap<>();
        if (o.has("poses")) object(o.get("poses"), w + ".poses").entrySet().forEach(e -> {
            JsonObject p = object(e.getValue(), w + ".poses." + e.getKey());
            String pw = w + ".poses." + e.getKey();
            poses.put(e.getKey(), new PoseSpec(Specs.expr(p, "x_rot", 0, pw), Specs.expr(p, "y_rot", 0, pw), Specs.expr(p, "z_rot", 0, pw),
                Specs.expr(p, "weight", 1, pw), "set".equalsIgnoreCase(string(p, "mode", "add")), bool(p, "visible", true)));
        });
        return (c, e) -> {
            if (cond != null && !cond.test(c)) return;
            int id = c.effect.anchor().entityId();
            if (id < 0) return;
            if (outline != null) e.outline(id, outline.eval(c, c.params()) & 0xFFFFFF);
            poses.forEach((part, p) -> e.pose(id, part, new PartPose(p.x.evalf(c), p.y.evalf(c), p.z.evalf(c), 0, 0, 0, 1, p.visible,
                p.set ? PartPose.Mode.SET : PartPose.Mode.ADD, p.weight.evalf(c))));
        };
    }

    record PoseSpec(Expr x, Expr y, Expr z, Expr weight, boolean set, boolean visible) {
    }

    // ---- HUD

    private static final Pattern INTERPOLATION = Pattern.compile("\\{([^}:]+)(?::([^}]+))?}");

    interface TextFn {
        String text(DataEffect.Ctx c);
    }

    private static TextFn text(String template, String w) {
        Matcher m = INTERPOLATION.matcher(template);
        List<Object> parts = new ArrayList<>();
        int last = 0;
        while (m.find()) {
            parts.add(template.substring(last, m.start()));
            Expr e = expr(new JsonPrimitive(m.group(1)), w);
            String fmt = m.group(2);
            parts.add(new Object[]{e, fmt});
            last = m.end();
        }
        parts.add(template.substring(last));
        if (parts.size() == 1) return c -> template;
        return c -> {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof String s) sb.append(s);
                else {
                    Object[] a = (Object[]) p;
                    double v = ((Expr) a[0]).eval(c);
                    String fmt = (String) a[1];
                    if (fmt != null) sb.append(String.format(Locale.ROOT, fmt.contains("%") ? fmt : "%" + fmt, fmt.endsWith("d") ? (Object) Math.round(v) : (Object) v));
                    else sb.append(v == Math.rint(v) && Math.abs(v) < 1e9 ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v));
                }
            }
            return sb.toString();
        };
    }

    private static HudOp hud(JsonObject o, String w) {
        String type = string(o, "type", "text").toLowerCase(Locale.ROOT);
        Expr cond = when(o, w);
        Expr alpha = Specs.expr(o, "alpha", 1, w);
        ColorSpec color = ColorSpec.parse(o.get("color"), 0xFFFFFFFF, w + ".color");
        Expr x0 = Specs.expr(o, "x0", 0, w), y0 = Specs.expr(o, "y0", 0, w), x1 = Specs.expr(o, "x1", 0, w), y1 = Specs.expr(o, "y1", 0, w);
        Expr x = Specs.expr(o, "x", 0, w), y = Specs.expr(o, "y", 0, w), thickness = Specs.expr(o, "thickness", 1, w);
        HudOp op = switch (type) {
            case "text" -> {
                TextFn tf = text(string(o, "text", ""), w + ".text");
                Expr scale = Specs.expr(o, "scale", 1, w), spacing = o.has("spacing") ? expr(o.get("spacing"), w + ".spacing") : null;
                String align = string(o, "align", "left").toLowerCase(Locale.ROOT);
                ColorSpec halo = o.has("halo") ? ColorSpec.parse(o.get("halo"), 0, w + ".halo") : null;
                ColorSpec outline = o.has("outline") ? ColorSpec.parse(o.get("outline"), 0, w + ".outline") : null;
                boolean shadow = bool(o, "shadow", false);
                yield (c, cv) -> {
                    String s = tf.text(c);
                    float sc = scale.evalf(c), px = x.evalf(c), py = y.evalf(c);
                    int col = c.color(color, alpha);
                    if (spacing != null) {
                        cv.spaced(s, px, py, sc, spacing.evalf(c), col);
                        return;
                    }
                    if (halo != null) {
                        cv.glow(s, align.equals("center") ? px : px + cv.textWidth(s, sc) / 2, py, sc, col, c.color(halo, alpha), 1f);
                        return;
                    }
                    float tx = switch (align) {
                        case "center" -> px - cv.textWidth(s, sc) / 2;
                        case "right" -> px - cv.textWidth(s, sc);
                        default -> px;
                    };
                    if (outline != null) cv.outlined(s, tx, py, sc, col, c.color(outline, alpha));
                    else cv.text(s, tx, py, sc, col, shadow);
                };
            }
            case "rect" -> (c, cv) -> cv.rect(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), c.color(color, alpha));
            case "outline" -> (c, cv) -> cv.outline(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), thickness.evalf(c), c.color(color, alpha));
            case "line" -> (c, cv) -> cv.line(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), thickness.evalf(c), c.color(color, alpha));
            case "gradient" -> {
                ColorSpec bottom = ColorSpec.parse(o.get("color_bottom"), 0, w + ".color_bottom");
                yield (c, cv) -> cv.gradient(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), c.color(color, alpha), c.color(bottom, alpha));
            }
            case "corners" -> {
                Expr len = Specs.expr(o, "length", 8, w);
                yield (c, cv) -> cv.corners(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), len.evalf(c), thickness.evalf(c), c.color(color, alpha));
            }
            case "bar" -> {
                Expr fill = Specs.expr(o, "fill", 0, w);
                ColorSpec bg = ColorSpec.parse(o.get("background"), 0x40000000, w + ".background");
                yield (c, cv) -> cv.bar(x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), fill.evalf(c), c.color(bg, alpha), c.color(color, alpha));
            }
            case "image" -> {
                Identifier tex = Identifier.parse(string(o, "texture", FxTextures.SOFT.toString()));
                yield (c, cv) -> cv.image(tex, x0.evalf(c), y0.evalf(c), x1.evalf(c), y1.evalf(c), c.color(color, alpha));
            }
            case "marker" -> {
                VecSpec at = VecSpec.parse(o.get("at"), w + ".at");
                Expr size = Specs.expr(o, "size", 12, w), len = Specs.expr(o, "length", 4, w), margin = Specs.expr(o, "margin", 24, w);
                TextFn tf = o.has("text") ? text(string(o, "text", ""), w + ".text") : null;
                Expr scale = Specs.expr(o, "scale", 1, w);
                boolean edge = bool(o, "edge_arrow", true);
                yield (c, cv) -> {
                    Vector3f p = cv.project(c.pos(at));
                    Vector3f q = cv.clampToEdge(p, margin.evalf(c));
                    int col = c.color(color, alpha);
                    if (Float.isNaN(q.z)) {
                        cv.reticle(q.x, q.y, size.evalf(c), len.evalf(c), thickness.evalf(c), col);
                        if (tf != null) cv.textCentered(tf.text(c), q.x, q.y + size.evalf(c) + 3, scale.evalf(c), col);
                    } else if (edge) {
                        cv.arrow(q.x, q.y, q.z, size.evalf(c), thickness.evalf(c), col);
                    }
                };
            }
            default -> throw new DataException("unknown hud element '" + type + "' at " + w);
        };
        return (c, cv) -> {
            if (cond == null || cond.test(c)) op.draw(c, cv);
        };
    }

    // ---- geometry

    private static GeoOp geometry(JsonObject o, String w) {
        String type = string(o, "type", "billboard").toLowerCase(Locale.ROOT);
        String render = string(o, "render", o.has("texture") ? "additive_texture" : "additive").toLowerCase(Locale.ROOT);
        Identifier texture = Identifier.parse(string(o, "texture", FxTextures.SOFT.toString()));
        RenderTypeRef rt = () -> switch (render) {
            case "additive" -> FxRenderTypes.additive();
            case "additive_texture" -> FxRenderTypes.additive(texture);
            case "translucent" -> FxRenderTypes.translucent(texture);
            default -> throw new DataException("unknown render '" + render + "' at " + w);
        };
        Expr cond = when(o, w), alpha = Specs.expr(o, "alpha", 1, w);
        ColorSpec color = ColorSpec.parse(o.get("color"), 0xFFFFFFFF, w + ".color");
        ColorSpec colorEnd = o.has("color_end") ? ColorSpec.parse(o.get("color_end"), 0, w + ".color_end") : null;
        VecSpec at = VecSpec.parse(o.get("at"), w + ".at");
        Expr size = Specs.expr(o, "size", 1, w), rotation = Specs.expr(o, "rotation", 0, w), width = Specs.expr(o, "width", 0.2, w);
        Expr radius = Specs.expr(o, "radius", 1, w), inner = Specs.expr(o, "inner", 0.8, w), segments = Specs.expr(o, "segments", 48, w);
        VecSpec to = VecSpec.parse(o.get("to"), w + ".to");
        VecSpec normal = VecSpec.raw(o.get("normal"), new Vec3(0, 1, 0), w + ".normal");
        GeoOp op = switch (type) {
            case "billboard" -> (c, g) -> g.draw(rt.get(), m -> m.billboard(c.pos(at), size.evalf(c), rotation.evalf(c), c.color(color, alpha)));
            case "beam" -> (c, g) -> g.draw(rt.get(), m -> {
                int c0 = c.color(color, alpha);
                m.beam(c.pos(at), c.pos(to), width.evalf(c), c0, colorEnd != null ? c.color(colorEnd, alpha) : c0);
            });
            case "ring", "disc" -> (c, g) -> g.draw(rt.get(), m -> {
                int c0 = c.color(color, alpha), c1 = colorEnd != null ? c.color(colorEnd, alpha) : c0;
                float r = radius.evalf(c);
                m.ring(c.pos(at), c.vec(normal), type.equals("disc") ? 0 : inner.evalf(c), r, Math.max(3, segments.evalf(c) > 0 ? (int) segments.eval(c) : 48), c0, c1);
            });
            case "sphere" -> (c, g) -> g.draw(rt.get(), m -> m.sphere(c.pos(at), radius.evalf(c), Math.max(4, (int) segments.eval(c) / 2),
                Math.max(6, (int) segments.eval(c)), c.color(color, alpha)));
            case "cylinder" -> (c, g) -> g.draw(rt.get(), m -> {
                int c0 = c.color(color, alpha), c1 = colorEnd != null ? c.color(colorEnd, alpha) : c0;
                float r = radius.evalf(c);
                m.cylinder(c.pos(at), c.pos(to), r, r, Math.max(3, (int) segments.eval(c)), c0, c1);
            });
            default -> throw new DataException("unknown geometry '" + type + "' at " + w);
        };
        return (c, g) -> {
            if (cond == null || cond.test(c)) op.draw(c, g);
        };
    }

    interface RenderTypeRef {
        RenderType get();
    }

    // ------------------------------------------------------------------ particles from JSON

    /** Decodes a particle (string id or object) after replacing "$param" strings with parameter values. */
    static ParticleOptions decodeParticle(JsonElement json, FxParams params, String w) {
        JsonElement resolved = substitute(json.deepCopy(), params);
        if (resolved instanceof JsonPrimitive p && p.isString()) {      // "minecraft:end_rod" → {"type": "minecraft:end_rod"}
            JsonObject o = new JsonObject();
            o.add("type", p);
            resolved = o;
        }
        var level = Minecraft.getInstance().level;
        var ops = level != null ? RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()) : JsonOps.INSTANCE;
        return ParticleTypes.CODEC.parse(ops, resolved).getOrThrow(msg -> new DataException("bad particle at " + w + ": " + msg));
    }

    private static JsonElement substitute(JsonElement e, FxParams params) {
        if (e instanceof JsonPrimitive p && p.isString() && p.getAsString().startsWith("$")) {
            String key = p.getAsString().substring(1);
            Object raw = params.raw(key);
            if (raw instanceof Double d) return new JsonPrimitive(d);
            if (raw instanceof Boolean b) return new JsonPrimitive(b);
            if (raw instanceof String s) return new JsonPrimitive(s);
            if (raw instanceof double[] a) {
                if (a.length >= 3) return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", params.getColor(key, -1)));
                return new JsonPrimitive(a[0]);
            }
            return e;
        }
        if (e instanceof JsonObject o) {
            for (String k : List.copyOf(o.keySet())) o.add(k, substitute(o.get(k), params));
        } else if (e.isJsonArray()) {
            var a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) a.set(i, substitute(a.get(i), params));
        }
        return e;
    }
}
