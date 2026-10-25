package me.julionxn.blocmpact.client.api.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Helpers to make effects follow the ground. */
public final class Terrain {
    private Terrain() {
    }

    /** Top of the highest motion-blocking block at (x, z), or {@code fallback} if the chunk is not loaded. */
    public static double groundY(Level level, double x, double z, double fallback) {
        if (level == null) return fallback;
        BlockPos p = BlockPos.containing(x, 0, z);
        if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) return fallback;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
    }

    /** First solid surface straight below {@code from} (up to {@code maxDepth} blocks), or {@code from} if none. */
    public static Vec3 below(Level level, Vec3 from, double maxDepth) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.subtract(0, maxDepth, 0), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? from : hit.getLocation();
    }
}
