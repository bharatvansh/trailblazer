package com.trailblazer.plugin;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the visual rendering of paths for players using server-side particles.
 * This serves as the fallback for users without the client-side companion mod.
 */
public class PathRendererManager {

    // A map to track the active rendering task for each player.
    // Key: Player UUID
    // Value: The BukkitTask that is currently rendering a path for them.
    private final Map<UUID, BukkitTask> activeRenderTasks = new ConcurrentHashMap<>();
    private final TrailblazerPlugin plugin;

    public PathRendererManager(TrailblazerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts rendering a path for a specific player.
     * If another path is already being rendered, it will be stopped first.
     * @param player The player who will see the path.
     * @param path The path to render.
     */
    public void startRendering(Player player, PathData path) {
        stopRendering(player); // Stop any previous task for this player.

        World world = plugin.getServer().getWorld(path.getDimension());
        if (world == null) {
        // Use Adventure API instead of deprecated ChatColor
        player.sendMessage(net.kyori.adventure.text.Component.text(
            "Cannot show path: It is in a different world (" + path.getDimension() + ").",
            net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // Create a new BukkitRunnable to handle the particle spawning.
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // Ensure the player is still online.
                if (!player.isOnline()) {
                    stopRendering(player);
                    return;
                }

                // Define the particle options. Redstone dust allows us to specify a color.
                Particle.DustOptions dustOptions = new Particle.DustOptions(Color.AQUA, 1.0f);

                // Spawn a particle at each point in the path.
                for (Vector3d point : path.getPoints()) {
                    Location loc = new Location(world, point.getX(), point.getY(), point.getZ());
                    // The particle is only shown to the specific player.
                    player.spawnParticle(Particle.DUST, loc, 1, dustOptions);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 0L delay, 20L period (repeats every second)

        activeRenderTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops rendering a path for a player.
     * @param player The player to stop rendering for.
     */
    public void stopRendering(Player player) {
        BukkitTask existingTask = activeRenderTasks.remove(player.getUniqueId());
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }
}