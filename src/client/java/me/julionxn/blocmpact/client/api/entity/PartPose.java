package me.julionxn.blocmpact.client.api.entity;

/**
 * Transform applied to one model part after vanilla animated it. Rotations in radians, offsets in model pixels
 * (1/16 block). With {@link Mode#ADD} the values are added to the animation; with {@link Mode#SET} they replace it.
 * {@code weight} blends the result (0 = vanilla, 1 = full).
 */
public record PartPose(float xRot, float yRot, float zRot, float x, float y, float z, float scale, boolean visible, Mode mode,
                       float weight) {
    public enum Mode {ADD, SET}

    public static PartPose rotation(float xRot, float yRot, float zRot) {
        return new PartPose(xRot, yRot, zRot, 0, 0, 0, 1, true, Mode.SET, 1);
    }

    public static PartPose addRotation(float xRot, float yRot, float zRot) {
        return new PartPose(xRot, yRot, zRot, 0, 0, 0, 1, true, Mode.ADD, 1);
    }

    public static PartPose hidden() {
        return new PartPose(0, 0, 0, 0, 0, 0, 1, false, Mode.ADD, 1);
    }

    public PartPose withWeight(float w) {
        return new PartPose(xRot, yRot, zRot, x, y, z, scale, visible, mode, w);
    }

    public PartPose withOffset(float ox, float oy, float oz) {
        return new PartPose(xRot, yRot, zRot, ox, oy, oz, scale, visible, mode, weight);
    }

    public PartPose withScale(float s) {
        return new PartPose(xRot, yRot, zRot, x, y, z, s, visible, mode, weight);
    }
}
