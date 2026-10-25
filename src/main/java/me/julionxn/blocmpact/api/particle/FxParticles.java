package me.julionxn.blocmpact.api.particle;

import me.julionxn.blocmpact.Blocmpact;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configurable particle types. {@link #FX} ships with neutral primitive sprites
 * (see {@code assets/blocmpact/particles/fx.json}); register your own type to use your own sprite set with the same
 * behavior: call {@link #register} from your {@code ModInitializer} and add {@code assets/<ns>/particles/<path>.json}.
 * Blocmpact registers the client provider automatically.
 */
public final class FxParticles {
    private static final List<ParticleType<FxParticleOptions>> TYPES = new ArrayList<>();

    /**
     * Sprites of {@code blocmpact:fx}, by index: 0 soft dot, 1 hard dot, 2 ring, 3 soft square, 4 horizontal streak,
     * 5 cross, 6 noise puff, 7 thin ring.
     */
    public static final ParticleType<FxParticleOptions> FX = register(Blocmpact.id("fx"));

    public static final int SOFT = 0, DOT = 1, RING = 2, SQUARE = 3, STREAK = 4, CROSS = 5, PUFF = 6, THIN_RING = 7;

    private FxParticles() {
    }

    /** Registers a configurable particle type with its own sprite set. Call during common initialization. */
    public static ParticleType<FxParticleOptions> register(Identifier id) {
        ParticleType<FxParticleOptions> type = FabricParticleTypes.complex(true, FxParticleOptions::codec, FxParticleOptions::streamCodec);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type);
        synchronized (TYPES) {
            TYPES.add(type);
        }
        return type;
    }

    /** Options builder for {@link #FX}. */
    public static FxParticleOptions.Builder options() {
        return FxParticleOptions.builder(FX);
    }

    /** Options builder for one of your registered types. */
    public static FxParticleOptions.Builder options(ParticleType<FxParticleOptions> type) {
        return FxParticleOptions.builder(type);
    }

    public static List<ParticleType<FxParticleOptions>> types() {
        synchronized (TYPES) {
            return Collections.unmodifiableList(new ArrayList<>(TYPES));
        }
    }

    /** Forces class loading (registers {@link #FX}). */
    public static void bootstrap() {
    }
}
