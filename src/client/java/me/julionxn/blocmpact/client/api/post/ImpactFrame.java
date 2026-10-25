package me.julionxn.blocmpact.client.api.post;

/**
 * A graphic "impact frame": for a few drawings the whole image is replaced by a stylized version built from
 * masks of the image itself (local two-tone, ink edges, highlights, halftone). Every color is yours to choose;
 * the defaults are plain black ink on white paper.
 *
 * <pre>{@code
 * ImpactFrame comic = ImpactFrame.builder(ImpactFrame.Mode.TWO_TONE).paper(0xFFF2E8D5).ink(0xFF1A0F0A).build();
 * screen.impact(comic, secondsIntoThisDrawing, focusUv);
 * }</pre>
 *
 * @param mode      how the masks are combined
 * @param paper     ARGB of the bright side
 * @param ink       ARGB of the dark side and of the lines
 * @param highlight ARGB of the hottest areas (core)
 * @param bias      shifts the two-tone split (-0.5 darker … 0.5 brighter)
 * @param edges     strength of the ink outlines (0 = none)
 * @param cell      halftone cell size in pixels, or posterize levels for {@link Mode#POSTERIZE}
 * @param glitch    band displacement and channel split amount
 * @param spin      hue rotation speed for {@link Mode#RAINBOW}
 * @param invert    swaps paper and ink
 */
public record ImpactFrame(Mode mode, int paper, int ink, int highlight, float bias, float edges, float cell, float glitch,
                          float spin, boolean invert) {

    public enum Mode {
        /** Paper where the image is brighter than its surroundings, ink elsewhere, with ink outlines and highlights. */
        TWO_TONE,
        /** Two-tone without outlines: flat silhouettes. */
        SILHOUETTE,
        /** Only the outlines, like line art. */
        LINE_ART,
        /** Halftone dots sized by the tone ({@code cell} = pixels per dot). */
        HALFTONE,
        /** {@code cell} levels between ink and paper, highlight on top. */
        POSTERIZE,
        /** Inverted image. */
        NEGATIVE,
        /** Two-tone with displaced bands and channel split. */
        GLITCH,
        /** Two-tone whose paper is a hue wheel around the focus point. */
        RAINBOW,
        /** Ink over the untouched image: outlines only, colors kept. */
        INK_OUTLINE
    }

    public static final ImpactFrame DEFAULT = builder(Mode.TWO_TONE).build();

    public static Builder builder(Mode mode) {
        return new Builder(mode);
    }

    public Builder toBuilder() {
        return new Builder(mode).paper(paper).ink(ink).highlight(highlight).bias(bias).edges(edges).cell(cell).glitch(glitch)
            .spin(spin).invert(invert);
    }

    public static final class Builder {
        private final Mode mode;
        private int paper = 0xFFFFFFFF, ink = 0xFF000000, highlight = 0xFFFFFFFF;
        private float bias = 0f, edges = 1f, cell = 9f, glitch = 0.5f, spin = 1f;
        private boolean invert;

        private Builder(Mode mode) {
            this.mode = mode;
            if (mode == Mode.POSTERIZE) cell = 4f;
        }

        public Builder paper(int argb) {
            paper = argb;
            return this;
        }

        public Builder ink(int argb) {
            ink = argb;
            return this;
        }

        public Builder highlight(int argb) {
            highlight = argb;
            return this;
        }

        public Builder bias(float v) {
            bias = v;
            return this;
        }

        public Builder edges(float v) {
            edges = v;
            return this;
        }

        public Builder cell(float v) {
            cell = v;
            return this;
        }

        public Builder glitch(float v) {
            glitch = v;
            return this;
        }

        public Builder spin(float v) {
            spin = v;
            return this;
        }

        public Builder invert(boolean v) {
            invert = v;
            return this;
        }

        public ImpactFrame build() {
            return new ImpactFrame(mode, paper, ink, highlight, bias, edges, cell, glitch, spin, invert);
        }
    }
}
