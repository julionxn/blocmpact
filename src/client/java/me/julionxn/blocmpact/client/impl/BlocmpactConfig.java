package me.julionxn.blocmpact.client.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.julionxn.blocmpact.Blocmpact;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player settings in {@code config/blocmpact.json}. Accessibility options scale or disable flashes, shakes,
 * impact frames and held drawings for every effect, whoever wrote it.
 */
public final class BlocmpactConfig {
    /** Full-screen shader effects (level 3). Off = effects degrade to particles, sound, HUD and geometry. */
    public boolean postProcessing = true;
    /** Multiplier for flashes and fades towards white (0 disables them). */
    public float flashScale = 1f;
    /** Multiplier for screen shake and camera shake (0 disables them). */
    public float shakeScale = 1f;
    /** Graphic impact frames (fast full-screen changes). */
    public boolean impactFrames = true;
    /** Held drawings ("animation on twos"). */
    public boolean heldFrames = true;
    /** Cutscenes that take over the camera. */
    public boolean cinematics = true;
    /** Multiplier for particle counts that go through the LOD helper. */
    public float particleDensity = 1f;
    /** Maximum simultaneous effects; the oldest is dropped. */
    public int maxEffects = 32;
    /** GPU time budget of the post-processing chain (ms); the resolution adapts to stay under it. */
    public float gpuBudgetMs = 8f;
    /** Millions of pixels the effect passes may use at full quality. */
    public float pixelBudget = 2.3f;
    /** Time per frame spent precompiling shaders when entering a world (ms). */
    public float warmupMsPerFrame = 25f;
    /** Shows active effects, passes and GPU time on screen. */
    public boolean debugOverlay = false;

    private static BlocmpactConfig instance = new BlocmpactConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static BlocmpactConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("blocmpact.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                BlocmpactConfig c = GSON.fromJson(r, BlocmpactConfig.class);
                if (c != null) instance = c;
            } catch (IOException | RuntimeException e) {
                Blocmpact.LOGGER.warn("Could not read {}, using defaults", p, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            Blocmpact.LOGGER.warn("Could not write {}", path(), e);
        }
    }
}
