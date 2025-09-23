package com.trailblazer.plugin.commands;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.PathDataManager;
import com.trailblazer.plugin.PathRecordingManager;
import com.trailblazer.plugin.PathRendererManager;
import com.trailblazer.plugin.TrailblazerPlugin;
import com.trailblazer.plugin.networking.ServerPacketHandler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PathCommand implements CommandExecutor {

    private final PathRecordingManager recordingManager;
    private final PathDataManager dataManager;
    private final PathRendererManager rendererManager;
    private final ServerPacketHandler packetHandler;

    // Refactored constructor to take the main plugin instance for easier access to all managers.
    public PathCommand(TrailblazerPlugin plugin) {
        this.recordingManager = plugin.getPathRecordingManager();
        this.dataManager = plugin.getPathDataManager();
        this.rendererManager = plugin.getPathRendererManager();
        this.packetHandler = plugin.getServerPacketHandler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        // Main command router
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "record":
                handleRecord(player, args);
                break;
            case "list":
                handleList(player);
                break;
            case "show":
                handleShow(player, args);
                break;
            case "hide":
                handleHide(player);
                break;
            default:
                sendUsage(player);
                break;
        }
        return true;
    }

    private void handleRecord(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player);
            return;
        }
        if (args[1].equalsIgnoreCase("start")) {
            boolean started = recordingManager.startRecording(player);
            if (started) {
                player.sendMessage(Component.text("Path recording started. Move to record your path.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("You are already recording a path.", NamedTextColor.YELLOW));
            }
        } else if (args[1].equalsIgnoreCase("stop")) {
            List<Vector3d> recordedPoints = recordingManager.stopRecording(player);
            if (recordedPoints == null) {
                player.sendMessage(Component.text("You were not recording a path.", NamedTextColor.YELLOW));
                return;
            }
            if (recordedPoints.size() < 2) {
                player.sendMessage(Component.text("Path too short to save. You need to move further.", NamedTextColor.YELLOW));
                return;
            }
            String pathName = "Path-" + (dataManager.loadPaths(player.getUniqueId()).size() + 1);
            PathData newPath = new PathData(UUID.randomUUID(), pathName, player.getUniqueId(), player.getName(), System.currentTimeMillis(), player.getWorld().getName(), recordedPoints);
            dataManager.savePath(player.getUniqueId(), newPath);
            player.sendMessage(Component.text()
                    .append(Component.text("Path recording stopped and saved as '", NamedTextColor.GREEN))
                    .append(Component.text(pathName, NamedTextColor.AQUA))
                    .append(Component.text("'.", NamedTextColor.GREEN)));
        } else {
            sendUsage(player);
        }
    }

    private void handleList(Player player) {
        List<PathData> paths = dataManager.loadPaths(player.getUniqueId());
        if (paths.isEmpty()) {
            player.sendMessage(Component.text("You have no saved paths.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("--- Your Saved Paths ---", NamedTextColor.GOLD));
        AtomicInteger index = new AtomicInteger(1);
        paths.forEach(path -> {
            int i = index.getAndIncrement();
            player.sendMessage(Component.text()
                    .append(Component.text(i + ". ", NamedTextColor.GRAY))
                    .append(Component.text(path.getPathName(), NamedTextColor.AQUA)));
        });
    }

    private void handleShow(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /path show <path_name>", NamedTextColor.RED));
            return;
        }
        String pathName = args[1];
        List<PathData> paths = dataManager.loadPaths(player.getUniqueId());
        Optional<PathData> foundPathOptional = paths.stream()
            .filter(p -> p.getPathName().equalsIgnoreCase(pathName))
            .findFirst();

        if (foundPathOptional.isPresent()) {
            PathData foundPath = foundPathOptional.get();

            if (packetHandler.isModdedPlayer(player)) {
                // Player has the mod, send the data via network packet.
                packetHandler.sendPathData(player, foundPath);
                player.sendMessage(Component.text()
                    .append(Component.text("Sending path to your client: ", NamedTextColor.GREEN))
                    .append(Component.text(pathName, NamedTextColor.AQUA)));
            } else {
                // Player does not have the mod, use the vanilla particle fallback.
                rendererManager.startRendering(player, foundPath);
                player.sendMessage(Component.text()
                    .append(Component.text("Showing path: ", NamedTextColor.GREEN))
                    .append(Component.text(pathName, NamedTextColor.AQUA)));
            }

        } else {
            player.sendMessage(Component.text("Could not find a path named '" + pathName + "'.", NamedTextColor.RED));
        }
    }

    private void handleHide(Player player) {
        if (packetHandler.isModdedPlayer(player)) {
            // If the player has the mod, send the "hide" packet.
            packetHandler.sendHideAllPaths(player);
        } else {
            // Otherwise, use the old particle renderer stop method.
            rendererManager.stopRendering(player);
        }
        player.sendMessage(Component.text("Stopped showing any active path.", NamedTextColor.GREEN));
    }

    private void sendUsage(Player player) {
    player.sendMessage(Component.text("--- Trailblazer Commands ---", NamedTextColor.GOLD));
    player.sendMessage(Component.text()
        .append(Component.text("/path record <start|stop>", NamedTextColor.AQUA))
        .append(Component.text(" - Start or stop recording.", NamedTextColor.GRAY)));
    player.sendMessage(Component.text()
        .append(Component.text("/path list", NamedTextColor.AQUA))
        .append(Component.text(" - List your saved paths.", NamedTextColor.GRAY)));
    player.sendMessage(Component.text()
        .append(Component.text("/path show <name>", NamedTextColor.AQUA))
        .append(Component.text(" - Display a saved path.", NamedTextColor.GRAY)));
    player.sendMessage(Component.text()
        .append(Component.text("/path hide", NamedTextColor.AQUA))
        .append(Component.text(" - Stop displaying the current path.", NamedTextColor.GRAY)));
    }
}