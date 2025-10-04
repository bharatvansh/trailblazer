package com.trailblazer.fabric.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.RenderSettingsManager;
import com.trailblazer.fabric.rendering.RenderMode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class TrailblazerCommand {

    private static ClientPathManager pathManager;
    private static RenderSettingsManager renderSettingsManager;
    private static boolean registered = false;

    private TrailblazerCommand() {}

    public static void register(ClientPathManager manager, RenderSettingsManager settingsManager) {
        if (registered) {
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("TrailblazerCommand: register() called again - ignoring duplicate");
            return;
        }
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("TrailblazerCommand: registering client commands");
        pathManager = manager;
        renderSettingsManager = settingsManager;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("TrailblazerCommand: callback invoked, building command tree");

            var recordNode = literal("record")
                .executes(ctx -> toggleRecording(ctx.getSource()))
                .then(literal("start").executes(ctx -> startRecording(ctx.getSource())))
                .then(literal("stop").executes(ctx -> stopRecording(ctx.getSource())))
                .then(literal("cancel").executes(ctx -> cancelRecording(ctx.getSource())))
                .then(literal("status").executes(ctx -> showRecordingStatus(ctx.getSource())));

            var trailblazerNode = literal("trailblazer")
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(literal("help").executes(ctx -> sendHelp(ctx.getSource())))
                .then(recordNode)
                .then(literal("view")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> viewPath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("hide")
                    .executes(ctx -> hideAll(ctx.getSource()))
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> hideOne(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("rendermode")
                    .then(argument("mode", StringArgumentType.string())
                        .suggests((c,b)->suggestRenderModes(b))
                        .executes(ctx -> setRenderMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(literal("list").executes(ctx -> listPaths(ctx.getSource())))
                .then(literal("info")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> showInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> deletePath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("rename")
                    .then(argument("oldName", StringArgumentType.string())
                        .suggests(TrailblazerCommand::suggestLocalPathNames)
                        .then(argument("newName", StringArgumentType.string())
                            .executes(ctx -> renamePath(ctx.getSource(), StringArgumentType.getString(ctx, "oldName"), StringArgumentType.getString(ctx, "newName"))))));

            var builtNode = dispatcher.register(trailblazerNode);
            dispatcher.register(literal("tbl").redirect(builtNode));
            registered = true;
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("TrailblazerCommand: registration complete");
        });
    }

    private static int sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("--- Trailblazer Help ---").formatted(Formatting.GOLD));
        source.sendFeedback(Text.literal("/trailblazer help").formatted(Formatting.YELLOW).append(Text.literal(" - Show this help.").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer record").formatted(Formatting.YELLOW).append(Text.literal(" - Toggle recording (shortcut).\n" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer record start").formatted(Formatting.YELLOW).append(Text.literal(" - Start a new recording." ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer record stop").formatted(Formatting.YELLOW).append(Text.literal(" - Stop and keep current recording." ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer record cancel").formatted(Formatting.YELLOW).append(Text.literal(" - Cancel and discard current recording." ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer record status").formatted(Formatting.YELLOW).append(Text.literal(" - Show current recording status." ).formatted(Formatting.WHITE)));
    source.sendFeedback(Text.literal("/trailblazer view <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Show a path using client renderer (quote names with spaces).").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer hide [name]").formatted(Formatting.YELLOW).append(Text.literal(" - Hide one path or all (no name).\n").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer rendermode <mode>").formatted(Formatting.YELLOW).append(Text.literal(" - Change client render mode. Modes: trail | markers | arrows").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer list").formatted(Formatting.YELLOW).append(Text.literal(" - List your local and shared paths.").formatted(Formatting.WHITE)));
    source.sendFeedback(Text.literal("/trailblazer info <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Get the start and end coordinates of a path (quote names with spaces).").formatted(Formatting.WHITE)));
    source.sendFeedback(Text.literal("/trailblazer delete <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Delete or hide a path locally (quote names with spaces).").formatted(Formatting.WHITE)));
    source.sendFeedback(Text.literal("/trailblazer rename <oldName> <newName>").formatted(Formatting.YELLOW).append(Text.literal(" - Rename a local or imported path (quote names with spaces).").formatted(Formatting.WHITE)));
        return 1;
    }

    private static final Map<String, RenderMode> MODE_ALIASES = Map.ofEntries(
        Map.entry("trail", RenderMode.PARTICLE_TRAIL),
        Map.entry("particle", RenderMode.PARTICLE_TRAIL),
        Map.entry("particles", RenderMode.PARTICLE_TRAIL),
        Map.entry("particle_trail", RenderMode.PARTICLE_TRAIL),
        Map.entry("markers", RenderMode.SPACED_MARKERS),
        Map.entry("marker", RenderMode.SPACED_MARKERS),
        Map.entry("spaced_markers", RenderMode.SPACED_MARKERS),
        Map.entry("arrows", RenderMode.DIRECTIONAL_ARROWS),
        Map.entry("arrow", RenderMode.DIRECTIONAL_ARROWS),
        Map.entry("directional_arrows", RenderMode.DIRECTIONAL_ARROWS)
    );

    private static CompletableFuture<Suggestions> suggestRenderModes(SuggestionsBuilder builder) {
        builder.suggest("trail");
        builder.suggest("markers");
        builder.suggest("arrows");
        return builder.buildFuture();
    }

    private static int viewPath(FabricClientCommandSource source, String name) {
        UUID found = findPathIdByName(name);
        if (found == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }
        pathManager.setPathVisible(found);
        source.sendFeedback(Text.literal("Showing path: " + name).formatted(Formatting.GREEN));
        return 1;
    }

    private static int hideOne(FabricClientCommandSource source, String name) {
        UUID found = findPathIdByName(name);
        if (found == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }
        pathManager.setPathHidden(found);
        source.sendFeedback(Text.literal("Hid path: " + name).formatted(Formatting.YELLOW));
        return 1;
    }

    private static int hideAll(FabricClientCommandSource source) {
        pathManager.hideAllPaths();
        source.sendFeedback(Text.literal("All paths hidden.").formatted(Formatting.YELLOW));
        return 1;
    }

    private static UUID findPathIdByName(String name) {
        for (PathData p : pathManager.getMyPaths()) {
            if (p.getPathName().equalsIgnoreCase(name)) return p.getPathId();
        }
        for (PathData p : pathManager.getSharedPaths()) {
            if (p.getPathName().equalsIgnoreCase(name)) return p.getPathId();
        }
        return null;
    }

    private static int setRenderMode(FabricClientCommandSource source, String modeInput) {
        if (renderSettingsManager == null) {
            source.sendError(Text.literal("Render settings are not available."));
            return 0;
        }
        RenderMode mode = MODE_ALIASES.get(modeInput.toLowerCase());
        if (mode == null) {
            source.sendError(Text.literal("Unknown render mode: " + modeInput));
            return 0;
        }
        renderSettingsManager.setRenderMode(mode);
        MutableText feedback = Text.literal("Render mode set to ").formatted(Formatting.GREEN)
            .append(mode.getDisplayText().copy());
        source.sendFeedback(feedback);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestPathNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        Stream<String> myPaths = pathManager.getMyPaths().stream().map(PathData::getPathName);
        Stream<String> sharedPaths = pathManager.getSharedPaths().stream().map(PathData::getPathName);
        Stream.concat(myPaths, sharedPaths)
            .distinct()
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLocalPathNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        pathManager.getMyPaths().stream()
            .filter(p -> pathManager.isLocalPath(p.getPathId()))
            .map(PathData::getPathName)
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int toggleRecording(FabricClientCommandSource source) {
        boolean isRecording = pathManager.isRecording();

        if (isRecording) {
            pathManager.stopRecordingLocal();
            source.sendFeedback(Text.literal("Stopped local recording.").formatted(Formatting.GREEN));
        } else {
            pathManager.startRecordingLocal();
            source.sendFeedback(Text.literal("Started local recording.").formatted(Formatting.GREEN));
        }
        return 1;
    }

    private static int startRecording(FabricClientCommandSource source) {
        if (pathManager.isRecording()) {
            source.sendError(Text.literal("Already recording. Use /trailblazer record stop or cancel."));
            return 0;
        }
        pathManager.startRecordingLocal();
        source.sendFeedback(Text.literal("Started recording.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int stopRecording(FabricClientCommandSource source) {
        if (!pathManager.isRecording()) {
            source.sendError(Text.literal("No active recording."));
            return 0;
        }
        pathManager.stopRecordingLocal();
        source.sendFeedback(Text.literal("Stopped recording.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int cancelRecording(FabricClientCommandSource source) {
        if (!pathManager.isRecording()) {
            source.sendError(Text.literal("No active recording."));
            return 0;
        }
        pathManager.cancelRecordingLocal();
        source.sendFeedback(Text.literal("Cancelled recording (discarded path)." ).formatted(Formatting.YELLOW));
        return 1;
    }

    private static int showRecordingStatus(FabricClientCommandSource source) {
        boolean isRecording = pathManager.isRecording();
        String label;
        if (isRecording) {
            var path = pathManager.getLocalRecordingPath();
            int points = path != null ? path.getPoints().size() : 0;
            label = "Recording '" + (path != null ? path.getPathName() : "(unknown)") + "' (" + points + " points)";
        } else {
            label = "Not recording.";
        }
        source.sendFeedback(Text.literal(label).formatted(Formatting.GRAY));
        return 1;
    }

    private static int listPaths(FabricClientCommandSource source) {
        List<PathData> localPaths = new ArrayList<>();
        List<PathData> importedPaths = new ArrayList<>();
        List<PathData> serverShares = new ArrayList<>();
        Set<UUID> serverShareIds = new HashSet<>();

        for (PathData path : pathManager.getMyPaths()) {
            PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
            switch (origin) {
                case LOCAL, SERVER_OWNED -> localPaths.add(path);
                case IMPORTED -> importedPaths.add(path);
                case SERVER_SHARED -> {
                    if (serverShareIds.add(path.getPathId())) {
                        serverShares.add(path);
                    }
                }
            }
        }

        for (PathData path : pathManager.getSharedPaths()) {
            PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
            if (origin == PathOrigin.SERVER_SHARED && serverShareIds.add(path.getPathId())) {
                serverShares.add(path);
            }
        }

        source.sendFeedback(Text.literal("--- Your Paths ---").formatted(Formatting.GOLD));
        if (localPaths.isEmpty()) {
            source.sendFeedback(Text.literal("No locally-owned paths.").formatted(Formatting.GRAY));
        } else {
            localPaths.forEach(path -> source.sendFeedback(formatListEntry(path)));
        }

        source.sendFeedback(Text.literal("--- Shared With You ---").formatted(Formatting.GOLD));
        if (importedPaths.isEmpty() && serverShares.isEmpty()) {
            source.sendFeedback(Text.literal("No shared paths loaded.").formatted(Formatting.GRAY));
        } else {
            importedPaths.forEach(path -> source.sendFeedback(formatListEntry(path)));
            serverShares.forEach(path -> source.sendFeedback(formatListEntry(path)));
        }
        return 1;
    }

    private static Text formatListEntry(PathData path) {
        PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
        String originLabel = switch (origin) {
            case LOCAL -> " (Local)";
            case IMPORTED -> {
                String from = path.getOriginOwnerName();
                yield from != null && !from.isBlank() ? " (Imported from " + from + ")" : " (Imported)";
            }
            case SERVER_OWNED -> " (Server copy)";
            case SERVER_SHARED -> " (Server share)";
        };
        return Text.literal(path.getPathName()).formatted(Formatting.YELLOW)
            .append(Text.literal(originLabel).formatted(Formatting.GRAY));
    }

    private static int showInfo(FabricClientCommandSource source, String name) {
        Optional<PathData> pathOpt = pathManager.getMyPaths().stream()
            .filter(p -> p.getPathName().equalsIgnoreCase(name))
            .findFirst()
            .or(() -> pathManager.getSharedPaths().stream()
                .filter(p -> p.getPathName().equalsIgnoreCase(name))
                .findFirst());

        if (pathOpt.isEmpty()) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        PathData path = pathOpt.get();
        java.util.List<com.trailblazer.api.Vector3d> points = path.getPoints();
        if (points.isEmpty()) {
            source.sendFeedback(Text.literal("Path '" + name + "' has no points.").formatted(Formatting.YELLOW));
            return 1;
        }
        com.trailblazer.api.Vector3d start = points.get(0);
        com.trailblazer.api.Vector3d end = points.get(points.size() - 1);

        source.sendFeedback(Text.literal("--- Info for " + name + " ---").formatted(Formatting.GOLD));
        source.sendFeedback(Text.literal("Start: ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.1f, %.1f, %.1f", start.getX(), start.getY(), start.getZ())).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("End:   ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.1f, %.1f, %.1f", end.getX(), end.getY(), end.getZ())).formatted(Formatting.WHITE)));
        return 1;
    }

    private static int deletePath(FabricClientCommandSource source, String name) {
        Optional<PathData> pathOpt = pathManager.getMyPaths().stream()
            .filter(p -> p.getPathName().equalsIgnoreCase(name))
            .findFirst();

        if (pathOpt.isEmpty()) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        PathData path = pathOpt.get();
        UUID pathId = path.getPathId();
        PathOrigin origin = pathManager.getPathOrigin(pathId);

        switch (origin) {
            case LOCAL, IMPORTED -> {
                pathManager.deletePath(pathId);
                source.sendFeedback(Text.literal("Deleted path locally: " + name).formatted(Formatting.GREEN));
            }
            case SERVER_OWNED, SERVER_SHARED -> {
                pathManager.removeServerPath(pathId);
                source.sendFeedback(Text.literal("Removed server-synced path from your list: " + name).formatted(Formatting.YELLOW));
            }
        }
        return 1;
    }

    private static int renamePath(FabricClientCommandSource source, String oldName, String newName) {
        Optional<PathData> pathOpt = pathManager.getMyPaths().stream()
            .filter(p -> p.getPathName().equalsIgnoreCase(oldName))
            .findFirst();

        if (pathOpt.isEmpty()) {
            source.sendError(Text.literal("Path not found: " + oldName));
            return 0;
        }

        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            source.sendError(Text.literal("New name cannot be empty."));
            return 0;
        }

        PathData path = pathOpt.get();
        UUID pathId = path.getPathId();
        PathOrigin origin = pathManager.getPathOrigin(pathId);

        if (origin == PathOrigin.SERVER_OWNED || origin == PathOrigin.SERVER_SHARED) {
            source.sendError(Text.literal("Server-managed paths cannot be renamed locally."));
            return 0;
        }

        boolean nameTaken = pathManager.getMyPaths().stream()
            .anyMatch(p -> !p.getPathId().equals(pathId) && p.getPathName().equalsIgnoreCase(trimmed));
        if (nameTaken) {
            source.sendError(Text.literal("A path with that name already exists."));
            return 0;
        }

        path.setPathName(trimmed);
        pathManager.onPathUpdated(path);
        source.sendFeedback(Text.literal("Renamed '" + oldName + "' to '" + trimmed + "'.").formatted(Formatting.GREEN));
        return 1;
    }
}
