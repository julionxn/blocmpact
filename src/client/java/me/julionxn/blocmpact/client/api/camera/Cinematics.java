package me.julionxn.blocmpact.client.api.camera;

import me.julionxn.blocmpact.client.impl.camera.CinematicDirector;

/** Plays {@link Cutscene}s on the local player's camera. */
public final class Cinematics {
    private Cinematics() {
    }

    /** Starts a cutscene. Returns false if a higher-priority one is playing or cinematics are disabled in the config. */
    public static boolean play(Cutscene cutscene) {
        return CinematicDirector.INSTANCE.play(cutscene);
    }

    /** Fades the current cutscene out (skip). */
    public static void skip() {
        CinematicDirector.INSTANCE.skip();
    }

    /** Stops immediately. */
    public static void stop() {
        CinematicDirector.INSTANCE.stop();
    }

    public static boolean active() {
        return CinematicDirector.INSTANCE.active();
    }

    /** True while {@code cutscene} is the one playing. */
    public static boolean playing(Cutscene cutscene) {
        return CinematicDirector.INSTANCE.current() == cutscene;
    }

    /** Current blend weight with the player's camera (0–1). */
    public static float weight() {
        return CinematicDirector.INSTANCE.weight();
    }

    /** Current cutscene time in ticks, or NaN. */
    public static float time() {
        return CinematicDirector.INSTANCE.time();
    }
}
