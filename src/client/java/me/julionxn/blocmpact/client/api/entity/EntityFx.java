package me.julionxn.blocmpact.client.api.entity;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-frame changes to how entities are drawn: colored outlines (the glowing effect, visible through walls) and
 * model part poses. Poses work on any entity model: parts are addressed by the names of the model's part tree.
 * Humanoids (players, zombies, armor...) use {@link #HEAD}, {@link #BODY}, {@link #RIGHT_ARM}... For other models,
 * the names are the ones in their {@code LayerDefinition} (e.g. a wolf's {@code "tail"}).
 */
public final class EntityFx {
    public static final String HEAD = "head", HAT = "hat", BODY = "body", RIGHT_ARM = "right_arm", LEFT_ARM = "left_arm",
        RIGHT_LEG = "right_leg", LEFT_LEG = "left_leg";

    private final Map<Integer, Integer> outlines = new HashMap<>();
    private final Map<Integer, Map<String, PartPose>> poses = new HashMap<>();

    /** Outlines the entity with an RGB color for this frame. The last call wins. */
    public EntityFx outline(int entityId, int rgb) {
        outlines.put(entityId, rgb & 0xFFFFFF);
        return this;
    }

    public EntityFx outline(Entity entity, int rgb) {
        return outline(entity.getId(), rgb);
    }

    /** Poses one part of the entity's model for this frame. The last call per part wins. */
    public EntityFx pose(int entityId, String part, PartPose pose) {
        if (pose.weight() > 0) poses.computeIfAbsent(entityId, k -> new HashMap<>()).put(part, pose);
        return this;
    }

    public EntityFx pose(Entity entity, String part, PartPose pose) {
        return pose(entity.getId(), part, pose);
    }

    // ---- read side

    public boolean isEmpty() {
        return outlines.isEmpty() && poses.isEmpty();
    }

    public Integer outline(int entityId) {
        return outlines.get(entityId);
    }

    public Map<String, PartPose> poses(int entityId) {
        return poses.get(entityId);
    }
}
