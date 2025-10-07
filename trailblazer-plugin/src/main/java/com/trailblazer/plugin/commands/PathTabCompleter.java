package com.trailblazer.plugin.commands;

import com.trailblazer.plugin.PathDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PathTabCompleter implements TabCompleter {

    private final PathDataManager pathDataManager;
    // Include 'help' so tab completion suggests it for server-side users as well
    // Ordered to match preferred server-side command order: record -> list -> view -> hide -> info -> rename -> delete -> color -> spacing -> share -> rendermode -> help
    private static final List<String> SUB_COMMANDS = List.of("record", "list", "view", "hide", "info", "rename", "delete", "color", "spacing", "share", "rendermode", "help");
    private static final List<String> RECORD_SUB = List.of("start","stop","cancel","status");

    public PathTabCompleter(PathDataManager pathDataManager) {
        this.pathDataManager = pathDataManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> suggestions;
            try {
                var plugin = com.trailblazer.plugin.TrailblazerPlugin.getInstance();
                boolean modded = plugin.getServerPacketHandler().isModdedPlayer(player);
                if (modded) {
                    // For modded players, provide the client command list + server 'share' and 'color'.
                    suggestions = new ArrayList<>(List.of("record", "view", "hide", "rendermode", "list", "info", "delete", "rename", "help", "share", "color"));
                } else {
                    // For unmodded players, provide the full server command list.
                    suggestions = new ArrayList<>(SUB_COMMANDS);
                }
            } catch (Exception ignored) {
                suggestions = new ArrayList<>(SUB_COMMANDS);
            }
            return StringUtil.copyPartialMatches(args[0], suggestions, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
        case "view":
        case "hide":
        case "delete":
        case "rename":
        case "info":
            var all = pathDataManager.loadPaths(player.getUniqueId()).stream();
            var suggestions = com.trailblazer.api.PathNameMatcher.getSuggestions(all, args[1], 50).stream()
                .map(n -> n.contains(" ") ? ('"' + n + '"') : n)
                .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
                case "rendermode":
                    return StringUtil.copyPartialMatches(args[1], List.of("trail", "arrows"), new ArrayList<>());
        case "color":
            var colorSuggestions = com.trailblazer.api.PathNameMatcher.getSuggestions(pathDataManager.loadPaths(player.getUniqueId()).stream(), args[1], 50).stream()
                .map(n -> n.contains(" ") ? ('"' + n + '"') : n)
                .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], colorSuggestions, new ArrayList<>());
                case "record":
                    return StringUtil.copyPartialMatches(args[1], RECORD_SUB, new ArrayList<>());
                case "share":
            var shareSuggestions = com.trailblazer.api.PathNameMatcher.getSuggestions(pathDataManager.loadPaths(player.getUniqueId()).stream(), args[1], 50).stream()
                .map(n -> n.contains(" ") ? ('"' + n + '"') : n)
                .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], shareSuggestions, new ArrayList<>());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("color")) {
            // Suggest palette color names
            List<String> names = new ArrayList<>(com.trailblazer.api.PathColors.getColorNames());
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }

        // share player name suggestions after path + players list
        if (args.length == 3 && args[0].equalsIgnoreCase("share")) {
            String current = args[2];
            int comma = current.lastIndexOf(',');
            final String prefixPart;
            final String partial;
            if (comma >= 0) {
                prefixPart = current.substring(0, comma + 1); // keep trailing comma
                partial = current.substring(comma + 1).trim();
            } else {
                prefixPart = "";
                partial = current;
            }
            List<String> online = new ArrayList<>();
            try {
                var plugin = com.trailblazer.plugin.TrailblazerPlugin.getInstance();
                plugin.getServer().getOnlinePlayers().forEach(p -> online.add(p.getName()));
            } catch (Exception ignored) {}
            List<String> matches = StringUtil.copyPartialMatches(partial, online, new ArrayList<>());
            if (!prefixPart.isEmpty()) {
                matches = matches.stream().map(s -> prefixPart + s).collect(Collectors.toList());
            }
            return matches;
        }

        return new ArrayList<>(); // No suggestions
    }
}