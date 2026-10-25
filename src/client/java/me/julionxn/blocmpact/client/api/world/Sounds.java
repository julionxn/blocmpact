package me.julionxn.blocmpact.client.api.world;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.impl.EffectManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Level-0 sound helpers. Effect sounds default to the {@code MASTER} category so they keep playing while an effect
 * silences the world. The audible range of a positional sound is 16 × volume blocks (gain never exceeds 1, a higher
 * volume only reaches farther). Pitch 0.5–0.7 makes any vanilla sound feel bigger.
 */
public final class Sounds {
    private final Vec3 camera;
    private final RandomSource random;
    private SoundSource source = SoundSource.MASTER;

    public Sounds(Vec3 camera, RandomSource random) {
        this.camera = camera;
        this.random = random;
    }

    /** Category for the following sounds (default MASTER). */
    public Sounds category(SoundSource source) {
        this.source = source;
        return this;
    }

    // ---- immediate

    /** At a world position with linear falloff. */
    public SoundInstance at(Identifier sound, Vec3 pos, float volume, float pitch) {
        return play(new SimpleSoundInstance(sound, source, volume, pitch, random, false, 0, SoundInstance.Attenuation.LINEAR,
            pos.x, pos.y, pos.z, false));
    }

    public SoundInstance at(SoundEvent sound, Vec3 pos, float volume, float pitch) {
        return at(sound.location(), pos, volume, pitch);
    }

    public SoundInstance at(Holder<SoundEvent> sound, Vec3 pos, float volume, float pitch) {
        return at(sound.value(), pos, volume, pitch);
    }

    /** "In the head": same loudness everywhere (warnings, music stingers, hits that must land). */
    public SoundInstance head(Identifier sound, float volume, float pitch) {
        return play(new SimpleSoundInstance(sound, source, volume, pitch, random, false, 0, SoundInstance.Attenuation.NONE,
            0, 0, 0, true));
    }

    public SoundInstance head(SoundEvent sound, float volume, float pitch) {
        return head(sound.location(), volume, pitch);
    }

    public SoundInstance head(Holder<SoundEvent> sound, float volume, float pitch) {
        return head(sound.value(), volume, pitch);
    }

    /**
     * For a large zone: plays at the point of its edge nearest to the listener (or on the listener when inside), so
     * it surrounds you inside and comes from the right direction outside.
     */
    public SoundInstance edge(Identifier sound, Vec3 center, double radius, float volume, float pitch) {
        return at(sound, edgeToward(center, radius, camera), volume, pitch);
    }

    public SoundInstance edge(SoundEvent sound, Vec3 center, double radius, float volume, float pitch) {
        return edge(sound.location(), center, radius, volume, pitch);
    }

    public SoundInstance edge(Holder<SoundEvent> sound, Vec3 center, double radius, float volume, float pitch) {
        return edge(sound.value(), center, radius, volume, pitch);
    }

    // ---- delayed

    /** Plays after {@code gameTicks} real game ticks (not affected by the effect's speed). */
    public void later(int gameTicks, Runnable play) {
        EffectManager.INSTANCE.schedule(gameTicks, play);
    }

    /**
     * Light arrives instantly, sound at 17 blocks per tick: plays {@code sound} at {@code pos} when the sound of an
     * event happening now reaches the listener.
     */
    public void atSpeedOfSound(Identifier sound, Vec3 pos, float volume, float pitch) {
        int delay = Math.round(FxMath.soundArrival(0, camera.distanceTo(pos)));
        Vec3 p = pos;
        later(delay, () -> at(sound, p, volume, pitch));
    }

    public void atSpeedOfSound(SoundEvent sound, Vec3 pos, float volume, float pitch) {
        atSpeedOfSound(sound.location(), pos, volume, pitch);
    }

    public void atSpeedOfSound(Holder<SoundEvent> sound, Vec3 pos, float volume, float pitch) {
        atSpeedOfSound(sound.value().location(), pos, volume, pitch);
    }

    // ---- utilities

    public static SoundInstance play(SoundInstance instance) {
        Minecraft.getInstance().getSoundManager().play(instance);
        return instance;
    }

    public static void stop(SoundInstance instance) {
        Minecraft.getInstance().getSoundManager().stop(instance);
    }

    /** Point of a horizontal zone's edge nearest to {@code listener}; the height is clamped between the center and +80. */
    public static Vec3 edgeToward(Vec3 center, double radius, Vec3 listener) {
        double dx = listener.x - center.x, dz = listener.z - center.z, d = Math.sqrt(dx * dx + dz * dz);
        double y = Math.max(center.y, Math.min(listener.y, center.y + 80));
        return d <= radius ? new Vec3(listener.x, y, listener.z) : new Vec3(center.x + dx / d * radius, y, center.z + dz / d * radius);
    }
}
