package me.julionxn.blocmpact.client.api.geometry;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.resources.Identifier;

/**
 * Neutral white shapes shipped with Blocmpact (64×64, white with alpha), meant to be tinted by vertex color.
 * They are geometric primitives, not a style: bring your own textures for anything with character.
 */
public final class FxTextures {
    /** Gaussian falloff: halos, glows, soft light cards. */
    public static final Identifier SOFT = tex("soft");
    /** Filled circle with an anti-aliased edge. */
    public static final Identifier DOT = tex("dot");
    /** Anti-aliased ring. */
    public static final Identifier RING = tex("ring");
    /** Square with soft edges. */
    public static final Identifier SQUARE = tex("square");
    /** Horizontal streak, bright in the middle (stretch it along a velocity). */
    public static final Identifier STREAK = tex("streak");
    /** Thin cross (+). */
    public static final Identifier CROSS = tex("cross");
    /** Noisy puff: smoke and dust cards. */
    public static final Identifier PUFF = tex("puff");
    /** Thin anti-aliased ring. */
    public static final Identifier THIN_RING = tex("thin_ring");
    /** Horizontal gradient: opaque at u = 0, transparent at u = 1 (trails, fades along a ribbon). */
    public static final Identifier GRADIENT = tex("gradient");

    private FxTextures() {
    }

    private static Identifier tex(String name) {
        return Blocmpact.id("textures/fx/" + name + ".png");
    }
}
