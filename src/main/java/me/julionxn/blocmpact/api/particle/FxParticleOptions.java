package me.julionxn.blocmpact.api.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.julionxn.blocmpact.api.FxParams;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * Options of a configurable particle: every visual property is data, so one particle type covers sparks, embers,
 * smoke, motes, snow, bubbles... without registering anything. Built with {@link #builder(ParticleType)} or
 * {@link FxParticles#options()}; usable in {@code level.addParticle}, {@code /particle} and JSON.
 *
 * @param colorStart  ARGB at birth
 * @param colorEnd    ARGB at death (interpolated with {@code curve})
 * @param sizeStart   half size of the quad in blocks at birth (vanilla particles are about 0.1)
 * @param sizeEnd     half size at death
 * @param lifetime    ticks
 * @param jitter      0–1 random variation applied to lifetime and size
 * @param gravity     blocks per tick² pulled down (negative floats up)
 * @param drag        velocity kept each tick (1 = no drag, vanilla ≈ 0.98)
 * @param spin        roll speed in radians per tick (sign chosen at random when {@code jitter > 0})
 * @param blend       how it is blended with the scene
 * @param emissive    ignores world light (full bright)
 * @param collide     collides with blocks
 * @param facing      orientation of the quad
 * @param sprite      which sprite of the type's sprite set to use
 * @param spriteIndex sprite index for {@link Sprite#INDEX}
 * @param curve       easing of the color and size interpolation
 * @param fadeIn      fraction of the life spent fading in from transparent (0 = pops in)
 */
public record FxParticleOptions(ParticleType<FxParticleOptions> type, int colorStart, int colorEnd, float sizeStart, float sizeEnd,
                                int lifetime, float jitter, float gravity, float drag, float spin, Blend blend, boolean emissive,
                                boolean collide, Facing facing, Sprite sprite, int spriteIndex, Curve curve, float fadeIn)
    implements ParticleOptions {

    public enum Blend {TRANSLUCENT, ADDITIVE, OPAQUE}

    public enum Facing {
        /** Always faces the camera. */
        CAMERA,
        /** Rotates only around the vertical axis (rain, flames, columns). */
        VERTICAL
    }

    public enum Sprite {
        /** Uses {@code spriteIndex}. */
        INDEX,
        /** A random sprite of the set per particle. */
        RANDOM,
        /** Walks the sprite set from first to last over the particle's life. */
        ANIMATE
    }

    public enum Curve {LINEAR, EASE_IN, EASE_OUT, SMOOTH}

    @Override
    public ParticleType<FxParticleOptions> getType() {
        return type;
    }

    public static Builder builder(ParticleType<FxParticleOptions> type) {
        return new Builder(type);
    }

    public Builder toBuilder() {
        Builder b = new Builder(type);
        b.colorStart = colorStart;
        b.colorEnd = colorEnd;
        b.sizeStart = sizeStart;
        b.sizeEnd = sizeEnd;
        b.lifetime = lifetime;
        b.jitter = jitter;
        b.gravity = gravity;
        b.drag = drag;
        b.spin = spin;
        b.blend = blend;
        b.emissive = emissive;
        b.collide = collide;
        b.facing = facing;
        b.sprite = sprite;
        b.spriteIndex = spriteIndex;
        b.curve = curve;
        b.fadeIn = fadeIn;
        return b;
    }

    // ---- codecs

    /** Color as an int or a {@code "#RRGGBB"} / {@code "#AARRGGBB"} string. */
    public static final Codec<Integer> COLOR = Codec.withAlternative(Codec.INT,
        Codec.STRING.comapFlatMap(s -> {
            Integer c = FxParams.tryParseColor(s);
            return c == null ? DataResult.error(() -> "bad color " + s) : DataResult.success(c);
        }, c -> String.format(Locale.ROOT, "#%08X", c)));

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        E[] values = type.getEnumConstants();
        return Codec.STRING.comapFlatMap(s -> {
            for (E e : values) if (e.name().equalsIgnoreCase(s)) return DataResult.success(e);
            return DataResult.error(() -> "unknown " + type.getSimpleName() + " " + s);
        }, e -> e.name().toLowerCase(Locale.ROOT));
    }

    private static final Codec<Blend> BLEND = enumCodec(Blend.class);
    private static final Codec<Facing> FACING = enumCodec(Facing.class);
    private static final Codec<Sprite> SPRITE = enumCodec(Sprite.class);
    private static final Codec<Curve> CURVE = enumCodec(Curve.class);

    public static MapCodec<FxParticleOptions> codec(ParticleType<FxParticleOptions> type) {
        Builder d = new Builder(type);
        return RecordCodecBuilder.mapCodec(i -> i.group(
            COLOR.optionalFieldOf("color", d.colorStart).forGetter(FxParticleOptions::colorStart),
            COLOR.optionalFieldOf("color_end").forGetter(o -> java.util.Optional.of(o.colorEnd)),
            Codec.FLOAT.optionalFieldOf("size", d.sizeStart).forGetter(FxParticleOptions::sizeStart),
            Codec.FLOAT.optionalFieldOf("size_end").forGetter(o -> java.util.Optional.of(o.sizeEnd)),
            Codec.INT.optionalFieldOf("lifetime", d.lifetime).forGetter(FxParticleOptions::lifetime),
            Codec.FLOAT.optionalFieldOf("jitter", d.jitter).forGetter(FxParticleOptions::jitter),
            Codec.FLOAT.optionalFieldOf("gravity", d.gravity).forGetter(FxParticleOptions::gravity),
            Codec.FLOAT.optionalFieldOf("drag", d.drag).forGetter(FxParticleOptions::drag),
            Codec.FLOAT.optionalFieldOf("spin", d.spin).forGetter(FxParticleOptions::spin),
            BLEND.optionalFieldOf("blend", d.blend).forGetter(FxParticleOptions::blend),
            Codec.BOOL.optionalFieldOf("emissive", d.emissive).forGetter(FxParticleOptions::emissive),
            Codec.BOOL.optionalFieldOf("collide", d.collide).forGetter(FxParticleOptions::collide),
            FACING.optionalFieldOf("facing", d.facing).forGetter(FxParticleOptions::facing),
            SPRITE.optionalFieldOf("sprite", d.sprite).forGetter(FxParticleOptions::sprite),
            Codec.INT.optionalFieldOf("sprite_index", d.spriteIndex).forGetter(FxParticleOptions::spriteIndex),
            RecordCodecBuilder.<Tail>mapCodec(j -> j.group(
                CURVE.optionalFieldOf("curve", d.curve).forGetter(Tail::curve),
                Codec.FLOAT.optionalFieldOf("fade_in", d.fadeIn).forGetter(Tail::fadeIn)
            ).apply(j, Tail::new)).forGetter(o -> new Tail(o.curve, o.fadeIn))
        ).apply(i, (color, colorEnd, size, sizeEnd, lifetime, jitter, gravity, drag, spin, blend, emissive, collide, facing, sprite, spriteIndex, tail) ->
            new FxParticleOptions(type, color, colorEnd.orElse(color), size, sizeEnd.orElse(size), Math.max(1, lifetime), jitter, gravity, drag,
                spin, blend, emissive, collide, facing, sprite, spriteIndex, tail.curve, tail.fadeIn)));
    }

    private record Tail(Curve curve, float fadeIn) {
    }

    public static StreamCodec<RegistryFriendlyByteBuf, FxParticleOptions> streamCodec(ParticleType<FxParticleOptions> type) {
        return StreamCodec.of((buf, o) -> {
            buf.writeInt(o.colorStart);
            buf.writeInt(o.colorEnd);
            buf.writeFloat(o.sizeStart);
            buf.writeFloat(o.sizeEnd);
            buf.writeVarInt(o.lifetime);
            buf.writeFloat(o.jitter);
            buf.writeFloat(o.gravity);
            buf.writeFloat(o.drag);
            buf.writeFloat(o.spin);
            buf.writeByte(o.blend.ordinal());
            buf.writeByte((o.emissive ? 1 : 0) | (o.collide ? 2 : 0));
            buf.writeByte(o.facing.ordinal());
            buf.writeByte(o.sprite.ordinal());
            buf.writeVarInt(o.spriteIndex);
            buf.writeByte(o.curve.ordinal());
            buf.writeFloat(o.fadeIn);
        }, buf -> {
            int c0 = buf.readInt(), c1 = buf.readInt();
            float s0 = buf.readFloat(), s1 = buf.readFloat();
            int life = buf.readVarInt();
            float jitter = buf.readFloat(), gravity = buf.readFloat(), drag = buf.readFloat(), spin = buf.readFloat();
            Blend blend = pick(Blend.values(), buf.readByte());
            int flags = buf.readByte();
            Facing facing = pick(Facing.values(), buf.readByte());
            Sprite sprite = pick(Sprite.values(), buf.readByte());
            int index = buf.readVarInt();
            Curve curve = pick(Curve.values(), buf.readByte());
            float fadeIn = buf.readFloat();
            return new FxParticleOptions(type, c0, c1, s0, s1, Math.max(1, life), jitter, gravity, drag, spin, blend,
                (flags & 1) != 0, (flags & 2) != 0, facing, sprite, index, curve, fadeIn);
        });
    }

    private static <E> E pick(E[] values, int ordinal) {
        return values[Math.floorMod(ordinal, values.length)];
    }

    // ---- builder

    public static final class Builder {
        private final ParticleType<FxParticleOptions> type;
        private int colorStart = 0xFFFFFFFF, colorEnd = 0x00FFFFFF;
        private float sizeStart = 0.1f, sizeEnd = 0.1f;
        private int lifetime = 20;
        private float jitter = 0.2f, gravity = 0f, drag = 0.96f, spin = 0f;
        private Blend blend = Blend.TRANSLUCENT;
        private boolean emissive = true, collide = false;
        private Facing facing = Facing.CAMERA;
        private Sprite sprite = Sprite.INDEX;
        private int spriteIndex = 0;
        private Curve curve = Curve.LINEAR;
        private float fadeIn = 0f;
        private boolean endColorSet;

        private Builder(ParticleType<FxParticleOptions> type) {
            this.type = type;
        }

        /** Same color for the whole life, fading out to transparent unless {@link #colorEnd} is set. */
        public Builder color(int argb) {
            this.colorStart = argb;
            if (!endColorSet) this.colorEnd = argb & 0x00FFFFFF;
            return this;
        }

        public Builder colorEnd(int argb) {
            this.colorEnd = argb;
            this.endColorSet = true;
            return this;
        }

        public Builder colors(int start, int end) {
            return color(start).colorEnd(end);
        }

        public Builder size(float size) {
            this.sizeStart = size;
            this.sizeEnd = size;
            return this;
        }

        public Builder size(float start, float end) {
            this.sizeStart = start;
            this.sizeEnd = end;
            return this;
        }

        public Builder lifetime(int ticks) {
            this.lifetime = Math.max(1, ticks);
            return this;
        }

        public Builder jitter(float amount) {
            this.jitter = amount;
            return this;
        }

        public Builder gravity(float g) {
            this.gravity = g;
            return this;
        }

        public Builder drag(float keptPerTick) {
            this.drag = keptPerTick;
            return this;
        }

        public Builder spin(float radiansPerTick) {
            this.spin = radiansPerTick;
            return this;
        }

        public Builder blend(Blend blend) {
            this.blend = blend;
            return this;
        }

        public Builder additive() {
            return blend(Blend.ADDITIVE);
        }

        public Builder emissive(boolean emissive) {
            this.emissive = emissive;
            return this;
        }

        public Builder collide(boolean collide) {
            this.collide = collide;
            return this;
        }

        public Builder facing(Facing facing) {
            this.facing = facing;
            return this;
        }

        public Builder sprite(int index) {
            this.sprite = Sprite.INDEX;
            this.spriteIndex = index;
            return this;
        }

        public Builder sprite(Sprite mode) {
            this.sprite = mode;
            return this;
        }

        public Builder curve(Curve curve) {
            this.curve = curve;
            return this;
        }

        public Builder fadeIn(float fraction) {
            this.fadeIn = fraction;
            return this;
        }

        public FxParticleOptions build() {
            return new FxParticleOptions(type, colorStart, colorEnd, sizeStart, sizeEnd, lifetime, jitter, gravity, drag, spin, blend,
                emissive, collide, facing, sprite, spriteIndex, curve, fadeIn);
        }
    }
}
