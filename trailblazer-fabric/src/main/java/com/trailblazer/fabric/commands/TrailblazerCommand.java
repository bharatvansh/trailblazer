package com.trailblazer.fabric.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ServerIntegrationBridge;
import com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload;
import com.trailblazer.fabric.networking.payload.c2s.ToggleRecordingPayload;
import com.trailblazer.fabric.networking.payload.c2s.UpdatePathMetadataPayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class TrailblazerCommand {

    private static ClientPathManager pathManager;

    public static void register(ClientPathManager manager) {
        pathManager = manager;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Register the main command
            var trailblazerNode = literal("trailblazer")
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(literal("record").executes(ctx -> toggleRecording(ctx.getSource())))
                .then(literal("list").executes(ctx -> listPaths(ctx.getSource())))
                .then(literal("info")
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> showInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> deletePath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("rename")
                    .then(argument("oldName", StringArgumentType.string())
                    .then(argument("newName", StringArgumentType.string())
                        .executes(ctx -> renamePath(ctx.getSource(), StringArgumentType.getString(ctx, "oldName"), StringArgumentType.getString(ctx, "newName"))))));

            var builtNode = dispatcher.register(trailblazerNode);

            // Register the alias
            dispatcher.register(literal("tbl").redirect(builtNode));
        });
    }

    private static int sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("--- Trailblazer Help ---").formatted(Formatting.GOLD));
        source.sendFeedback(Text.literal("/trailblazer record").formatted(Formatting.YELLOW).append(Text.literal(" - Start or stop recording a path.").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer list").formatted(Formatting.YELLOW).append(Text.literal(" - List all your loaded paths.").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer info <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Get the start and end coordinates of a path.").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer delete <name>").formatted(Formatting.YELLOW).append(Text.literal(" - Delete a path.").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("/trailblazer rename <oldName> <newName>").formatted(Formatting.YELLOW).append(Text.literal(" - Rename a local path.").formatted(Formatting.WHITE)));
        return 1;
    }

    private static int toggleRecording(FabricClientCommandSource source) {
        boolean isRecording = pathManager.isRecording();
        boolean serverAvailable = ServerIntegrationBridge.SERVER_INTEGRATION.isServerSupported();

        if (serverAvailable) {
            // Let the server manage the recording state
            ClientPlayNetworking.send(new ToggleRecordingPayload());
            source.sendFeedback(Text.literal("Requested server to " + (isRecording ? "stop" : "start") + " recording.").formatted(Formatting.GREEN));
        } else {
            // Fallback to local-only recording
            if (isRecording) {
                pathManager.stopRecordingLocal();
                source.sendFeedback(Text.literal("Stopped local recording.").formatted(Formatting.GREEN));
            } else {
                pathManager.startRecordingLocal();
                source.sendFeedback(Text.literal("Started local recording.").formatted(Formatting.GREEN));
            }
        }
        return 1;
    }

    private static int listPaths(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("--- Your Paths ---").formatted(Formatting.GOLD));
        pathManager.getMyPaths().forEach(path -> {
            String origin = pathManager.isServerBacked(path.getPathId()) ? " (Server)" : " (Local)";
            source.sendFeedback(Text.literal(path.getPathName()).formatted(Formatting.YELLOW).append(Text.literal(origin).formatted(Formatting.GRAY)));
        });
        source.sendFeedback(Text.literal("--- Shared With You ---").formatted(Formatting.GOLD));
        pathManager.getSharedPaths().forEach(path -> {
            source.sendFeedback(Text.literal(path.getPathName()).formatted(Formatting.AQUA));
        });
        return 1;
    }

    private static int showInfo(FabricClientCommandSource source, String name) {
        // Search both my paths and shared paths
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

        if (pathManager.isServerBacked(pathId) && ServerIntegrationBridge.SERVER_INTEGRATION.isServerSupported()) {
            ClientPlayNetworking.send(new DeletePathPayload(pathId));
            source.sendFeedback(Text.literal("Requested server to delete path: " + name).formatted(Formatting.GREEN));
        } else {
            pathManager.deletePath(pathId);
            source.sendFeedback(Text.literal("Locally deleted path: " + name).formatted(Formatting.GREEN));
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

        PathData path = pathOpt.get();
        UUID pathId = path.getPathId();

        if (pathManager.isServerBacked(pathId) && ServerIntegrationBridge.SERVER_INTEGRATION.isServerSupported()) {
            // Send an update payload to the server
            ClientPlayNetworking.send(new UpdatePathMetadataPayload(pathId, newName, path.getColorArgb()));
            source.sendFeedback(Text.literal("Requested server to rename '" + oldName + "' to '" + newName + "'.").formatted(Formatting.GREEN));
        } else {
            // Rename locally
            path.setPathName(newName);
            pathManager.onPathUpdated(path); // This marks it as dirty for persistence
            source.sendFeedback(Text.literal("Renamed '" + oldName + "' to '" + newName + "' locally.").formatted(Formatting.GREEN));
        }
        return 1;
    }
}
