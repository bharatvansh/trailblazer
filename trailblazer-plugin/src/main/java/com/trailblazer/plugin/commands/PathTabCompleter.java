package com.trailblazer.plugin.commands;

import com.trailblazer.plugin.PathDataManager;
import com.trailblazer.plugin.rendering.RenderMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PathTabCompleter implements TabCompleter {

    private final PathDataManager pathDataManager;
    private static final List<String> SUB_COMMANDS = List.of("view", "hide", "delete", "rename", "rendermode", "share", "color", "info");
    // This will now correctly reflect the new RenderMode names
    private static final List<String> RENDER_MODES = Arrays.stream(RenderMode.values())
            .map(Enum::name)
            .collect(Collectors.toList());

    public PathTabCompleter(PathDataManager pathDataManager) {
        this.pathDataManager = pathDataManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "view":
                case "delete":
                case "rename":
                case "info":
                    List<String> pathNames = pathDataManager.loadPaths(player.getUniqueId()).stream()
                            .map(com.trailblazer.api.PathData::getPathName)
                            .collect(Collectors.toList());
                    return StringUtil.copyPartialMatches(args[1], pathNames, new ArrayList<>());
                case "rendermode":
                    return StringUtil.copyPartialMatches(args[1], RENDER_MODES, new ArrayList<>());
                case "color":
                    List<String> colorPathNames = pathDataManager.loadPaths(player.getUniqueId()).stream()
                            .map(com.trailblazer.api.PathData::getPathName)
                            .collect(Collectors.toList());
                    return StringUtil.copyPartialMatches(args[1], colorPathNames, new ArrayList<>());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("color")) {
            // Suggest palette color names
            List<String> names = List.of("red","orange","yellow","green","cyan","blue","purple","pink","white");
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }

        return new ArrayList<>(); // No suggestions
    }
}