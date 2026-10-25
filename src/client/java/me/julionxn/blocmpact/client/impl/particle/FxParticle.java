package me.julionxn.blocmpact.client.impl.particle;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.api.particle.FxParticleOptions;
import me.julionxn.blocmpact.api.particle.FxParticles;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.RandomSource;

import java.util.List;

/** Client side of the configurable particle: every property comes from {@link FxParticleOptions}. */
public final class FxParticle extends SingleQuadParticle {
    private static Layer additiveLayer;

    private final FxParticleOptions options;
    private final FabricSpriteSet sprites;
    private final float sizeScale, spinSign;

    FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, FxParticleOptions o,
               FabricSpriteSet sprites, RandomSource random) {
        super(level, x, y, z, pick(o, sprites, random));
        this.options = o;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        float j = FxMath.clamp01(o.jitter());
        this.lifetime = Math.max(1, Math.round(o.lifetime() * (1 + (random.nextFloat() - 0.5f) * 2 * j * 0.5f)));
        this.sizeScale = 1 + (random.nextFloat() - 0.5f) * 2 * j * 0.5f;
        this.spinSign = j > 0 && random.nextBoolean() ? -1 : 1;
        this.gravity = o.gravity() / 0.04f;
        this.friction = o.drag();
        this.hasPhysics = o.collide();
        this.roll = this.oRoll = j > 0 ? random.nextFloat() * FxMath.TAU : 0;
        update();
    }

    private static TextureAtlasSprite pick(FxParticleOptions o, FabricSpriteSet sprites, RandomSource random) {
        List<TextureAtlasSprite> list = sprites.getSprites();
        if (list.isEmpty()) return sprites.first();
        return switch (o.sprite()) {
            case INDEX -> list.get(Math.floorMod(o.spriteIndex(), list.size()));
            case RANDOM -> list.get(random.nextInt(list.size()));
            case ANIMATE -> list.get(0);
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        oRoll = roll;
        roll += options.spin() * spinSign;
        if (options.sprite() == FxParticleOptions.Sprite.ANIMATE) setSpriteFromAge(sprites);
        update();
    }

    private void update() {
        float k = lifetime <= 0 ? 1 : FxMath.clamp01(age / (float) lifetime);
        float e = switch (options.curve()) {
            case LINEAR -> k;
            case EASE_IN -> k * k;
            case EASE_OUT -> 1 - (1 - k) * (1 - k);
            case SMOOTH -> FxMath.smooth(k);
        };
        int c = FxMath.lerpColor(options.colorStart(), options.colorEnd(), e);
        setColor(FxMath.red(c), FxMath.green(c), FxMath.blue(c));
        float a = FxMath.alpha(c);
        if (options.fadeIn() > 0) a *= FxMath.clamp01(k / options.fadeIn());
        setAlpha(a);
        quadSize = FxMath.lerp(options.sizeStart(), options.sizeEnd(), e) * sizeScale;
    }

    @Override
    protected Layer getLayer() {
        return switch (options.blend()) {
            case ADDITIVE -> additiveLayer();
            case OPAQUE -> Layer.OPAQUE;
            case TRANSLUCENT -> Layer.TRANSLUCENT;
        };
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return options.facing() == FxParticleOptions.Facing.VERTICAL ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    protected int getLightCoords(float partial) {
        return options.emissive() ? 0xF000F0 : super.getLightCoords(partial);
    }

    private static synchronized Layer additiveLayer() {
        if (additiveLayer == null) bootstrap();
        return additiveLayer;
    }

    /** Registers the additive particle pipeline and the providers of every configurable particle type. */
    public static synchronized void bootstrap() {
        if (additiveLayer == null) {
            RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                .withLocation(Blocmpact.id("pipeline/additive_particle"))
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build());
            OitPipelineSet oit = RenderPipelines.register(OitPipelineSet.builder("blocmpact_additive_particle",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build());
            additiveLayer = new Layer(true, Layer.TRANSLUCENT.textureAtlasLocation(), pipeline, oit);
        }
    }

    public static void registerProviders() {
        for (ParticleType<FxParticleOptions> type : FxParticles.types()) register(type);
    }

    public static void register(ParticleType<FxParticleOptions> type) {
        ParticleProviderRegistry.getInstance().register(type, (FabricSpriteSet sprites) -> provider(sprites));
    }

    private static ParticleProvider<FxParticleOptions> provider(FabricSpriteSet sprites) {
        return (FxParticleOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz, RandomSource random) ->
            (Particle) new FxParticle(level, x, y, z, vx, vy, vz, o, sprites, random);
    }
}
