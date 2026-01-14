package com.trailblazer.fabric.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.RenderSettingsManager;
import com.trailblazer.fabric.networking.payload.c2s.UpdatePathMetadataPayload;
import com.trailblazer.fabric.rendering.RenderMode;
import com.trailblazer.fabric.sharing.PathShareSender;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TrailblazerCommand {

    private static ClientPathManager pathManager;
    private static RenderSettingsManager renderSettingsManager;
    private static boolean registered = false;

    private TrailblazerCommand() {}

    public static void register(ClientPathManager manager, RenderSettingsManager settingsManager) {
        if (registered) {
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

            com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> trailblazerNode = literal("trailblazer")
                .executes(ctx -> sendHelp(ctx.getSource()))
                // Ordered for client-only preference: record -> list -> view -> hide -> info -> rename -> delete -> color -> share -> rendermode -> help
                .then(recordNode)
                .then(literal("list").executes(ctx -> listPaths(ctx.getSource())))
                .then(literal("view")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> viewPath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("hide")
                    .executes(ctx -> hideAll(ctx.getSource()))
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> hideOne(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("info")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> showInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("rename")
                    .then(argument("oldName", StringArgumentType.string())
                        .suggests(TrailblazerCommand::suggestRenameNames)
                        .then(argument("newName", StringArgumentType.string())
                            .executes(ctx -> renamePath(ctx.getSource(), StringArgumentType.getString(ctx, "oldName"), StringArgumentType.getString(ctx, "newName"))))))
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .executes(ctx -> deletePath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("color")
                    .then(argument("name", StringArgumentType.string())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .then(argument("color", StringArgumentType.string())
                            .suggests(TrailblazerCommand::suggestColorNames)
                            .executes(ctx -> setColor(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "color"))))))
                .then(literal("share")
                    .then(argument("name", StringArgumentType.string())
                        .suggests(TrailblazerCommand::suggestPathNames)
                        .then(argument("players", StringArgumentType.string())
                            .suggests(TrailblazerCommand::suggestPlayerNames)
                            .executes(ctx -> sharePath(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "players"))))))
                .then(literal("rendermode")
                    .then(argument("mode", StringArgumentType.string())
                        .suggests((c,b)->suggestRenderModes(b))
                        .executes(ctx -> setRenderMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(literal("help").executes(ctx -> sendHelp(ctx.getSource())));

            var builtNode = dispatcher.register(trailblazerNode);
            dispatcher.register(literal("tbl").redirect(builtNode));
            registered = true;
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("TrailblazerCommand: registration complete");
        });
    }

    private static int sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("--- Trailblazer Help ---").formatted(Formatting.GOLD));
        source.sendFeedback(Text.literal("/trailblazer record [start|stop|cancel|status]").formatted(Formatting.YELLOW).append(Text.literal(" - Recording commands (use 'record' alone to toggle)" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer list").formatted(Formatting.YELLOW).append(Text.literal(" - List your paths").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer view <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Show a path" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer hide [name]").formatted(Formatting.YELLOW).append(Text.literal(" - Hide path(s)" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer info <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Get path coordinates" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer rename <old> <new>").formatted(Formatting.YELLOW).append(Text.literal(" - Rename a path" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer delete <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Delete a path" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer color <name> <color>").formatted(Formatting.YELLOW).append(Text.literal(" - Change path color" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer share <name> <players>").formatted(Formatting.YELLOW).append(Text.literal(" - Share path with players" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer rendermode <trail|markers|arrows>").formatted(Formatting.YELLOW).append(Text.literal(" - Change render mode" ).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("Tip: Press M for UI, R to toggle recording, G to cycle render mode").formatted(Formatting.GRAY));
        return 1;
    }

    private static final Map<String, RenderMode> MODE_ALIASES = Map.ofEntries(
        Map.entry("solid", RenderMode.SOLID_LINE),
        Map.entry("solid_line", RenderMode.SOLID_LINE),
        Map.entry("trail", RenderMode.DASHED_LINE),
        Map.entry("dashed", RenderMode.DASHED_LINE),
        Map.entry("dash", RenderMode.DASHED_LINE),
        Map.entry("dashed_line", RenderMode.DASHED_LINE),
        Map.entry("markers", RenderMode.SPACED_MARKERS),
        Map.entry("marker", RenderMode.SPACED_MARKERS),
        Map.entry("spaced_markers", RenderMode.SPACED_MARKERS),
        Map.entry("arrows", RenderMode.DIRECTIONAL_ARROWS),
        Map.entry("arrow", RenderMode.DIRECTIONAL_ARROWS),
        Map.entry("directional_arrows", RenderMode.DIRECTIONAL_ARROWS)
    );

    private static CompletableFuture<Suggestions> suggestRenderModes(SuggestionsBuilder builder) {
        builder.suggest("solid");
        builder.suggest("trail");
        builder.suggest("markers");
        builder.suggest("arrows");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestColorNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        com.trailblazer.api.PathColors.getColorNames().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        var handler = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            handler.getPlayerList().stream()
                .map(p -> p.getProfile().name())
                .forEach(builder::suggest);
        }
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

    private static int setColor(FabricClientCommandSource source, String name, String colorArg) {
        UUID found = findPathIdByName(name);
        if (found == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        java.util.Optional<Integer> parsed = com.trailblazer.api.PathColors.parse(colorArg);
        if (parsed.isEmpty()) {
            source.sendError(Text.literal("Invalid color. Use a name or #RRGGBB."));
            return 0;
        }
        int color = parsed.get();

        // Update local copy
        PathData path = null;
        for (PathData p : pathManager.getMyPaths()) if (p.getPathId().equals(found)) path = p;
        if (path == null) {
            for (PathData p : pathManager.getSharedPaths()) if (p.getPathId().equals(found)) path = p;
        }
        if (path == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        path.setColorArgb(color);
        pathManager.onPathUpdated(path);

        // If this is a server-owned path, send metadata update to server so it persists
        com.trailblazer.fabric.ClientPathManager.PathOrigin origin = pathManager.getPathOrigin(found);
        if (origin == com.trailblazer.fabric.ClientPathManager.PathOrigin.SERVER_OWNED && ClientPlayNetworking.canSend(UpdatePathMetadataPayload.ID)) {
            try {
                ClientPlayNetworking.send(new UpdatePathMetadataPayload(found, path.getPathName(), color));
            } catch (Exception ignored) {}
        }

        source.sendFeedback(Text.literal("Color set to " + com.trailblazer.api.PathColors.nameOrHex(color)).formatted(Formatting.GREEN));
        return 1;
    }

    private static int sharePath(FabricClientCommandSource source, String name, String playersCsv) {
        UUID found = findPathIdByName(name);
        if (found == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        PathData path = null;
        for (PathData p : pathManager.getMyPaths()) if (p.getPathId().equals(found)) path = p;
        if (path == null) {
            for (PathData p : pathManager.getSharedPaths()) if (p.getPathId().equals(found)) path = p;
        }
        if (path == null) {
            source.sendError(Text.literal("Path not found: " + name));
            return 0;
        }

        // resolve player names (comma separated)
        String[] parts = playersCsv.split(",");
        List<UUID> recipients = new ArrayList<>();
        var handler = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            source.sendError(Text.literal("No network handler available."));
            return 0;
        }
        List<String> matchedNames = new ArrayList<>();
        for (String p : parts) {
            String nameTrim = p.trim();
            if (nameTrim.isEmpty()) continue;
            for (var entry : handler.getPlayerList()) {
                if (entry.getProfile().name().equalsIgnoreCase(nameTrim)) {
                    recipients.add(entry.getProfile().id());
                    matchedNames.add(entry.getProfile().name());
                    break;
                }
            }
        }

        if (recipients.isEmpty()) {
            source.sendError(Text.literal("No valid online players found to share with."));
            return 0;
        }

        try {
            PathShareSender.sharePath(path, recipients);
            source.sendFeedback(Text.literal("Share request sent for '" + name + "' to: " + String.join(", ", matchedNames)).formatted(Formatting.GREEN));
        } catch (Exception ex) {
            source.sendError(Text.literal("Failed to send share request: " + ex.getMessage()));
            return 0;
        }

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

    private static CompletableFuture<Suggestions> suggestRenameNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        pathManager.getMyPaths().stream()
            .filter(p -> {
                var origin = pathManager.getPathOrigin(p.getPathId());
                return origin == PathOrigin.LOCAL || origin == PathOrigin.SERVER_OWNED;
            })
            .map(PathData::getPathName)
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPathNames(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        Stream<String> myPaths = pathManager.getMyPaths().stream().map(PathData::getPathName);
        Stream<String> sharedPaths = pathManager.getSharedPaths().stream().map(PathData::getPathName);
        Stream.concat(myPaths, sharedPaths)
            .distinct()
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int toggleRecording(FabricClientCommandSource source) {
        boolean isRecording = pathManager.isRecording();
        boolean useServer = pathManager.shouldUseServerRecording();
        
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("/trailblazer record (toggle) command executed: isRecording={}, useServerRecording={}", isRecording, useServer);

        if (isRecording) {
            if (useServer) {
                pathManager.sendStopRecordingRequest(true);
                source.sendFeedback(Text.literal("Stopping server-side recording...").formatted(Formatting.GREEN));
            } else {
                pathManager.stopRecordingLocal();
                source.sendFeedback(Text.literal("Stopped local recording.").formatted(Formatting.GREEN));
            }
        } else {
            if (useServer) {
                com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Toggle command: Using SERVER recording");
                pathManager.sendStartRecordingRequest(null);
                source.sendFeedback(Text.literal("Started server-side recording.").formatted(Formatting.GREEN));
            } else {
                com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Toggle command: Using LOCAL recording");
                pathManager.startRecordingLocal();
                source.sendFeedback(Text.literal("Started local recording.").formatted(Formatting.GREEN));
            }
        }
        return 1;
    }

    private static int startRecording(FabricClientCommandSource source) {
        if (pathManager.isRecording()) {
            source.sendError(Text.literal("Already recording. Use /trailblazer record stop or cancel."));
            return 0;
        }
        
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("/trailblazer record start command executed");
        boolean useServer = pathManager.shouldUseServerRecording();
        
        if (useServer) {
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Command: Using SERVER recording");
            pathManager.sendStartRecordingRequest(null);
            source.sendFeedback(Text.literal("Started server-side recording.").formatted(Formatting.GREEN));
        } else {
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.info("Command: Using LOCAL recording");
            pathManager.startRecordingLocal();
            source.sendFeedback(Text.literal("Started local recording.").formatted(Formatting.GREEN));
        }
        return 1;
    }

    private static int stopRecording(FabricClientCommandSource source) {
        if (!pathManager.isRecording()) {
            source.sendError(Text.literal("No active recording."));
            return 0;
        }
        
        if (pathManager.shouldUseServerRecording()) {
            pathManager.sendStopRecordingRequest(true);
            source.sendFeedback(Text.literal("Stopping server-side recording...").formatted(Formatting.GREEN));
        } else {
            pathManager.stopRecordingLocal();
            source.sendFeedback(Text.literal("Stopped local recording.").formatted(Formatting.GREEN));
        }
        return 1;
    }

    private static int cancelRecording(FabricClientCommandSource source) {
        if (!pathManager.isRecording()) {
            source.sendError(Text.literal("No active recording."));
            return 0;
        }
        
        if (pathManager.shouldUseServerRecording()) {
            pathManager.sendStopRecordingRequest(false);
            source.sendFeedback(Text.literal("Cancelling server-side recording (discarded path).").formatted(Formatting.YELLOW));
        } else {
            pathManager.cancelRecordingLocal();
            source.sendFeedback(Text.literal("Cancelled local recording (discarded path).").formatted(Formatting.YELLOW));
        }
        return 1;
    }

    private static int showRecordingStatus(FabricClientCommandSource source) {
        boolean isRecording = pathManager.isRecording();
        String label;
        if (isRecording) {
            // Try to get recording path (works for both local and server recordings)
            var path = pathManager.getRecordingPath();
            if (path == null) {
                // Fallback: check if we have live path updates (server recording)
                var livePath = pathManager.getLivePath();
                int points = livePath != null ? livePath.getPoints().size() : 0;
                label = "Recording (server) (" + points + " points)";
            } else {
                int points = path.getPoints().size();
                String pathName = path.getPathName();
                boolean isServerRecording = pathManager.shouldUseServerRecording();
                label = "Recording '" + pathName + "' (" + points + " points)" + (isServerRecording ? " [Server]" : " [Local]");
            }
        } else {
            label = "Not recording.";
        }
        source.sendFeedback(Text.literal(label).formatted(Formatting.GRAY));
        return 1;
    }

    private static int listPaths(FabricClientCommandSource source) {
        List<PathData> localPaths = new ArrayList<>();
        List<PathData> serverShares = new ArrayList<>();
        Set<UUID> serverShareIds = new HashSet<>();

        for (PathData path : pathManager.getMyPaths()) {
            PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
            switch (origin) {
                case LOCAL, SERVER_OWNED -> localPaths.add(path);
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
        if (serverShares.isEmpty()) {
            source.sendFeedback(Text.literal("No shared paths loaded.").formatted(Formatting.GRAY));
        } else {
            serverShares.forEach(path -> source.sendFeedback(formatListEntry(path)));
        }
        return 1;
    }

    private static Text formatListEntry(PathData path) {
        PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
        String originLabel = switch (origin) {
            case LOCAL -> " (Local)";
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
            case LOCAL -> {
                pathManager.deletePath(pathId);
                source.sendFeedback(Text.literal("Deleted path locally: " + name).formatted(Formatting.GREEN));
            }
            case SERVER_OWNED, SERVER_SHARED -> {
                // If server supports delete payload, request server deletion for SERVER_OWNED paths.
                if (origin == PathOrigin.SERVER_OWNED && ClientPlayNetworking.canSend(com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload.ID)) {
                    try {
                        ClientPlayNetworking.send(new com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload(pathId));
                        source.sendFeedback(Text.literal("Requested server deletion for: " + name).formatted(Formatting.YELLOW));
                    } catch (Exception ignored) {
                        // Fallback: remove locally from client list if send fails
                        pathManager.removeServerPath(pathId);
                        source.sendFeedback(Text.literal("Removed server-synced path from your list: " + name).formatted(Formatting.YELLOW));
                    }
                } else {
                    // For server-shared (or when server delete not supported) just remove client-side entry
                    pathManager.removeServerPath(pathId);
                    source.sendFeedback(Text.literal("Removed server-synced path from your list: " + name).formatted(Formatting.YELLOW));
                }
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

        if (origin == PathOrigin.SERVER_SHARED) {
            source.sendError(Text.literal("Server-shared paths cannot be renamed locally."));
            return 0;
        }

        if (origin == PathOrigin.SERVER_OWNED) {
            // Request server-side rename so server persists authoritative name
            if (ClientPlayNetworking.canSend(UpdatePathMetadataPayload.ID)) {
                try {
                    // send current color back along with requested name
                    ClientPlayNetworking.send(new UpdatePathMetadataPayload(pathId, trimmed, path.getColorArgb()));
                    source.sendFeedback(Text.literal("Requested server rename for '" + oldName + "' -> '" + trimmed + "'.").formatted(Formatting.GREEN));
                    return 1;
                } catch (Exception ex) {
                    source.sendError(Text.literal("Failed to send rename request: " + ex.getMessage()));
                    return 0;
                }
            } else {
                source.sendError(Text.literal("Server does not support remote rename."));
                return 0;
            }
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
