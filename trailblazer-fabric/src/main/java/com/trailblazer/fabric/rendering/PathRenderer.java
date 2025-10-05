package com.trailblazer.fabric.rendering;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.RenderSettingsManager;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;

/**
 * Handles the client-side rendering of paths in the world.
 */
public class PathRenderer {

    private final ClientPathManager clientPathManager;
    private final RenderSettingsManager renderSettingsManager;

    private static final Map<Integer, DustParticleEffect> COLOR_CACHE = new ConcurrentHashMap<>();

    private DustParticleEffect effectFor(int argb) {
        return COLOR_CACHE.computeIfAbsent(argb, c -> {
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;
            return new DustParticleEffect(new Vector3f(r, g, b), 1.0f);
        });
    }


    public PathRenderer(ClientPathManager clientPathManager, RenderSettingsManager renderSettingsManager) {
        this.clientPathManager = clientPathManager;
        this.renderSettingsManager = renderSettingsManager;
    }

    public void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            renderActivePaths(context.world());
        });
    }

    private void renderActivePaths(ClientWorld world) {
        PathData livePath = clientPathManager.getLivePath();
        if (livePath != null) {
            renderPath(livePath, world, true);
        }

        for (PathData path : clientPathManager.getVisiblePaths()) {
            renderPath(path, world, false);
        }
    }

    private void renderPath(PathData path, ClientWorld world, boolean isLive) {
        if (path.getPoints().size() < 2) {
            return;
        }

        switch (renderSettingsManager.getRenderMode()) {
            case DASHED_LINE:
                renderDashedLine(path, world, isLive);
                break;
            case SPACED_MARKERS:
                renderSpacedMarkers(path, world, isLive);
                break;
            case DIRECTIONAL_ARROWS:
                renderDirectionalArrows(path, world, isLive);
                break;
        }
    }

    /**
     * Renders the path as dashed line segments using custom quad geometry.
     * This avoids animated particles for better performance.
     */
    private void renderDashedLine(PathData path, ClientWorld world, boolean isLive) {
        List<Vector3d> points = path.getPoints();
        final int color = path.getColorArgb();
        
        // Extract color components
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f;
        
        // Use orange for live paths
        if (isLive) {
            r = 1.0f;
            g = 0.5f;
            b = 0.0f;
        }
        
    // Setup rendering state (keep depth test ENABLED so dashes are occluded by terrain/blocks)
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    // Depth test intentionally left enabled to avoid ESP/X-ray effect
    RenderSystem.disableCull();
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        // Get camera position for billboarding
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        
        // Render dashed segments
        double dashLength = 2.0; // Length of each dash
        double gapLength = 1.0;  // Length of each gap
        double segmentLength = dashLength + gapLength;

        boolean hasVertices = false;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d start = new Vec3d(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            Vec3d end = new Vec3d(points.get(i + 1).getX(), points.get(i + 1).getY(), points.get(i + 1).getZ());
            
            double totalDistance = start.distanceTo(end);
            if (totalDistance < 0.1) continue; // Skip very short segments
            
            Vec3d direction = end.subtract(start).normalize();
            
            // Create dashes along the segment
            for (double d = 0; d < totalDistance; d += segmentLength) {
                double dashEnd = Math.min(d + dashLength, totalDistance);
                
                Vec3d dashStart = start.add(direction.multiply(d));
                Vec3d dashEndPos = start.add(direction.multiply(dashEnd));
                
                // Create a small quad for this dash
                hasVertices |= renderDashQuad(buffer, dashStart, dashEndPos, camera, r, g, b, a);
            }
        }

    var rendered = buffer.end();
        if (hasVertices) {
            BufferRenderer.drawWithGlobalProgram(rendered);
        }
        rendered.close();
        
    // Restore rendering state (depth test was never disabled)
    RenderSystem.enableCull();
    RenderSystem.disableBlend();
    }
    
    /**
     * Renders a single dash as a small quad billboard facing the camera.
     */
    private boolean renderDashQuad(BufferBuilder buffer, Vec3d start, Vec3d end, Vec3d camera,
                                   float r, float g, float b, float a) {
        Vec3d centerWorld = start.add(end).multiply(0.5);
        Vec3d toCamera = camera.subtract(centerWorld);
        if (toCamera.lengthSquared() < 1.0e-6) {
            toCamera = new Vec3d(0.0, 1.0, 0.0);
        }

        // Create a perpendicular vector for quad width
        Vec3d direction = end.subtract(start);
        double lengthSq = direction.lengthSquared();
        if (lengthSq < 1.0e-6) {
            return false;
        }
        direction = direction.normalize();

        Vec3d right = direction.crossProduct(toCamera);
        if (right.lengthSquared() < 1.0e-6) {
            right = direction.crossProduct(new Vec3d(0.0, 1.0, 0.0));
            if (right.lengthSquared() < 1.0e-6) {
                right = direction.crossProduct(new Vec3d(0.0, 0.0, 1.0));
            }
        }
        if (right.lengthSquared() < 1.0e-6) {
            return false;
        }
        right = right.normalize();

        double width = 0.1; // Width of the dash quad
        Vec3d rightOffset = right.multiply(width);

        // Positions relative to camera for the current render pass
        Vec3d startRelative = start.subtract(camera);
        Vec3d endRelative = end.subtract(camera);

        // Create quad vertices
        Vec3d v1 = startRelative.add(rightOffset);
        Vec3d v2 = startRelative.subtract(rightOffset);
        Vec3d v3 = endRelative.subtract(rightOffset);
        Vec3d v4 = endRelative.add(rightOffset);

        // Add vertices to buffer (counter-clockwise)
        buffer.vertex((float) v1.x, (float) v1.y, (float) v1.z).color(r, g, b, a);
        buffer.vertex((float) v2.x, (float) v2.y, (float) v2.z).color(r, g, b, a);
        buffer.vertex((float) v3.x, (float) v3.y, (float) v3.z).color(r, g, b, a);
        buffer.vertex((float) v4.x, (float) v4.y, (float) v4.z).color(r, g, b, a);

        return true;
    }

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
                    world.addParticle(ParticleTypes.FLAME, currentPoint.x, currentPoint.y, currentPoint.z, 0, 0, 0);
                } else {
                    world.addParticle(trailEffect, currentPoint.x, currentPoint.y, currentPoint.z, 0, 0, 0);
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

        world.addParticle(ParticleTypes.FLAME, position.x, position.y, position.z, vx, vy, vz);
    }
}