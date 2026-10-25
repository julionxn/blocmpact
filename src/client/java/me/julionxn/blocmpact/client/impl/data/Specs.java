package me.julionxn.blocmpact.client.impl.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.api.FxParams;
import me.julionxn.blocmpact.client.api.data.Expr;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Small value specs used by data-driven effects: numbers, vectors and colors that may be expressions. */
final class Specs {
    private Specs() {
    }

    static final class DataException extends RuntimeException {
        DataException(String msg) {
            super(msg);
        }
    }

    // ---- numbers

    static Expr expr(JsonElement e, String where) {
        if (e == null || e.isJsonNull()) throw new DataException("missing value at " + where);
        if (e instanceof JsonPrimitive p) {
            if (p.isNumber()) return Expr.constant(p.getAsDouble());
            if (p.isBoolean()) return Expr.constant(p.getAsBoolean() ? 1 : 0);
            try {
                return Expr.parse(p.getAsString());
            } catch (IllegalArgumentException ex) {
                throw new DataException(where + ": " + ex.getMessage());
            }
        }
        throw new DataException("expected a number or an expression at " + where);
    }

    static Expr expr(JsonObject o, String key, double fallback, String where) {
        JsonElement e = o.get(key);
        return e == null ? Expr.constant(fallback) : expr(e, where + "." + key);
    }

    /** Optional condition (null = always). */
    static Expr when(JsonObject o, String where) {
        JsonElement e = o.get("when");
        return e == null ? null : expr(e, where + ".when");
    }

    static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e instanceof JsonPrimitive p ? p.getAsString() : fallback;
    }

    static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e instanceof JsonPrimitive p ? p.getAsBoolean() : fallback;
    }

    static JsonObject object(JsonElement e, String where) {
        if (e instanceof JsonObject o) return o;
        throw new DataException("expected an object at " + where);
    }

    static JsonArray array(JsonElement e) {
        if (e instanceof JsonArray a) return a;
        JsonArray a = new JsonArray();
        if (e != null && !e.isJsonNull()) a.add(e);
        return a;
    }

    // ---- vectors

    /** A position: "origin", "camera", [dx,dy,dz] offset from the origin, {"abs": [...]}, {"param": name}, {"offset": [...], "from": ...}. */
    record VecSpec(Kind kind, Expr x, Expr y, Expr z, String param, VecSpec base) {
        enum Kind {ORIGIN, CAMERA, OFFSET, ABS, PARAM, RAW}

        static final VecSpec ORIGIN = new VecSpec(Kind.ORIGIN, null, null, null, null, null);

        static VecSpec parse(JsonElement e, String where) {
            if (e == null || e.isJsonNull()) return ORIGIN;
            if (e instanceof JsonPrimitive p && p.isString()) {
                String s = p.getAsString().toLowerCase(Locale.ROOT);
                return switch (s) {
                    case "origin" -> ORIGIN;
                    case "camera" -> new VecSpec(Kind.CAMERA, null, null, null, null, null);
                    default -> new VecSpec(Kind.PARAM, null, null, null, p.getAsString(), null);
                };
            }
            if (e instanceof JsonArray a) {
                Expr[] c = triple(a, where);
                return new VecSpec(Kind.OFFSET, c[0], c[1], c[2], null, ORIGIN);
            }
            JsonObject o = object(e, where);
            if (o.has("abs")) {
                Expr[] c = triple(array(o.get("abs")), where + ".abs");
                return new VecSpec(Kind.ABS, c[0], c[1], c[2], null, null);
            }
            if (o.has("param")) return new VecSpec(Kind.PARAM, null, null, null, string(o, "param", ""), null);
            if (o.has("offset")) {
                Expr[] c = triple(array(o.get("offset")), where + ".offset");
                return new VecSpec(Kind.OFFSET, c[0], c[1], c[2], null, parse(o.get("from"), where + ".from"));
            }
            throw new DataException("bad position at " + where);
        }

        /** A plain vector (directions, sizes): [x,y,z] expressions or a vec param name. */
        static VecSpec raw(JsonElement e, Vec3 fallback, String where) {
            if (e == null || e.isJsonNull())
                return new VecSpec(Kind.RAW, Expr.constant(fallback.x), Expr.constant(fallback.y), Expr.constant(fallback.z), null, null);
            if (e instanceof JsonPrimitive p && p.isString()) return new VecSpec(Kind.PARAM, null, null, null, p.getAsString(), null);
            Expr[] c = triple(array(e), where);
            return new VecSpec(Kind.RAW, c[0], c[1], c[2], null, null);
        }

        private static Expr[] triple(JsonArray a, String where) {
            if (a.size() != 3) throw new DataException("expected 3 components at " + where);
            return new Expr[]{expr(a.get(0), where + "[0]"), expr(a.get(1), where + "[1]"), expr(a.get(2), where + "[2]")};
        }

        Vec3 eval(Expr.Env env, Vec3 origin, Vec3 camera, FxParams params) {
            return switch (kind) {
                case ORIGIN -> origin;
                case CAMERA -> camera;
                case ABS, RAW -> new Vec3(x.eval(env), y.eval(env), z.eval(env));
                case PARAM -> params.getVec3(param, origin);
                case OFFSET -> base.eval(env, origin, camera, params).add(x.eval(env), y.eval(env), z.eval(env));
            };
        }
    }

    // ---- colors

    /** "#hex", a color param name, [r,g,b(,a)] expressions in 0–1, or a packed number. */
    record ColorSpec(Integer literal, String param, Expr r, Expr g, Expr b, Expr a) {
        static ColorSpec parse(JsonElement e, int fallback, String where) {
            if (e == null || e.isJsonNull()) return new ColorSpec(fallback, null, null, null, null, null);
            if (e instanceof JsonPrimitive p) {
                if (p.isNumber()) {
                    long c = p.getAsLong();
                    return new ColorSpec((c & 0xFF000000L) == 0 ? (int) (c | 0xFF000000L) : (int) c, null, null, null, null, null);
                }
                String s = p.getAsString();
                if (s.startsWith("#")) {
                    Integer c = FxParams.tryParseColor(s);
                    if (c == null) throw new DataException("bad color " + s + " at " + where);
                    return new ColorSpec(c, null, null, null, null, null);
                }
                return new ColorSpec(null, s, null, null, null, null);
            }
            JsonArray a = array(e);
            if (a.size() < 3) throw new DataException("expected [r, g, b] or [r, g, b, a] at " + where);
            return new ColorSpec(null, null, expr(a.get(0), where), expr(a.get(1), where), expr(a.get(2), where),
                a.size() > 3 ? expr(a.get(3), where) : Expr.constant(1));
        }

        /** The literal color, or {@code fallback} when the color depends on params or expressions. */
        int literalOr(int fallback) {
            return literal != null ? literal : fallback;
        }

        int eval(Expr.Env env, FxParams params) {
            if (literal != null) return literal;
            if (param != null) return params.getColor(param, 0xFFFFFFFF);
            return FxMath.argb(a.evalf(env), r.evalf(env), g.evalf(env), b.evalf(env));
        }
    }
}
