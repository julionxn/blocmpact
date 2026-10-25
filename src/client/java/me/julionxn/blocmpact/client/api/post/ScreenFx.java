package me.julionxn.blocmpact.client.api.post;

import org.joml.Vector3fc;

/**
 * Camera operations of the shared compositor (one full-resolution pass after the effects and the glow).
 * Every effect in range writes into the same instance each frame, so simultaneous effects combine instead of
 * fighting. Each setter documents its combination rule. All values start neutral: an effect that writes
 * nothing changes nothing. Requires OpenGL, like every level-3 feature.
 */
public final class ScreenFx {
    // displacement
    public float shake, shakeFrequency = 30f;
    public float aberration, aberrationX = 0.5f, aberrationY = 0.5f;
    public float zoomBlur, zoomX = 0.5f, zoomY = 0.5f;
    public float zoom = 1f, zoomCenterX = 0.5f, zoomCenterY = 0.5f;
    // light
    public float bloom, bloomRadius = 1f, thresholdBloom, adaptation;
    // grading
    public float exposure = 1f, saturation = 1f, contrast = 1f, contrastPivot = 0.5f, gamma = 1f, hueShift;
    public float tintR = 1f, tintG = 1f, tintB = 1f, liftR, liftG, liftB;
    public float grayR = 1f, grayG = 1f, grayB = 1f;
    public float keepColor, keepColorThreshold = 0.75f;
    // overlays
    public float vignette, vignetteR, vignetteG, vignetteB, vignetteInner = 0.35f, vignetteOuter = 1.05f;
    public float flash, flashR = 1f, flashG = 1f, flashB = 1f;
    public float fade, fadeR, fadeG, fadeB;
    public float grain, grainFps = 7f;
    // time
    public float holdFps;
    // impact frame
    public ImpactFrame impact;
    public float impactTime, impactX = 0.5f, impactY = 0.5f;

    // ---- displacement

    /** Image shake in screen units (≈0.15 hit, 1 big impact). Keeps the max. */
    public ScreenFx shake(float amount) {
        shake = Math.max(shake, amount);
        return this;
    }

    /** Radial chromatic aberration (≈0.3–1). Keeps the max. */
    public ScreenFx aberration(float amount) {
        aberration = Math.max(aberration, amount);
        return this;
    }

    /** Radial blur towards a screen point (uv, y up). The strongest wins. */
    public ScreenFx zoomBlur(float amount, float u, float v) {
        if (amount > zoomBlur) {
            zoomBlur = amount;
            zoomX = u;
            zoomY = v;
        }
        return this;
    }

    /** Scales the image around a point: 1.05 is a punch-in, 0.95 a pull-out. The farthest from 1 wins. */
    public ScreenFx zoom(float scale, float u, float v) {
        if (Math.abs(scale - 1f) > Math.abs(zoom - 1f)) {
            zoom = scale;
            zoomCenterX = u;
            zoomCenterY = v;
        }
        return this;
    }

    // ---- light

    /**
     * Bloom of the light the effects added (not of the whole image, so the sun and snow never burn).
     * {@code strength} ≈ 0.5–1, {@code radius} 1 = normal. Keeps the max of each.
     */
    public ScreenFx bloom(float strength, float radius) {
        bloom = Math.max(bloom, strength);
        bloomRadius = Math.max(bloomRadius, radius);
        return this;
    }

    /**
     * Declares how much of the image a pass invented (0–1). Invented pixels have no original scene to compare with,
     * so their bloom uses a classic brightness threshold instead. Pixels a pass writes with alpha 0 count as invented
     * anyway. Keeps the max.
     */
    public ScreenFx thresholdBloom(float amount) {
        thresholdBloom = Math.max(thresholdBloom, amount);
        return this;
    }

    /** Automatic exposure: darkens the image when added light floods the screen, like an eye adapting. Keeps the max. */
    public ScreenFx adaptation(float amount) {
        adaptation = Math.max(adaptation, amount);
        return this;
    }

    // ---- grading

    /** Brightness multiplier. Multiplies. */
    public ScreenFx exposure(float multiplier) {
        exposure *= multiplier;
        return this;
    }

    /** 1 = untouched, 0 = gray, above 1 = more vivid. The value farthest from 1 wins. */
    public ScreenFx saturation(float value) {
        if (Math.abs(value - 1f) > Math.abs(saturation - 1f)) saturation = value;
        return this;
    }

    /** Color the desaturated image tends to (white = neutral gray). The last call wins. */
    public ScreenFx grayTint(float r, float g, float b) {
        grayR = r;
        grayG = g;
        grayB = b;
        return this;
    }

    /** Contrast around a pivot (1 = untouched). The value farthest from 1 wins. */
    public ScreenFx contrast(float value, float pivot) {
        if (Math.abs(value - 1f) > Math.abs(contrast - 1f)) {
            contrast = value;
            contrastPivot = pivot;
        }
        return this;
    }

    /** Gamma (1 = untouched). The value farthest from 1 wins. */
    public ScreenFx gamma(float value) {
        if (Math.abs(value - 1f) > Math.abs(gamma - 1f)) gamma = value;
        return this;
    }

    /** Hue rotation in turns. Adds. */
    public ScreenFx hueShift(float turns) {
        hueShift += turns;
        return this;
    }

    /** Multiplies the image by a color. Multiplies. */
    public ScreenFx tint(float r, float g, float b) {
        tintR *= r;
        tintG *= g;
        tintB *= b;
        return this;
    }

    /** Adds a color to the shadows. Adds. */
    public ScreenFx lift(float r, float g, float b) {
        liftR += r;
        liftG += g;
        liftB += b;
        return this;
    }

    /**
     * While the image is desaturated, highlights above {@code threshold} (luminance 0–1) keep their color: a gray
     * world where only the light of the effect stays colored. Keeps the max.
     */
    public ScreenFx keepColor(float amount, float threshold) {
        if (amount > keepColor) {
            keepColor = amount;
            keepColorThreshold = threshold;
        }
        return this;
    }

    /** A frozen-time look in one call: desaturation towards a tint, light keeping its color, contrast and grain. {@code k} 0–1. */
    public ScreenFx frozenWorld(float k, float r, float g, float b) {
        saturation(1f - 0.9f * k);
        grayTint(r, g, b);
        keepColor(k, 0.65f);
        contrast(1f + 0.3f * k, 0.45f);
        grain(0.6f * k);
        return this;
    }

    // ---- overlays

    /** Darkens (or tints) the borders. Color 0,0,0 = black. The strongest wins. */
    public ScreenFx vignette(float amount, float r, float g, float b) {
        if (amount > vignette) {
            vignette = amount;
            vignetteR = r;
            vignetteG = g;
            vignetteB = b;
        }
        return this;
    }

    /** Shape of the vignette: where it starts and where it is complete (in screen-height units from the center). */
    public ScreenFx vignetteShape(float inner, float outer) {
        vignetteInner = inner;
        vignetteOuter = outer;
        return this;
    }

    /** Adds light of a color; above 1 it burns towards white. The strongest wins. */
    public ScreenFx flash(float amount, float r, float g, float b) {
        if (amount > flash) {
            flash = amount;
            flashR = r;
            flashG = g;
            flashB = b;
        }
        return this;
    }

    /** Blends the whole image towards a color (0–1): fade to black, white-out, blood screen. The strongest wins. */
    public ScreenFx fade(float amount, float r, float g, float b) {
        if (amount > fade) {
            fade = amount;
            fadeR = r;
            fadeG = g;
            fadeB = b;
        }
        return this;
    }

    /** Film grain. Keeps the max. */
    public ScreenFx grain(float amount) {
        grain = Math.max(grain, amount);
        return this;
    }

    // ---- time

    /** Holds drawings: the image only refreshes {@code fps} times per second (12 = animation "on twos"). Lowest wins. */
    public ScreenFx holdFps(float fps) {
        if (fps > 0 && (holdFps <= 0 || fps < holdFps)) holdFps = fps;
        return this;
    }

    // ---- impact frames

    /**
     * Replaces the image with a graphic version for this frame. {@code time} is seconds inside the current drawing
     * (animates glitch and rainbow); {@code focus} is the uv point of the impact (y up). The last call wins.
     */
    public ScreenFx impact(ImpactFrame style, float time, float u, float v) {
        impact = style;
        impactTime = time;
        impactX = u;
        impactY = v;
        return this;
    }

    public ScreenFx impact(ImpactFrame style, float time, Vector3fc focusUv) {
        return impact(style, time, focusUv.x(), focusUv.y());
    }

    /**
     * Plays a sequence of drawings, each {@code duration} seconds long, starting {@code since} seconds ago.
     * Does nothing before the start or after the last drawing.
     */
    public ScreenFx impactCycle(float since, float duration, float u, float v, ImpactFrame... drawings) {
        if (since < 0 || drawings.length == 0 || since >= duration * drawings.length) return this;
        int i = (int) (since / duration);
        return impact(drawings[i], since - i * duration, u, v);
    }

    /** True if anything differs from neutral. */
    public boolean active() {
        return shake > 0 || aberration > 0 || zoomBlur > 0 || zoom != 1f || bloom > 0 || exposure != 1f || saturation != 1f
            || contrast != 1f || gamma != 1f || hueShift != 0 || tintR != 1f || tintG != 1f || tintB != 1f || liftR != 0
            || liftG != 0 || liftB != 0 || vignette > 0 || flash > 0 || fade > 0 || grain > 0 || holdFps > 0 || impact != null;
    }
}
