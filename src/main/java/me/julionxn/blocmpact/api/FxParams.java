package me.julionxn.blocmpact.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable bag of named values that travels with an effect: from the server (or a command, or JSON) to every client.
 * <p>
 * There are only four kinds of value, so anything can be expressed without registering codecs:
 * numbers ({@code double}), booleans, strings and vectors ({@code double[]}, 1–4096 components).
 * Colors are accepted as packed {@code 0xAARRGGBB} numbers, {@code "#RRGGBB"}/{@code "#AARRGGBB"} strings
 * or 3/4-component vectors in 0–1 (see {@link #getColor}).
 */
public final class FxParams {
    public static final FxParams EMPTY = new FxParams(Map.of());
    private static final int MAX_ENTRIES = 256, MAX_VECTOR = 4096, MAX_STRING = 32767;

    private final Map<String, Object> values;

    private FxParams(Map<String, Object> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.values.putAll(values);
        return b;
    }

    // ---- reading

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Raw value: {@link Double}, {@link Boolean}, {@link String} or {@code double[]} (do not modify it). */
    public Object raw(String key) {
        return values.get(key);
    }

    public double getDouble(String key, double fallback) {
        Object v = values.get(key);
        if (v instanceof Double d) return d;
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof double[] a && a.length > 0) return a[0];
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public float getFloat(String key, float fallback) {
        return (float) getDouble(key, fallback);
    }

    public int getInt(String key, int fallback) {
        return (int) Math.round(getDouble(key, fallback));
    }

    public long getLong(String key, long fallback) {
        return Math.round(getDouble(key, fallback));
    }

    public boolean getBool(String key, boolean fallback) {
        Object v = values.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Double d) return d != 0;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return fallback;
    }

    public String getString(String key, String fallback) {
        Object v = values.get(key);
        if (v instanceof String s) return s;
        if (v instanceof Double d) return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue()) : d.toString();
        if (v instanceof Boolean b) return b.toString();
        return fallback;
    }

    /** Copy of a vector value, or {@code fallback} if missing. A plain number becomes a 1-component vector. */
    public double[] getVec(String key, double[] fallback) {
        Object v = values.get(key);
        if (v instanceof double[] a) return a.clone();
        if (v instanceof Double d) return new double[]{d};
        return fallback;
    }

    public Vec3 getVec3(String key, Vec3 fallback) {
        Object v = values.get(key);
        if (v instanceof double[] a) {
            if (a.length >= 3) return new Vec3(a[0], a[1], a[2]);
            if (a.length == 1) return new Vec3(a[0], a[0], a[0]);
        }
        if (v instanceof Double d) return new Vec3(d, d, d);
        return fallback;
    }

    /**
     * Color as {@code 0xAARRGGBB}. Accepts a packed number (alpha 0 is treated as opaque when the value fits in 24 bits),
     * a hex string ({@code "#RGB"}, {@code "#RRGGBB"}, {@code "#AARRGGBB"}) or a vector of 3–4 components in 0–1.
     */
    public int getColor(String key, int fallback) {
        Object v = values.get(key);
        if (v instanceof Double d) {
            long c = d.longValue();
            return (c & 0xFF000000L) == 0 ? (int) (c | 0xFF000000L) : (int) c;
        }
        if (v instanceof String s) return parseColor(s, fallback);
        if (v instanceof double[] a && a.length >= 3) {
            double alpha = a.length >= 4 ? a[3] : 1;
            return FxMath.argb((float) alpha, (float) a[0], (float) a[1], (float) a[2]);
        }
        return fallback;
    }

    /** Parses {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB} (with or without {@code #}, also {@code 0x}). */
    public static int parseColor(String s, int fallback) {
        Integer c = tryParseColor(s);
        return c != null ? c : fallback;
    }

    /** Like {@link #parseColor(String, int)} but returns null when the text is not a color. */
    public static Integer tryParseColor(String s) {
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        else if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        try {
            if (h.length() == 3) {
                int r = Integer.parseInt(h.substring(0, 1), 16), g = Integer.parseInt(h.substring(1, 2), 16), b = Integer.parseInt(h.substring(2, 3), 16);
                return 0xFF000000 | (r * 17) << 16 | (g * 17) << 8 | b * 17;
            }
            if (h.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // ---- combining

    /** New params with {@code other}'s entries on top of these. */
    public FxParams merge(FxParams other) {
        if (other.isEmpty()) return this;
        if (isEmpty()) return other;
        Map<String, Object> m = new LinkedHashMap<>(values);
        m.putAll(other.values);
        return new FxParams(Collections.unmodifiableMap(m));
    }

    /** New params with defaults filled in where these have no value. */
    public FxParams withDefaults(FxParams defaults) {
        return defaults.merge(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        values.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(", ");
            sb.append(k).append('=');
            if (v instanceof double[] a) sb.append(java.util.Arrays.toString(a));
            else if (v instanceof String s) sb.append('"').append(s).append('"');
            else sb.append(v);
        });
        return sb.append('}').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FxParams p) || p.values.size() != values.size()) return false;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object a = e.getValue(), b = p.values.get(e.getKey());
            if (a instanceof double[] x && b instanceof double[] y) {
                if (!java.util.Arrays.equals(x, y)) return false;
            } else if (!a.equals(b)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return values.keySet().hashCode();
    }

    // ---- conversions

    /** Numbers, strings, booleans (as bytes) and numeric lists become params; nested compounds are ignored. */
    public static FxParams fromNbt(CompoundTag tag) {
        Builder b = builder();
        for (String key : tag.keySet()) {
            Tag t = tag.get(key);
            if (t instanceof NumericTag n) b.put(key, n.box().doubleValue());
            else if (t instanceof StringTag) t.asString().ifPresent(s -> b.put(key, s));
            else if (t instanceof CollectionTag list) {
                double[] a = new double[list.size()];
                boolean numeric = true;
                for (int i = 0; i < a.length; i++) {
                    if (list.get(i) instanceof NumericTag n) a[i] = n.box().doubleValue();
                    else numeric = false;
                }
                if (numeric && a.length > 0) b.put(key, a);
            }
        }
        return b.build();
    }

    /** Numbers, strings, booleans and arrays of numbers become params; nested objects are ignored. */
    public static FxParams fromJson(JsonObject json) {
        Builder b = builder();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            JsonElement v = e.getValue();
            if (v instanceof JsonPrimitive p) {
                if (p.isBoolean()) b.put(e.getKey(), p.getAsBoolean());
                else if (p.isNumber()) b.put(e.getKey(), p.getAsDouble());
                else b.put(e.getKey(), p.getAsString());
            } else if (v instanceof JsonArray arr) {
                double[] a = new double[arr.size()];
                boolean numeric = true;
                for (int i = 0; i < a.length; i++) {
                    JsonElement x = arr.get(i);
                    if (x instanceof JsonPrimitive p && p.isNumber()) a[i] = p.getAsDouble();
                    else numeric = false;
                }
                if (numeric && a.length > 0) b.put(e.getKey(), a);
            }
        }
        return b.build();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        values.forEach((k, v) -> {
            if (v instanceof Double d) o.addProperty(k, d);
            else if (v instanceof Boolean bo) o.addProperty(k, bo);
            else if (v instanceof String s) o.addProperty(k, s);
            else if (v instanceof double[] a) {
                JsonArray arr = new JsonArray();
                for (double x : a) arr.add(x);
                o.add(k, arr);
            }
        });
        return o;
    }

    // ---- network

    public static final StreamCodec<FriendlyByteBuf, FxParams> STREAM_CODEC = StreamCodec.of(FxParams::write, FxParams::read);

    private static void write(FriendlyByteBuf buf, FxParams p) {
        buf.writeVarInt(p.values.size());
        p.values.forEach((k, v) -> {
            buf.writeUtf(k, 256);
            switch (v) {
                case Double d -> {
                    buf.writeByte(0);
                    buf.writeDouble(d);
                }
                case Boolean b -> {
                    buf.writeByte(1);
                    buf.writeBoolean(b);
                }
                case String s -> {
                    buf.writeByte(2);
                    buf.writeUtf(s, MAX_STRING);
                }
                case double[] a -> {
                    buf.writeByte(3);
                    buf.writeVarInt(a.length);
                    for (double x : a) buf.writeDouble(x);
                }
                default -> throw new IllegalStateException("unsupported param " + k);
            }
        });
    }

    private static FxParams read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) throw new DecoderException("too many effect params: " + n);
        if (n == 0) return EMPTY;
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(256);
            byte type = buf.readByte();
            switch (type) {
                case 0 -> m.put(k, buf.readDouble());
                case 1 -> m.put(k, buf.readBoolean());
                case 2 -> m.put(k, buf.readUtf(MAX_STRING));
                case 3 -> {
                    int len = buf.readVarInt();
                    if (len < 0 || len > MAX_VECTOR) throw new DecoderException("effect vector too long: " + len);
                    double[] a = new double[len];
                    for (int j = 0; j < len; j++) a[j] = buf.readDouble();
                    m.put(k, a);
                }
                default -> throw new DecoderException("unknown param type " + type);
            }
        }
        return new FxParams(Collections.unmodifiableMap(m));
    }

    // ---- builder

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder set(String key, Object value) {
            if (values.size() >= MAX_ENTRIES && !values.containsKey(key)) throw new IllegalStateException("too many params");
            values.put(key, value);
            return this;
        }

        public Builder put(String key, double v) {
            return set(key, v);
        }

        public Builder put(String key, boolean v) {
            return set(key, v);
        }

        public Builder put(String key, String v) {
            return set(key, v);
        }

        public Builder put(String key, double... v) {
            if (v.length == 0 || v.length > MAX_VECTOR) throw new IllegalArgumentException("vector size " + v.length);
            return set(key, v.clone());
        }

        public Builder put(String key, Vec3 v) {
            return set(key, new double[]{v.x, v.y, v.z});
        }

        /** Stores a packed {@code 0xAARRGGBB} color as a 4-component vector in 0–1. */
        public Builder color(String key, int argb) {
            return set(key, new double[]{(argb >> 16 & 255) / 255.0, (argb >> 8 & 255) / 255.0, (argb & 255) / 255.0, (argb >>> 24) / 255.0});
        }

        public Builder putAll(FxParams other) {
            other.values.forEach(this::set);
            return this;
        }

        public Builder remove(String key) {
            values.remove(key);
            return this;
        }

        public FxParams build() {
            return values.isEmpty() ? EMPTY : new FxParams(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        }
    }
}
