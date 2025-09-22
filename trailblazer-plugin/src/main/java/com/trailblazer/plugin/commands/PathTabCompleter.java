package com.trailblazer.plugin.commands;

import com.trailblazer.api.PathData;
import com.trailblazer.plugin.PathDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles tab-completion for the /path command to provide contextual suggestions.
 */
public class PathTabCompleter implements TabCompleter {

    private final PathDataManager dataManager;
    private static final List<String> MAIN_COMMANDS = Arrays.asList("record", "list", "show", "hide");
    private static final List<String> RECORD_SUBCOMMANDS = Arrays.asList("start", "stop");

    public PathTabCompleter(PathDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList(); // No suggestions for the console
        }
        Player player = (Player) sender;

        // This list will hold our potential suggestions.
        final List<String> completions = new ArrayList<>();

        // --- Logic for the FIRST argument ---
        if (args.length == 1) {
            // Suggest main commands (record, list, etc.) that start with what the user has typed.
            StringUtil.copyPartialMatches(args[0], MAIN_COMMANDS, completions);
        }
        // --- Logic for the SECOND argument ---
        else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("record")) {
                // If the first command is "record", suggest "start" or "stop".
                StringUtil.copyPartialMatches(args[1], RECORD_SUBCOMMANDS, completions);
            } else if (subCommand.equals("show")) {
                // If the first command is "show", suggest the names of their saved paths.
                List<PathData> paths = dataManager.loadPaths(player.getUniqueId());
                List<String> pathNames = paths.stream().map(PathData::getPathName).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], pathNames, completions);
            }
        }

        // Sort the suggestions alphabetically for a clean look.
        Collections.sort(completions);
        return completions;
    }
}