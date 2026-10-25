package me.julionxn.blocmpact.client.api.geometry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Quad builder for level-2 geometry. Takes world positions (converted to camera-relative internally, in double
 * precision) and fills position, color, UV, overlay, light and normal as the render type requires.
 * Every shape is made of quads; UVs default to the 0–1 square so a {@link FxTextures} shape covers it.
 */
public final class Mesh {
    private static final int FULL_BRIGHT = 0xF000F0;

    private final PoseStack.Pose pose;
    private final VertexConsumer vc;
    private final Vec3 camera;
    private final Vector3f right, up;
    private final boolean hasUv, hasOverlay, hasLight, hasNormal;
    private int light = FULL_BRIGHT;
    private boolean doubleSided;

    public Mesh(PoseStack.Pose pose, VertexConsumer vc, VertexFormat format, Vec3 camera, Vector3f cameraRight, Vector3f cameraUp) {
        this.pose = pose;
        this.vc = vc;
        this.camera = camera;
        this.right = cameraRight;
        this.up = cameraUp;
        this.hasUv = format.contains(DefaultVertexFormat.UV0_SEMANTIC_NAME);
        this.hasOverlay = format.contains(DefaultVertexFormat.UV1_SEMANTIC_NAME);
        this.hasLight = format.contains(DefaultVertexFormat.UV2_SEMANTIC_NAME);
        this.hasNormal = format.contains(DefaultVertexFormat.NORMAL_SEMANTIC_NAME);
    }

    /** Packed light for the following vertices (default full bright, i.e. emissive). */
    public Mesh light(int packedLight) {
        this.light = packedLight;
        return this;
    }

    /**
     * Emit both faces of every quad (default off). Flat shapes always face the camera anyway; turn this on to see
     * the inside of closed shapes (spheres, tubes, boxes) through their translucent front.
     */
    public Mesh doubleSided(boolean on) {
        this.doubleSided = on;
        return this;
    }

    /** Light from block and sky levels (0–15). */
    public Mesh light(int block, int sky) {
        return light(LightCoordsUtil.pack(block, sky));
    }

    // ---- raw vertices

    /** One vertex at a camera-relative position. Call four times per quad. */
    public Mesh vertexRel(float x, float y, float z, int argb, float u, float v, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(argb);
        if (hasUv) vc.setUv(u, v);
        if (hasOverlay) vc.setOverlay(OverlayTexture.NO_OVERLAY);
        if (hasLight) vc.setLight(light);
        if (hasNormal) vc.setNormal(pose, nx, ny, nz);
        return this;
    }

    /** One vertex at a world position. */
    public Mesh vertex(Vec3 world, int argb, float u, float v, Vector3f normal) {
        return vertexRel((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), argb, u, v,
            normal.x, normal.y, normal.z);
    }

    // ---- quads

    /** Quad through four world points (in order around the edge), one color, full UV square. Faces the camera. */
    public Mesh quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        return quad(a, b, c, d, argb, argb, argb, argb, 0, 0, 1, 1);
    }

    /**
     * Quad with a color per corner and a UV rectangle (a = u0 v0, b = u1 v0, c = u1 v1, d = u0 v1). Its winding is
     * chosen to face the camera, so flat shapes are visible from both sides even with render types that cull.
     */
    public Mesh quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, float u0, float v0, float u1, float v1) {
        Vec3 mid = a.add(c).scale(0.5);
        return oriented(a, b, c, d, ca, cb, cc, cd, u0, v0, u1, v1, camera.subtract(mid));
    }

    /** Quad whose front faces {@code direction} (outward normal of a closed shape). */
    public Mesh quadFacing(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, float u0, float v0, float u1, float v1, Vec3 direction) {
        return oriented(a, b, c, d, ca, cb, cc, cd, u0, v0, u1, v1, direction);
    }

    private Mesh oriented(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, float u0, float v0, float u1, float v1, Vec3 facing) {
        Vec3 n = b.subtract(a).cross(c.subtract(a));
        boolean flip = n.dot(facing) < 0;
        emit(a, b, c, d, ca, cb, cc, cd, u0, v0, u1, v1, flip);
        if (doubleSided) emit(a, b, c, d, ca, cb, cc, cd, u0, v0, u1, v1, !flip);
        return this;
    }

    private void emit(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, float u0, float v0, float u1, float v1, boolean flip) {
        Vector3f n = normal(a, b, c);
        if (flip) {
            n.negate();
            vertex(a, ca, u0, v0, n);
            vertex(d, cd, u0, v1, n);
            vertex(c, cc, u1, v1, n);
            vertex(b, cb, u1, v0, n);
        } else {
            vertex(a, ca, u0, v0, n);
            vertex(b, cb, u1, v0, n);
            vertex(c, cc, u1, v1, n);
            vertex(d, cd, u0, v1, n);
        }
    }

    /** Camera-facing square: glows, flares, sparks. {@code rotation} in radians around the view axis. */
    public Mesh billboard(Vec3 center, float halfSize, float rotation, int argb) {
        return billboard(center, halfSize, halfSize, rotation, argb, 0, 0, 1, 1);
    }

    /** Camera-facing rectangle with a UV rectangle. */
    public Mesh billboard(Vec3 center, float halfWidth, float halfHeight, float rotation, int argb, float u0, float v0, float u1, float v1) {
        float c = (float) Math.cos(rotation), s = (float) Math.sin(rotation);
        Vector3f r = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s)).mul(halfWidth);
        Vector3f u = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s)).mul(halfHeight);
        Vec3 a = center.add(-r.x - u.x, -r.y - u.y, -r.z - u.z), b = center.add(r.x - u.x, r.y - u.y, r.z - u.z);
        Vec3 cc = center.add(r.x + u.x, r.y + u.y, r.z + u.z), d = center.add(-r.x + u.x, -r.y + u.y, -r.z + u.z);
        return quad(a, b, cc, d, argb, argb, argb, argb, u0, v1, u1, v0);
    }

    /**
     * Segment from {@code a} to {@code b} drawn as a camera-facing ribbon: beams, lasers, tracers. U runs along
     * the segment (0 at a, 1 at b), V across it.
     */
    public Mesh beam(Vec3 a, Vec3 b, float halfWidth, int colorA, int colorB) {
        return beam(a, b, halfWidth, halfWidth, colorA, colorB, 0, 1);
    }

    public Mesh beam(Vec3 a, Vec3 b, float halfWidthA, float halfWidthB, int colorA, int colorB, float uA, float uB) {
        Vec3 side = facingSide(a, b);
        if (side == null) return this;
        Vec3 sa = side.scale(halfWidthA), sb = side.scale(halfWidthB);
        return quad(a.subtract(sa), b.subtract(sb), b.add(sb), a.add(sa), colorA, colorB, colorB, colorA, uA, 0, uB, 1);
    }

    /**
     * A camera-facing strip through many points: trails, ribbons, lightning. {@code halfWidths} and {@code colors}
     * may have one entry (constant) or one per point. U goes from 0 at the first point to 1 at the last.
     */
    public Mesh ribbon(List<Vec3> points, float[] halfWidths, int[] colors) {
        int n = points.size();
        if (n < 2) return this;
        Vec3 prevL = null, prevR = null;
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i);
            Vec3 dir = points.get(Math.min(i + 1, n - 1)).subtract(points.get(Math.max(i - 1, 0)));
            Vec3 side = facingSide(p, p.add(dir));
            if (side == null) side = prevL == null ? new Vec3(right.x, right.y, right.z) : prevR.subtract(prevL).normalize();
            float w = halfWidths[Math.min(i, halfWidths.length - 1)];
            Vec3 l = p.subtract(side.scale(w)), r = p.add(side.scale(w));
            if (prevL != null) {
                int c0 = colors[Math.min(i - 1, colors.length - 1)], c1 = colors[Math.min(i, colors.length - 1)];
                float u0 = (i - 1) / (float) (n - 1), u1 = i / (float) (n - 1);
                quad(prevL, l, r, prevR, c0, c1, c1, c0, u0, 0, u1, 1);
            }
            prevL = l;
            prevR = r;
        }
        return this;
    }

    /**
     * Flat ring (or disc with {@code inner = 0}) around {@code normal}. U goes around (0–1), V from the inner edge (0)
     * to the outer edge (1).
     */
    public Mesh ring(Vec3 center, Vec3 normal, float inner, float outer, int segments, int colorInner, int colorOuter) {
        Vec3 n = normal.normalize();
        Vec3 t = Math.abs(n.y) < 0.99 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 bt = n.cross(t);
        segments = Math.max(3, segments);
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 d0 = t.scale(Math.cos(a0)).add(bt.scale(Math.sin(a0))), d1 = t.scale(Math.cos(a1)).add(bt.scale(Math.sin(a1)));
            quad(center.add(d0.scale(inner)), center.add(d1.scale(inner)), center.add(d1.scale(outer)), center.add(d0.scale(outer)),
                colorInner, colorInner, colorOuter, colorOuter, i / (float) segments, 0, (i + 1) / (float) segments, 1);
        }
        return this;
    }

    /** Filled disc, color fading from the center to the edge. */
    public Mesh disc(Vec3 center, Vec3 normal, float radius, int segments, int colorCenter, int colorEdge) {
        return ring(center, normal, 0, radius, segments, colorCenter, colorEdge);
    }

    /** Open tube from {@code a} to {@code b}: columns, pillars, cylindrical shields. U around, V along. */
    public Mesh cylinder(Vec3 a, Vec3 b, float radiusA, float radiusB, int segments, int colorA, int colorB) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1e-12) return this;
        Vec3 n = axis.normalize();
        Vec3 t = Math.abs(n.y) < 0.99 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 bt = n.cross(t);
        segments = Math.max(3, segments);
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 d0 = t.scale(Math.cos(a0)).add(bt.scale(Math.sin(a0))), d1 = t.scale(Math.cos(a1)).add(bt.scale(Math.sin(a1)));
            quadFacing(a.add(d0.scale(radiusA)), a.add(d1.scale(radiusA)), b.add(d1.scale(radiusB)), b.add(d0.scale(radiusB)),
                colorA, colorA, colorB, colorB, i / (float) segments, 0, (i + 1) / (float) segments, 1, d0.add(d1));
        }
        return this;
    }

    /** UV sphere: bubbles, shields, orbs. U around, V from bottom (0) to top (1). */
    public Mesh sphere(Vec3 center, float radius, int rings, int segments, int argb) {
        rings = Math.max(2, rings);
        segments = Math.max(3, segments);
        for (int r = 0; r < rings; r++) {
            double p0 = -Math.PI / 2 + Math.PI * r / rings, p1 = -Math.PI / 2 + Math.PI * (r + 1) / rings;
            for (int s = 0; s < segments; s++) {
                double a0 = Math.PI * 2 * s / segments, a1 = Math.PI * 2 * (s + 1) / segments;
                Vec3 q0 = spherePoint(p0, a0, radius), q2 = spherePoint(p1, a1, radius);
                quadFacing(center.add(q0), center.add(spherePoint(p0, a1, radius)), center.add(q2), center.add(spherePoint(p1, a0, radius)),
                    argb, argb, argb, argb, s / (float) segments, r / (float) rings, (s + 1) / (float) segments, (r + 1) / (float) rings,
                    q0.add(q2));
            }
        }
        return this;
    }

    /** The six faces of a box (facing outwards). */
    public Mesh box(AABB box, int argb) {
        Vec3 a = new Vec3(box.minX, box.minY, box.minZ), b = new Vec3(box.maxX, box.minY, box.minZ), c = new Vec3(box.maxX, box.minY, box.maxZ),
            d = new Vec3(box.minX, box.minY, box.maxZ), e = new Vec3(box.minX, box.maxY, box.minZ), f = new Vec3(box.maxX, box.maxY, box.minZ),
            g = new Vec3(box.maxX, box.maxY, box.maxZ), h = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 center = box.getCenter();
        face(a, b, c, d, argb, center);
        face(h, g, f, e, argb, center);
        face(a, e, f, b, argb, center);
        face(c, g, h, d, argb, center);
        face(d, h, e, a, argb, center);
        face(b, f, g, c, argb, center);
        return this;
    }

    private void face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb, Vec3 center) {
        quadFacing(a, b, c, d, argb, argb, argb, argb, 0, 0, 1, 1, a.add(c).scale(0.5).subtract(center));
    }

    // ---- helpers

    private static Vec3 spherePoint(double pitch, double yaw, double r) {
        double c = Math.cos(pitch);
        return new Vec3(Math.cos(yaw) * c * r, Math.sin(pitch) * r, Math.sin(yaw) * c * r);
    }

    /** Unit vector perpendicular to the segment and to the direction towards the camera, or null if degenerate. */
    private Vec3 facingSide(Vec3 a, Vec3 b) {
        Vec3 dir = b.subtract(a);
        Vec3 toCam = camera.subtract(a.add(b).scale(0.5));
        Vec3 side = dir.cross(toCam);
        double len = side.length();
        return len < 1e-9 ? null : side.scale(1 / len);
    }

    private static Vector3f normal(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 n = b.subtract(a).cross(c.subtract(a));
        double len = n.length();
        return len < 1e-12 ? new Vector3f(0, 1, 0) : new Vector3f((float) (n.x / len), (float) (n.y / len), (float) (n.z / len));
    }
}
