package com.trailblazer.plugin.commands;

import com.trailblazer.api.PathData;
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

import java.util.List;
import java.util.Optional;

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
            default:
                sendHelpMessage(player);
                break;
        }
        return true;
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
            player.sendMessage(Component.text("Usage: /path share <path-name> <player-name>", NamedTextColor.RED));
            return;
        }

        String pathName = args[1];
        String targetPlayerName = args[2];

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is not online.", NamedTextColor.RED));
            return;
        }

        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot share a path with yourself.", NamedTextColor.RED));
            return;
        }

        Optional<PathData> pathOpt = pathDataManager.loadPaths(player.getUniqueId()).stream()
                .filter(p -> p.getPathName().equalsIgnoreCase(pathName))
                .findFirst();

        if (pathOpt.isPresent()) {
            PathData path = pathOpt.get();
            if (path.getOwnerUUID().equals(player.getUniqueId())) {
                boolean isTargetModded = plugin.getServerPacketHandler().isModdedPlayer(targetPlayer);

                if (isTargetModded) {
                    // The target has the client mod, send the custom packet.
                    plugin.getServerPacketHandler().sendSharePath(targetPlayer, path);
                    targetPlayer.sendMessage(Component.text(player.getName() + " shared the path '" + path.getPathName() + "' with you. It's available in your shared paths menu.", NamedTextColor.AQUA));
                } else {
                    // The target does not have the mod, handle it entirely on the server.
                    // 1. Save the path to the target player's data file so they can manage it.
                    pathDataManager.savePath(targetPlayer.getUniqueId(), path);
                    // 2. Start rendering the path for them using the server-side fallback renderer.
                    plugin.getPathRendererManager().startRendering(targetPlayer, path);
                    // 3. Send them a helpful message explaining how to manage the path.
                    targetPlayer.sendMessage(Component.text(player.getName() + " shared the path '" + path.getPathName() + "' with you.", NamedTextColor.AQUA));
                    targetPlayer.sendMessage(Component.text("It is now being displayed. Use '/path hide' to hide it or '/path view " + path.getPathName() + "' to see it again.", NamedTextColor.GRAY));
                }

                // Notify the sender that the share was successful.
                player.sendMessage(Component.text("Path '" + pathName + "' shared with " + targetPlayerName + ".", NamedTextColor.GREEN));
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
        player.sendMessage(Component.text("/path share <path> <player>", NamedTextColor.YELLOW).append(Component.text(" - Share a path with another player", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/path rendermode <mode>", NamedTextColor.YELLOW).append(Component.text(" - Change fallback render mode (for non-mod users)", NamedTextColor.WHITE)));
    }
}