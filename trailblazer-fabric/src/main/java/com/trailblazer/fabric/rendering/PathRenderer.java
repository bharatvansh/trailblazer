package com.trailblazer.fabric.rendering;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Using DustParticleEffect int color constructor (1.21.8)

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.RenderSettingsManager;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
// Removed low-level buffer rendering imports for compatibility

/**
 * Handles the client-side rendering of paths in the world.
 */
public class PathRenderer {

    private final ClientPathManager clientPathManager;
    private final RenderSettingsManager renderSettingsManager;

    private static final Map<Integer, DustParticleEffect> COLOR_CACHE = new ConcurrentHashMap<>();

    private DustParticleEffect effectFor(int argb) {
        return COLOR_CACHE.computeIfAbsent(argb, c -> {
            int rgb = c & 0xFFFFFF; // strip alpha
            return new DustParticleEffect(rgb, 1.0f);
        });
    }


    public PathRenderer(ClientPathManager clientPathManager, RenderSettingsManager renderSettingsManager) {
        this.clientPathManager = clientPathManager;
        this.renderSettingsManager = renderSettingsManager;
    }

    public void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            renderActivePaths(context);
        });
    }

    private void renderActivePaths(WorldRenderContext context) {
        ClientWorld world = (ClientWorld) context.world();
        PathData livePath = clientPathManager.getLivePath();
        if (livePath != null) {
            renderPath(livePath, context, true);
        }

        for (PathData path : clientPathManager.getVisiblePaths()) {
            renderPath(path, context, false);
        }
    }

    private void renderPath(PathData path, WorldRenderContext context, boolean isLive) {
        if (path.getPoints().size() < 2) {
            return;
        }

        switch (renderSettingsManager.getRenderMode()) {
            case DASHED_LINE:
                renderDashedLine(path, context, isLive);
                break;
            case SPACED_MARKERS:
                renderSpacedMarkers(path, (ClientWorld) context.world(), isLive);
                break;
            case DIRECTIONAL_ARROWS:
                renderDirectionalArrows(path, (ClientWorld) context.world(), isLive);
                break;
        }
    }

    /**
     * Renders the path as dashed line quads (billboarded) using the 1.21.8 buffer pipeline.
     * Depth testing remains enabled so dashes are occluded by world geometry.
     */
    private void renderDashedLine(PathData path, WorldRenderContext context, boolean isLive) {
        List<Vector3d> points = path.getPoints();
        final int color = path.getColorArgb();

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f;
        if (isLive) {
            r = 1.0f; g = 0.5f; b = 0.0f;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        double dashLength = 2.0;
        double gapLength = 1.0;
        double segmentLength = dashLength + gapLength;

        boolean any = false;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d start = new Vec3d(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            Vec3d end = new Vec3d(points.get(i + 1).getX(), points.get(i + 1).getY(), points.get(i + 1).getZ());
            double total = start.distanceTo(end);
            if (total < 0.1) continue;
            Vec3d dir = end.subtract(start).normalize();

            for (double d = 0; d < total; d += segmentLength) {
                double dashEnd = Math.min(d + dashLength, total);
                Vec3d ds = start.add(dir.multiply(d));
                Vec3d de = start.add(dir.multiply(dashEnd));

                // Billboard quad facing camera
                // Camera-facing billboard width vector for visual consistency
                Vec3d center = ds.add(de).multiply(0.5);
                Vec3d toCam = camera.subtract(center);
                if (toCam.lengthSquared() < 1.0e-6) toCam = new Vec3d(0, 1, 0);
                Vec3d right = dir.crossProduct(toCam);
                if (right.lengthSquared() < 1.0e-6) {
                    right = dir.crossProduct(new Vec3d(0, 1, 0));
                    if (right.lengthSquared() < 1.0e-6) right = dir.crossProduct(new Vec3d(0, 0, 1));
                }
                if (right.lengthSquared() < 1.0e-6) continue;
                right = right.normalize().multiply(0.10); // width

                // Positions relative to camera for current pass (no matrix entry)
                Vec3d sRel = ds.subtract(camera);
                Vec3d eRel = de.subtract(camera);

                Vec3d v1 = sRel.add(right);
                Vec3d v2 = sRel.subtract(right);
                Vec3d v3 = eRel.subtract(right);
                Vec3d v4 = eRel.add(right);

                buffer.vertex((float) v1.x, (float) v1.y, (float) v1.z).color(r, g, b, a);
                buffer.vertex((float) v2.x, (float) v2.y, (float) v2.z).color(r, g, b, a);
                buffer.vertex((float) v3.x, (float) v3.y, (float) v3.z).color(r, g, b, a);
                buffer.vertex((float) v4.x, (float) v4.y, (float) v4.z).color(r, g, b, a);
                any = true;
            }
        }

        BuiltBuffer built = buffer.end();
        if (any) {
            // Use a quads layer; depth test is enabled by default for debug quads
            RenderLayer.getDebugQuads().draw(built);
        }
        built.close();
    }
    
    // Removed buffer-based dash quad renderer for compatibility.

    /**
     * Renders the path as particles spaced at regular intervals.
     */
    private void renderSpacedMarkers(PathData path, ClientWorld world, boolean isLive) {
        double spacing = renderSettingsManager.getMarkerSpacing();
        double distanceSinceLastMarker = 0.0;
        Vec3d lastPoint = null;
        final int color = path.getColorArgb();
        final DustParticleEffect trailEffect = effectFor(color);

        for (Vector3d point : path.getPoints()) {
            Vec3d currentPoint = new Vec3d(point.getX(), point.getY(), point.getZ());
            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distanceTo(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= spacing) {
                if (isLive) {
                    world.addParticleClient(ParticleTypes.FLAME, currentPoint.x, currentPoint.y, currentPoint.z, 0, 0, 0);
                } else {
                    world.addParticleClient(trailEffect, currentPoint.x, currentPoint.y, currentPoint.z, 0, 0, 0);
                }
                distanceSinceLastMarker = 0.0;
            }
            lastPoint = currentPoint;
        }
    }

    /**
     * Renders the path as arrows indicating direction.
     */
    private void renderDirectionalArrows(PathData path, ClientWorld world, boolean isLive) {
        // Use the server's arrow spacing to keep behavior consistent between client and server.
        double spacing = 3.0;
        double distanceSinceLastMarker = 0.0;
        Vec3d lastPoint = null;

        List<Vector3d> points = path.getPoints();
        for (int i = 0; i < points.size(); i++) {
            Vector3d point = points.get(i);
            Vec3d currentPoint = new Vec3d(point.getX(), point.getY(), point.getZ());

            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distanceTo(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= spacing) {
                // Match server fallback: spawn a single FLAME particle and give it velocity in
                // the direction of travel instead of drawing explicit wings/tip geometry.
                Optional<Vec3d> nextPointOpt = findNextDistinctPoint(points, i);
                if (nextPointOpt.isPresent()) {
                    Vec3d direction = nextPointOpt.get().subtract(currentPoint).normalize();
                    spawnArrow(world, currentPoint, direction);
                }
                distanceSinceLastMarker = 0.0; // Reset distance
            }
            lastPoint = currentPoint;
        }
    }

    /**
     * Finds the next point in the list that is not the same as the current one.
     */
    private Optional<Vec3d> findNextDistinctPoint(List<Vector3d> points, int currentIndex) {
        for (int i = currentIndex + 1; i < points.size(); i++) {
            Vec3d currentPoint = new Vec3d(points.get(currentIndex).getX(), points.get(currentIndex).getY(), points.get(currentIndex).getZ());
            Vec3d nextPoint = new Vec3d(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            if (currentPoint.squaredDistanceTo(nextPoint) > 0.01) {
                return Optional.of(nextPoint);
            }
        }
        return Optional.empty();
    }

    /**
     * Spawns a small arrow shape using particles.
     */
    private void spawnArrow(ClientWorld world, Vec3d position, Vec3d direction) {
        // Server fallback: spawn a single FLAME particle and give it velocity in the
        // path direction. Scale the velocity to approximately match the server extra speed.
        double speed = 0.1; // matches the server-side extra parameter
        double vx = direction.x * speed;
        double vy = direction.y * speed;
        double vz = direction.z * speed;

        world.addParticleClient(ParticleTypes.FLAME, position.x, position.y, position.z, vx, vy, vz);
    }
}
