package me.julionxn.blocmpact.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where an effect lives: a fixed point, or an entity it follows (with a reference point and an offset).
 * For entity anchors {@code position} is where the entity was at spawn time: it is used while the entity is not
 * loaded on a client and after it disappears.
 */
public record Anchor(Vec3 position, int entityId, Point point, Vec3 offset) {
    public static final int NO_ENTITY = -1;

    public enum Point {
        FEET, CENTER, EYES
    }

    public static Anchor at(Vec3 position) {
        return new Anchor(position, NO_ENTITY, Point.FEET, Vec3.ZERO);
    }

    public static Anchor on(Entity entity) {
        return on(entity, Point.FEET, Vec3.ZERO);
    }

    public static Anchor on(Entity entity, Point point, Vec3 offset) {
        return new Anchor(pointOf(entity, point, 1f).add(offset), entity.getId(), point, offset);
    }

    public boolean followsEntity() {
        return entityId != NO_ENTITY;
    }

    public Anchor withOffset(Vec3 newOffset) {
        return new Anchor(position, entityId, point, newOffset);
    }

    /** Current position in {@code level}, interpolated with {@code partial} when following a loaded entity. */
    public Vec3 resolve(Level level, float partial) {
        if (followsEntity() && level != null) {
            Entity e = level.getEntity(entityId);
            if (e != null) return pointOf(e, point, partial).add(offset);
        }
        return position;
    }

    public static Vec3 pointOf(Entity e, Point point, float partial) {
        Vec3 feet = e.getPosition(partial);
        return switch (point) {
            case FEET -> feet;
            case CENTER -> feet.add(0, e.getBbHeight() * 0.5, 0);
            case EYES -> feet.add(0, e.getEyeHeight(), 0);
        };
    }

    public static final StreamCodec<FriendlyByteBuf, Anchor> STREAM_CODEC = StreamCodec.of(
        (buf, a) -> {
            buf.writeDouble(a.position.x);
            buf.writeDouble(a.position.y);
            buf.writeDouble(a.position.z);
            buf.writeVarInt(a.entityId + 1);
            buf.writeByte(a.point.ordinal());
            buf.writeDouble(a.offset.x);
            buf.writeDouble(a.offset.y);
            buf.writeDouble(a.offset.z);
        },
        buf -> {
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int id = buf.readVarInt() - 1;
            Point[] points = Point.values();
            Point p = points[Math.floorMod(buf.readByte(), points.length)];
            Vec3 off = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            return new Anchor(pos, id, p, off);
        });
}
