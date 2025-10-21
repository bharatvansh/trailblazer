package com.trailblazer.plugin;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.trailblazer.api.PathColors;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles server-side path recording for players without the client mod (and also works with it for live preview).
 */
public class RecordingManager {
    private static final double MIN_DIST_SQ = 0.04; // ~0.2 blocks movement threshold
    private final Map<UUID, ActiveRecording> active = new HashMap<>();
    private final TrailblazerPlugin plugin;
    private final PathDataManager dataManager;
    private int maxPointsPerPath = 5000; // could be made configurable later

    public RecordingManager(TrailblazerPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPathDataManager();
    }

    public boolean isRecording(UUID playerId) {
        return active.containsKey(playerId);
    }

    public boolean startRecording(Player player, String providedName) {
        UUID id = player.getUniqueId();
        if (active.containsKey(id)) return false;
        String name = (providedName != null && !providedName.isBlank()) ? providedName.trim() : dataManager.getNextServerPathName();
        ActiveRecording rec = new ActiveRecording(UUID.randomUUID(), name, player.getWorld(), System.currentTimeMillis());
        active.put(id, rec);
        // seed with initial point immediately
        appendPoint(player, rec, true);
        return true;
    }

    public PathData stopRecording(Player player, boolean save) {
        ActiveRecording rec = active.remove(player.getUniqueId());
        if (rec == null) return null;
        if (!save || rec.points.size() < 2) {
            return null; // discard too-short or cancelled
        }
        PathData data = new PathData(rec.pathId, rec.name, player.getUniqueId(), player.getName(), rec.startTime,
            dimensionKey(rec.world), new ArrayList<>(rec.points), PathColors.assignColorFor(rec.pathId));
        dataManager.savePath(rec.world.getUID(), data);
        return data;
    }

    public void cancelRecording(Player player) {
        active.remove(player.getUniqueId());
    }

    public void tick() {
        if (active.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, ActiveRecording>> it = active.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, ActiveRecording> e = it.next();
            Player p = plugin.getServer().getPlayer(e.getKey());
            ActiveRecording rec = e.getValue();
            if (p == null || !p.isOnline()) {
                // auto-cancel if player left
                it.remove();
                continue;
            }
            if (p.getWorld() != rec.world) {
                // stop if dimension/world changed (could alternatively split)
                it.remove();
                continue;
            }
            appendPoint(p, rec, false);
        }
    }

    private void appendPoint(Player player, ActiveRecording rec, boolean force) {
        Location loc = player.getLocation();
        Vector3d current = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        if (!force && !rec.points.isEmpty()) {
            Vector3d last = rec.points.get(rec.points.size() - 1);
            double dx = current.getX() - last.getX();
            double dy = current.getY() - last.getY();
            double dz = current.getZ() - last.getZ();
            if ((dx*dx + dy*dy + dz*dz) < MIN_DIST_SQ) return;
        }
        if (rec.points.size() >= maxPointsPerPath) {
            player.sendMessage(Component.text("Path recording limit reached (" + maxPointsPerPath + " points). Recording stopped.", NamedTextColor.YELLOW));
            stopRecording(player, true);
            return;
        }
        rec.points.add(current);
        // Live update for modded player
        if (plugin.getServerPacketHandler().isModdedPlayer(player)) {
            plugin.getServerPacketHandler().sendLivePathUpdate(player, rec.points);
        }
    }

    private String dimensionKey(World world) {
        // Simplified mapping using namespaced key where possible.
        try {
            return world.key().asString();
        } catch (Throwable t) {
            return world.getName();
        }
    }

    public ActiveRecording getActive(UUID playerId) { return active.get(playerId); }

    public static class ActiveRecording {
        final UUID pathId;
        final String name;
        final World world;
        final long startTime;
        final List<Vector3d> points = new ArrayList<>();
        ActiveRecording(UUID pathId, String name, World world, long startTime) {
            this.pathId = pathId; this.name = name; this.world = world; this.startTime = startTime; }
        public String getName() { return name; }
        public List<Vector3d> getPoints() { return points; }
    }
}
