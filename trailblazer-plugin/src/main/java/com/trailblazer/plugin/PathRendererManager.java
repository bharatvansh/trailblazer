package com.trailblazer.plugin;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.rendering.RenderMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the visual rendering of paths for players using server-side particles.
 * This serves as the fallback for users without the client-side companion mod.
 */
public class PathRendererManager {

    private final Map<UUID, BukkitTask> activeRenderTasks = new ConcurrentHashMap<>();
    private final TrailblazerPlugin plugin;
    private final double markerSpacing = 3.0;
    private static final java.util.Map<Integer, Particle.DustOptions> DUST_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static Particle.DustOptions dustFor(int argb) {
        return DUST_CACHE.computeIfAbsent(argb, c -> {
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            return new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f);
        });
    }

    public PathRendererManager(TrailblazerPlugin plugin) {
        this.plugin = plugin;
    }

    public void startRendering(Player player, PathData path) {
        stopRendering(player);

        if (plugin.getServerPacketHandler().isModdedPlayer(player)) {
            return;
        }

        World world = resolveWorld(path.getDimension());
        if (world == null) {
            player.sendMessage(Component.text("Cannot show path: It is in a different world (" + path.getDimension() + ").", NamedTextColor.RED));
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopRendering(player);
                    return;
                }

                RenderMode currentMode = plugin.getPlayerRenderSettingsManager().getRenderMode(player);

                switch (currentMode) {
                    case PARTICLE_TRAIL:
                        renderInterpolatedParticleTrail(player, path, world);
                        break;
                    case SPACED_MARKERS:
                        renderSpacedMarkers(player, path, world);
                        break;
                    case DIRECTIONAL_ARROWS:
                        renderDirectionalArrows(player, path, world);
                        break;
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // Refresh 10 times a second. VERY FAST, VERY SMOOTH.

        activeRenderTasks.put(player.getUniqueId(), task);
    }

    public void stopRendering(Player player) {
        BukkitTask existingTask = activeRenderTasks.remove(player.getUniqueId());
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    private void renderInterpolatedParticleTrail(Player player, PathData path, World world) {
        List<Vector3d> points = path.getPoints();
        final Particle.DustOptions dust = dustFor(path.getColorArgb());
        for (int i = 0; i < points.size() - 1; i++) {
            Vector start = new Vector(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            Vector end = new Vector(points.get(i + 1).getX(), points.get(i + 1).getY(), points.get(i + 1).getZ());

            double distance = start.distance(end);
            if (distance < 0.1) continue;
            Vector direction = end.subtract(start).normalize();
            
            for (double d = 0; d < distance; d += 0.25) {
                Vector pos = start.add(direction.multiply(d));
                player.spawnParticle(Particle.DUST, pos.toLocation(world), 1, dust);
            }
        }
    }

    private void renderSpacedMarkers(Player player, PathData path, World world) {
        double distanceSinceLastMarker = 0.0;
        Vector lastPoint = null;

        for (Vector3d point : path.getPoints()) {
            Vector currentPoint = new Vector(point.getX(), point.getY(), point.getZ());
            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distance(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= markerSpacing) {
                // Using END_ROD for a much more solid, "static" looking marker.
                player.spawnParticle(Particle.END_ROD, currentPoint.toLocation(world), 1, 0, 0, 0, 0);
                distanceSinceLastMarker = 0.0;
            }
            lastPoint = currentPoint;
        }
    }

    private void renderDirectionalArrows(Player player, PathData path, World world) {
        double distanceSinceLastMarker = 0.0;
        Vector lastPoint = null;
        List<Vector3d> points = path.getPoints();

        for (int i = 0; i < points.size(); i++) {
            Vector currentPoint = new Vector(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());

            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distance(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= markerSpacing) {
                findNextDistinctPoint(points, i).ifPresent(nextPoint -> {
                    Vector direction = nextPoint.subtract(currentPoint).normalize();
                    // NEW LOGIC: Spawn one particle and give it velocity for a clear direction.
                    player.spawnParticle(Particle.FLAME, currentPoint.toLocation(world), 0, direction.getX(), direction.getY(), direction.getZ(), 0.1);
                });
                distanceSinceLastMarker = 0.0;
            }
            lastPoint = currentPoint;
        }
    }

    private Optional<Vector> findNextDistinctPoint(List<Vector3d> points, int currentIndex) {
        for (int i = currentIndex + 1; i < points.size(); i++) {
            Vector currentVec = new Vector(points.get(currentIndex).getX(), points.get(currentIndex).getY(), points.get(currentIndex).getZ());
            Vector nextVec = new Vector(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            if (currentVec.distanceSquared(nextVec) > 0.01) {
                return Optional.of(nextVec);
            }
        }
        return Optional.empty();
    }

    private World resolveWorld(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }

        World world = plugin.getServer().getWorld(dimensionId);
        if (world != null) {
            return world;
        }

        try {
            for (World candidate : plugin.getServer().getWorlds()) {
                if (dimensionId.equals(candidate.key().asString())) {
                    return candidate;
                }
            }
        } catch (NoSuchMethodError ignored) {
            // Older server versions may not expose World#key at runtime.
        }

        try {
            NamespacedKey key = NamespacedKey.fromString(dimensionId);
            if (key != null) {
                try {
                    world = plugin.getServer().getWorld(key);
                    if (world != null) {
                        return world;
                    }
                } catch (NoSuchMethodError ignored) {
                    // Method unavailable; fall through to name-based fallback below.
                }
                world = plugin.getServer().getWorld(key.getKey());
                if (world != null) {
                    return world;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Dimension string was not a valid namespaced key.
        }

        return null;
    }
}