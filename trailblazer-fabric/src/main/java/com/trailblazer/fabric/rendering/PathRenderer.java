package com.trailblazer.fabric.rendering;

import java.util.List;
import java.util.Optional;

import org.joml.Vector3f;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.RenderSettingsManager;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Handles the client-side rendering of paths in the world.
 */
public class PathRenderer {

    private final ClientPathManager clientPathManager;
    private final RenderSettingsManager renderSettingsManager;

    // A nice blue color for the particle trail
    private static final DustParticleEffect TRAIL_PARTICLE = new DustParticleEffect(new Vector3f(0.2f, 0.5f, 1.0f), 1.0f);
    private static final DustParticleEffect LIVE_TRAIL_PARTICLE = new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.0f), 1.0f);


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
        // --- START OF FIX ---
        // Render the live path if it exists, using a null check
        PathData livePath = clientPathManager.getLivePath();
        if (livePath != null) {
            renderPath(livePath, world, true);
        }
        // --- END OF FIX ---

        // Render all synced paths
        for (PathData path : clientPathManager.getVisiblePaths()) {
            renderPath(path, world, false);
        }
    }

    private void renderPath(PathData path, ClientWorld world, boolean isLive) {
        if (path.getPoints().size() < 2) {
            return; // Cannot render a path with fewer than 2 points
        }

        // Use the appropriate rendering method based on the current mode
        switch (renderSettingsManager.getRenderMode()) {
            case PARTICLE_TRAIL:
                renderInterpolatedParticleTrail(path, world, isLive);
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
     * Renders the path as a smooth, continuous line by interpolating between points.
     */
    private void renderInterpolatedParticleTrail(PathData path, ClientWorld world, boolean isLive) {
        List<Vector3d> points = path.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d start = new Vec3d(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            Vec3d end = new Vec3d(points.get(i + 1).getX(), points.get(i + 1).getY(), points.get(i + 1).getZ());

            double distance = start.distanceTo(end);
            Vec3d direction = end.subtract(start).normalize();
            
            // Spawn a particle at small intervals along the line between points
            for (double d = 0; d < distance; d += 0.25) {
                Vec3d pos = start.add(direction.multiply(d));
                 world.addParticle(
                    isLive ? LIVE_TRAIL_PARTICLE : TRAIL_PARTICLE,
                    pos.x, pos.y, pos.z,
                    0, 0, 0);
            }
        }
    }

    /**
     * Renders the path as particles spaced at regular intervals.
     */
    private void renderSpacedMarkers(PathData path, ClientWorld world, boolean isLive) {
        double spacing = renderSettingsManager.getMarkerSpacing();
        double distanceSinceLastMarker = 0.0;
        Vec3d lastPoint = null;

        for (Vector3d point : path.getPoints()) {
            Vec3d currentPoint = new Vec3d(point.getX(), point.getY(), point.getZ());
            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distanceTo(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= spacing) {
                world.addParticle(
                        isLive ? ParticleTypes.FLAME : ParticleTypes.INSTANT_EFFECT,
                        currentPoint.x,
                        currentPoint.y,
                        currentPoint.z,
                        0, 0, 0);
                distanceSinceLastMarker = 0.0; // Reset distance
            }
            lastPoint = currentPoint;
        }
    }

    /**
     * Renders the path as arrows indicating direction.
     */
    private void renderDirectionalArrows(PathData path, ClientWorld world, boolean isLive) {
        double spacing = renderSettingsManager.getMarkerSpacing();
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
                // Find the next point to determine direction
                Optional<Vec3d> nextPointOpt = findNextDistinctPoint(points, i);
                if (nextPointOpt.isPresent()) {
                    Vec3d direction = nextPointOpt.get().subtract(currentPoint).normalize();
                    spawnArrow(world, currentPoint, direction, isLive);
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
    private void spawnArrow(ClientWorld world, Vec3d position, Vec3d direction, boolean isLive) {
        // The main point of the arrow tip
        world.addParticle(isLive ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                position.x, position.y, position.z, 0, 0, 0);

        // Calculate a perpendicular vector for the arrow wings
        Vec3d perpendicular = new Vec3d(-direction.z, 0, direction.x).normalize();
        if (perpendicular.lengthSquared() < 0.1) { // Handle case where direction is mostly vertical
            perpendicular = new Vec3d(1, 0, 0);
        }

        // Two wing points, spaced wider and further back
        Vec3d wing1 = position.subtract(direction.multiply(0.6)).add(perpendicular.multiply(0.4));
        Vec3d wing2 = position.subtract(direction.multiply(0.6)).subtract(perpendicular.multiply(0.4));

        world.addParticle(isLive ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.CRIT,
                wing1.x, wing1.y, wing1.z, 0, 0, 0);
        world.addParticle(isLive ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.CRIT,
                wing2.x, wing2.y, wing2.z, 0, 0, 0);
    }
}