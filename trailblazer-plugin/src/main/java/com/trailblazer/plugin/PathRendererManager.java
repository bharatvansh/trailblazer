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
    // Per-player spacing is read from PlayerRenderSettingsManager to allow parity with client settings.
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
                    case DASHED_LINE:
                        renderDashedLineParticles(player, path, world);
                        break;
                    case DIRECTIONAL_ARROWS:
                        renderDirectionalArrows(player, path, world);
                        break;
                    default:
                        // Unknown or unsupported mode for server fallback — default to dashed line.
                        renderDashedLineParticles(player, path, world);
                        break;
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // Refresh 10 times a second. VERY FAST, VERY SMOOTH.

        activeRenderTasks.put(player.getUniqueId(), task);
    }

    public void stopRendering(Player player) {
        BukkitTask task = activeRenderTasks.remove(player.getUniqueId());
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception e) {
                plugin.getLogger().warning("Error canceling render task: " + e.getMessage());
            }
        }
    }

    private void renderDashedLineParticles(Player player, PathData path, World world) {
        List<Vector3d> points = path.getPoints();
        final Particle.DustOptions dust = dustFor(path.getColorArgb());
        // Detect if this path corresponds to an active recording owned by the same player — used
        // to decide whether to render live-style visuals.
        boolean isLive = false;
        var active = plugin.getRecordingManager().getActive(path.getOwnerUUID());
        if (active != null) {
            try {
                if (active.pathId.equals(path.getPathId())) {
                    isLive = true;
                }
            } catch (Throwable ignored) { }
        }
        
        // Render dashed segments using particles (server-side fallback)
        double dashLength = 2.0; // Length of each dash
        double gapLength = 1.0;  // Length of each gap
        double segmentLength = dashLength + gapLength;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vector start = new Vector(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());
            Vector end = new Vector(points.get(i + 1).getX(), points.get(i + 1).getY(), points.get(i + 1).getZ());

            double totalDistance = start.distance(end);
            if (totalDistance < 0.1) continue;
            Vector direction = end.subtract(start).normalize();
            
            // Create dashes along the segment
            for (double d = 0; d < totalDistance; d += segmentLength) {
                double dashEnd = Math.min(d + dashLength, totalDistance);
                
                // Render particles only within the dash portion (not the gap)
                for (double dashPos = d; dashPos < dashEnd; dashPos += 0.5) {
                    Vector pos = start.add(direction.multiply(dashPos));
                    if (isLive) {
                        // For live visuals, use a flame particle for stronger contrast similar to the client.
                        player.spawnParticle(Particle.FLAME, pos.toLocation(world), 1, 0, 0, 0, 0);
                    } else {
                        player.spawnParticle(Particle.DUST, pos.toLocation(world), 1, dust);
                    }
                }
            }
        }
    }

    // Spaced markers intentionally unsupported in server fallback — client handles this mode when available.

    private void renderDirectionalArrows(Player player, PathData path, World world) {
        double distanceSinceLastMarker = 0.0;
        Vector lastPoint = null;
        List<Vector3d> points = path.getPoints();
        double spacing = plugin.getPlayerRenderSettingsManager().getMarkerSpacing(player);

        for (int i = 0; i < points.size(); i++) {
            Vector currentPoint = new Vector(points.get(i).getX(), points.get(i).getY(), points.get(i).getZ());

            if (lastPoint != null) {
                distanceSinceLastMarker += lastPoint.distance(currentPoint);
            }

            if (lastPoint == null || distanceSinceLastMarker >= spacing) {
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