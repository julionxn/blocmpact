package me.julionxn.blocmpact.client.api.geometry;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render types for level-2 geometry. They go through Minecraft's own render API, so they also work with Vulkan
 * and with improved transparency. All of them draw quads; {@link Mesh} fills whatever attributes each one needs.
 */
public final class FxRenderTypes {
    private static final Map<Identifier, RenderType> ADDITIVE_TEXTURED = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> TRANSLUCENT_TEXTURED = new ConcurrentHashMap<>();
    private static RenderPipeline additivePipeline;
    private static OitPipelineSet additiveOit;

    private FxRenderTypes() {
    }

    /** Vertex colors added to the scene, no texture: glows, beams, rings, energy. Does not write depth. */
    public static RenderType additive() {
        return RenderTypes.lightning();
    }

    /**
     * A texture (white = shape, alpha = coverage) multiplied by the vertex color and added to the scene.
     * See {@link FxTextures} for neutral built-in shapes.
     */
    public static RenderType additive(Identifier texture) {
        return ADDITIVE_TEXTURED.computeIfAbsent(texture, t -> RenderType.create("blocmpact_additive_textured",
            RenderSetup.builder(additivePipeline()).setOitPipelines(additiveOit).withTexture("Sampler0", t)
                .useLightmap().useOverlay().sortOnUpload().createRenderSetup()));
    }

    /** A texture blended normally (alpha), full bright: smoke cards, decals, symbols, panels. */
    public static RenderType translucent(Identifier texture) {
        return TRANSLUCENT_TEXTURED.computeIfAbsent(texture, RenderTypes::entityTranslucentEmissive);
    }

    /** Minecraft's beacon beam type (scrolling texture), opaque or translucent. */
    public static RenderType beam(Identifier texture, boolean translucent) {
        return RenderTypes.beaconBeam(texture, translucent);
    }

    /** Additive texture with a UV offset (animate {@code u}, {@code v} to scroll it). Writes depth. */
    public static RenderType swirl(Identifier texture, float u, float v) {
        return RenderTypes.energySwirl(texture, u, v);
    }

    private static synchronized RenderPipeline additivePipeline() {
        if (additivePipeline == null) bootstrap();
        return additivePipeline;
    }

    /** Registers Blocmpact's pipelines (called during client initialization). */
    public static synchronized void bootstrap() {
        if (additivePipeline != null) return;
        additivePipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENERGY_SWIRL_SNIPPET)
            .withLocation(Blocmpact.id("pipeline/additive_textured"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build());
        additiveOit = RenderPipelines.register(OitPipelineSet.builder("blocmpact_additive_textured",
            RenderPipeline.builder(RenderPipelines.ENERGY_SWIRL_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build());
    }
}
