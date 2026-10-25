package me.julionxn.blocmpact.client.impl.entity;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.entity.EntityFx;
import me.julionxn.blocmpact.client.api.entity.PartPose;
import me.julionxn.blocmpact.client.impl.FrameState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Copies the frame's {@link EntityFx} into render states and applies part poses after vanilla animation. */
public final class EntityPoses {
    public static final RenderStateDataKey<Map<String, PartPose>> POSES = RenderStateDataKey.create(() -> "blocmpact:poses");
    private static final Map<Model<?>, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();

    private EntityPoses() {
    }

    /** End of {@code EntityRenderer.extractRenderState}. */
    public static void extract(Entity entity, EntityRenderState state) {
        EntityFx fx = FrameState.entities();
        if (fx.isEmpty()) {
            state.setData(POSES, null);
            return;
        }
        Integer outline = fx.outline(entity.getId());
        if (outline != null) state.outlineColor = 0xFF000000 | outline;
        state.setData(POSES, fx.poses(entity.getId()));
    }

    /** Right after {@code Model.setupAnim(state)} at render time. */
    public static void apply(Model<?> model, Object state) {
        if (!(state instanceof FabricRenderState frs)) return;
        Map<String, PartPose> poses = frs.getData(POSES);
        if (poses == null || poses.isEmpty()) return;
        Function<String, ModelPart> lookup;
        synchronized (LOOKUPS) {
            lookup = LOOKUPS.computeIfAbsent(model, m -> m.root().createPartLookup());
        }
        for (Map.Entry<String, PartPose> e : poses.entrySet()) {
            ModelPart part;
            try {
                part = lookup.apply(e.getKey());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (part == null) continue;
            PartPose p = e.getValue();
            float w = FxMath.clamp01(p.weight());
            if (p.mode() == PartPose.Mode.SET) {
                part.xRot = FxMath.lerp(part.xRot, p.xRot(), w);
                part.yRot = FxMath.lerp(part.yRot, p.yRot(), w);
                part.zRot = FxMath.lerp(part.zRot, p.zRot(), w);
            } else {
                part.xRot += p.xRot() * w;
                part.yRot += p.yRot() * w;
                part.zRot += p.zRot() * w;
            }
            part.x += p.x() * w;
            part.y += p.y() * w;
            part.z += p.z() * w;
            float s = FxMath.lerp(1f, p.scale(), w);
            part.xScale *= s;
            part.yScale *= s;
            part.zScale *= s;
            if (!p.visible() && w >= 0.5f) part.visible = false;
        }
    }
}
