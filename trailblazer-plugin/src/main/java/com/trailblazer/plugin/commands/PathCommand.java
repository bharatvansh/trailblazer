package com.trailblazer.plugin.commands;

import com.trailblazer.api.PathData;
import com.trailblazer.api.PathColors;
import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.TrailblazerPlugin;
import com.trailblazer.plugin.rendering.PlayerRenderSettingsManager;
import com.trailblazer.plugin.rendering.RenderMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class PathCommand implements CommandExecutor {

    private final TrailblazerPlugin plugin;
    private final com.trailblazer.plugin.PathDataManager pathDataManager;
    private final PlayerRenderSettingsManager renderSettingsManager;

    public PathCommand(TrailblazerPlugin plugin) {
        this.plugin = plugin;
        this.pathDataManager = plugin.getPathDataManager();
        this.renderSettingsManager = plugin.getPlayerRenderSettingsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by a player.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "view":
                handleView(player, args);
                break;
            case "hide":
                handleHide(player, args);
                break;
            case "delete":
                handleDelete(player, args);
                break;
            case "rename":
                handleRename(player, args);
                break;
            case "rendermode":
                handleRenderMode(player, args);
                break;
            case "share":
                handleShare(player, args);
                break;
            case "color":
                handleColor(player, args);
                break;
            case "record":
                handleRecord(player, args);
                break;
            default:
                sendHelpMessage(player);
                break;
        }
        return true;
    }

    /**
     * Handles server-side recording when a player has NO client mod installed.
     * Usage: /path record start <name?>  OR  /path record stop
     */
    private void handleRecord(Player player, String[] args) {
        boolean hasClientMod = plugin.getServerPacketHandler().isModdedPlayer(player);
        if (hasClientMod) {
            player.sendMessage(Component.text("You have the client mod installed. Use the keybind or in-game UI to record.", NamedTextColor.YELLOW));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /path record <start|stop> [name]", NamedTextColor.RED));
            return;
        }
        String action = args[1].toLowerCase();
        var recordingManager = plugin.getPathRecordingManager();
        switch (action) {
            case "start": {
                if (recordingManager.isRecording(player)) {
                    player.sendMessage(Component.text("You are already recording a path.", NamedTextColor.RED));
                    return;
                }
                String name = (args.length >= 3) ? args[2] : ("Path_" + System.currentTimeMillis());
                boolean started = recordingManager.startRecording(player);
                if (started) {
                    player.sendMessage(Component.text("Recording started: " + name, NamedTextColor.GREEN));
                    // Store a provisional path container in player metadata? We will build final PathData on stop using gathered points.
                    // For simplicity just remember chosen name in a lightweight map in the PathRecordingManager? (Not implemented yet)
                    // So embed name later by reusing provided name here; we pass it back via a temporary attribute.
                    player.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "trailblazer_record_name"), org.bukkit.persistence.PersistentDataType.STRING, name);
                } else {
                    player.sendMessage(Component.text("Failed to start recording (already active).", NamedTextColor.RED));
                }
                break; }
            case "stop": {
                if (!recordingManager.isRecording(player)) {
                    player.sendMessage(Component.text("You are not currently recording.", NamedTextColor.RED));
                    return;
                }
                java.util.List<Vector3d> points = recordingManager.stopRecording(player);
                if (points == null || points.isEmpty()) {
                    player.sendMessage(Component.text("No points recorded, nothing saved.", NamedTextColor.YELLOW));
                    return;
                }
                String storedName = player.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "trailblazer_record_name"), org.bukkit.persistence.PersistentDataType.STRING);
                if (storedName == null) storedName = "Path_" + System.currentTimeMillis();
                java.util.UUID pathId = java.util.UUID.randomUUID();
                PathData data = new PathData(pathId, storedName, player.getUniqueId(), player.getName(), System.currentTimeMillis(), player.getWorld().getName(), points, PathColors.assignColorFor(pathId));
                pathDataManager.savePath(data);
                player.sendMessage(Component.text("Recording stopped. Saved path '" + storedName + "' with " + points.size() + " points.", NamedTextColor.GREEN));
                // Immediately render it using fallback server renderer for non-modded players
                plugin.getPathRendererManager().startRendering(player, data);
                break; }
            default:
                player.sendMessage(Component.text("Usage: /path record <start|stop> [name]", NamedTextColor.RED));
        }
    }

    private void handleColor(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /path color <name> <colorName|#RRGGBB>", NamedTextColor.RED));
            return;
        }
        String pathName = args[1];
        String colorArg = args[2];
        List<PathData> paths = pathDataManager.loadPaths(player.getUniqueId());
        Optional<PathData> pathOpt = paths.stream().filter(p -> p.getPathName().equalsIgnoreCase(pathName)).findFirst();
        if (pathOpt.isEmpty()) {
            player.sendMessage(Component.text("Path '" + pathName + "' not found.", NamedTextColor.RED));
            return;
        }
        PathData path = pathOpt.get();
        java.util.Optional<Integer> parsed = com.trailblazer.api.PathColors.parse(colorArg);
        if (parsed.isEmpty()) {
            player.sendMessage(Component.text("Invalid color. Use a name or #RRGGBB.", NamedTextColor.RED));
            return;
        }
        path.setColorArgb(parsed.get());
        // Persist by rewriting file (reuse rename logic pattern)
        pathDataManager.savePath(path);
        player.sendMessage(Component.text("Color for path '" + path.getPathName() + "' set to " + com.trailblazer.api.PathColors.nameOrHex(path.getColorArgb()), NamedTextColor.GREEN));
        // If currently rendering on server fallback, re-start to apply new color (will matter after server side color usage implemented)
        plugin.getPathRendererManager().startRendering(player, path);
    }

    private void handleView(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /path view <name>", NamedTextColor.RED));
            return;
        }
        String pathName = args[1];
        List<PathData> paths = pathDataManager.loadPaths(player.getUniqueId());
        Optional<PathData> pathOpt = paths.stream().filter(p -> p.getPathName().equalsIgnoreCase(pathName)).findFirst();

        if (pathOpt.isPresent()) {
            plugin.getPathRendererManager().startRendering(player, pathOpt.get());
            player.sendMessage(Component.text("Showing path: " + pathName, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Path '" + pathName + "' not found.", NamedTextColor.RED));
        }
    }

    private void handleHide(Player player, String[] args) {
        plugin.getPathRendererManager().stopRendering(player);
        player.sendMessage(Component.text("Path hidden.", NamedTextColor.GREEN));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /path delete <name>", NamedTextColor.RED));
            return;
        }
        String pathName = args[1];
        Optional<PathData> pathOpt = pathDataManager.loadPaths(player.getUniqueId()).stream()
            .filter(p -> p.getPathName().equalsIgnoreCase(pathName))
            .findFirst();

        if (pathOpt.isPresent()) {
            // A player can remove any path from their list, whether they own it or it was shared with them.
            pathDataManager.deletePath(player.getUniqueId(), pathOpt.get().getPathId());
            player.sendMessage(Component.text("Path '" + pathName + "' has been removed from your list.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Path '" + pathName + "' not found in your list.", NamedTextColor.RED));
        }
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /path rename <oldName> <newName>", NamedTextColor.RED));
            return;
        }
        String oldName = args[1];
        String newName = args[2];
        List<PathData> paths = pathDataManager.loadPaths(player.getUniqueId());

        // Check if a path with the new name already exists to avoid duplicates.
        if (paths.stream().anyMatch(p -> p.getPathName().equalsIgnoreCase(newName))) {
            player.sendMessage(Component.text("A path with the name '" + newName + "' already exists.", NamedTextColor.RED));
            return;
        }

        Optional<PathData> pathOpt = paths.stream().filter(p -> p.getPathName().equalsIgnoreCase(oldName)).findFirst();

        if (pathOpt.isPresent()) {
            // A player can rename any path in their list. For shared paths, this is just a local alias.
            pathDataManager.renamePath(player.getUniqueId(), pathOpt.get().getPathId(), newName);
            player.sendMessage(Component.text("Path '" + oldName + "' renamed to '" + newName + "'.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Path '" + oldName + "' not found.", NamedTextColor.RED));
        }
    }

    private void handleRenderMode(Player player, String[] args) {
        if (plugin.getServerPacketHandler().isModdedPlayer(player)) {
             player.sendMessage(Component.text("You have the client mod! Please use the 'G' key to change render modes.", NamedTextColor.YELLOW));
             return;
        }

        if (args.length < 2) {
            RenderMode currentMode = renderSettingsManager.getRenderMode(player);
            player.sendMessage(Component.text("Your current render mode is " + currentMode.name() + ".", NamedTextColor.GRAY));
            // Updated usage message
            player.sendMessage(Component.text("Usage: /path rendermode <PARTICLE_TRAIL | SPACED_MARKERS | DIRECTIONAL_ARROWS>", NamedTextColor.RED));
            return;
        }

        String modeName = args[1].toUpperCase();
        Optional<RenderMode> modeOpt = RenderMode.fromString(modeName);

        if (modeOpt.isPresent()) {
            renderSettingsManager.setRenderMode(player, modeOpt.get());
            player.sendMessage(Component.text("Render mode set to " + modeOpt.get().name(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Invalid render mode: " + modeName, NamedTextColor.RED));
        }
    }

    private void handleShare(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /path share <path-name> <player1,player2,...>", NamedTextColor.RED));
            return;
        }

        String pathName = args[1];
        List<String> targetPlayerNames = Arrays.asList(args[2].split(","));

        List<Player> targetPlayers = targetPlayerNames.stream()
                .map(name -> plugin.getServer().getPlayer(name.trim()))
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toList());

        if (targetPlayers.isEmpty()) {
            player.sendMessage(Component.text("No valid online players found to share with.", NamedTextColor.RED));
            return;
        }

        Optional<PathData> pathOpt = pathDataManager.loadPaths(player.getUniqueId()).stream()
                .filter(p -> p.getPathName().equalsIgnoreCase(pathName))
                .findFirst();

        if (pathOpt.isPresent()) {
            PathData path = pathOpt.get();
            if (path.getOwnerUUID().equals(player.getUniqueId())) {
                List<UUID> sharedWithList = path.getSharedWith();
                for (Player targetPlayer : targetPlayers) {
                    if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("You cannot share a path with yourself.", NamedTextColor.YELLOW));
                        continue;
                    }
                    if (!sharedWithList.contains(targetPlayer.getUniqueId())) {
                        sharedWithList.add(targetPlayer.getUniqueId());
                    }

                    boolean isTargetModded = plugin.getServerPacketHandler().isModdedPlayer(targetPlayer);

                    if (isTargetModded) {
                        plugin.getServerPacketHandler().sendSharePath(targetPlayer, path);
                        targetPlayer.sendMessage(Component.text(player.getName() + " shared the path '" + path.getPathName() + "' with you. It's available in your shared paths menu.", NamedTextColor.AQUA));
                    } else {
                        pathDataManager.savePath(path);
                        plugin.getPathRendererManager().startRendering(targetPlayer, path);
                        targetPlayer.sendMessage(Component.text(player.getName() + " shared the path '" + path.getPathName() + "' with you.", NamedTextColor.AQUA));
                        targetPlayer.sendMessage(Component.text("It is now being displayed. Use '/path hide' to hide it or '/path view " + path.getPathName() + "' to see it again.", NamedTextColor.GRAY));
                    }
                }
                pathDataManager.savePath(path);
                player.sendMessage(Component.text("Path '" + pathName + "' shared with " + targetPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) + ".", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("You can only share paths that you own.", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("Path '" + pathName + "' not found.", NamedTextColor.RED));
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(Component.text("--- Trailblazer Help ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/path view <name>", NamedTextColor.YELLOW).append(Component.text(" - Show a path", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path hide", NamedTextColor.YELLOW).append(Component.text(" - Hide the current path", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path delete <name>", NamedTextColor.YELLOW).append(Component.text(" - Delete a path you own", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path rename <old> <new>", NamedTextColor.YELLOW).append(Component.text(" - Rename a path you own", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path share <path> <player1,player2,...>", NamedTextColor.YELLOW).append(Component.text(" - Share a path with other players", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path rendermode <mode>", NamedTextColor.YELLOW).append(Component.text(" - Change fallback render mode (for non-mod users)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path color <name> <color>", NamedTextColor.YELLOW).append(Component.text(" - Change stored color for a path", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path record start [name]", NamedTextColor.YELLOW).append(Component.text(" - Begin server-side recording (no client mod)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path record stop", NamedTextColor.YELLOW).append(Component.text(" - Finish recording & save", NamedTextColor.WHITE)));
    }
}