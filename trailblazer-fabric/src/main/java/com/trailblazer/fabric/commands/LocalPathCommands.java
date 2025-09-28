package com.trailblazer.fabric.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client side commands for local path management (no server needed). */
public final class LocalPathCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private LocalPathCommands() {}

    public static void register(ClientPathManager manager) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("tblocal")
                .then(literal("list").executes(ctx -> listPaths(ctx, manager)))
                .then(literal("export")
                    .then(argument("id", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestLocalPathIds(ctx, builder, manager))
                        .executes(ctx -> exportPath(ctx, manager))))
            );
        });
    }

    private static CompletableFuture<Suggestions> suggestLocalPathIds(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder, ClientPathManager manager) {
        manager.getMyPaths().stream()
            .filter(p -> manager.isLocalPath(p.getPathId()))
            .map(p -> p.getPathId().toString())
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int listPaths(CommandContext<FabricClientCommandSource> ctx, ClientPathManager mgr) {
        FabricClientCommandSource src = ctx.getSource();
        int count = 0;
        for (PathData p : mgr.getMyPaths()) {
            src.sendFeedback(Text.literal(p.getPathId()+" :: "+p.getPathName()+" (#"+p.getPoints().size()+")"));
            count++;
        }
        if (count == 0) src.sendError(Text.literal("No local paths."));
        return count;
    }

    private static int exportPath(CommandContext<FabricClientCommandSource> ctx, ClientPathManager mgr) throws CommandSyntaxException {
        String idStr = StringArgumentType.getString(ctx, "id");
        UUID id;
        try { id = UUID.fromString(idStr); } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid UUID"));
            return 0;
        }
        PathData target = mgr.getMyPaths().stream().filter(p -> p.getPathId().equals(id)).findFirst().orElse(null);
        if (target == null) {
            ctx.getSource().sendError(Text.literal("Path not found"));
            return 0;
        }
        Path exportDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("trailblazer_export");
        try { Files.createDirectories(exportDir); } catch (IOException ignored) {}
        Path out = exportDir.resolve(target.getPathName().replaceAll("[^a-zA-Z0-9-_]","_")+"_"+id+".json");
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            GSON.toJson(target, w);
        } catch (IOException e) {
            ctx.getSource().sendError(Text.literal("Failed to export: "+e.getMessage()));
            return 0;
        }
        ctx.getSource().sendFeedback(Text.literal("Exported to "+out.toAbsolutePath()));
        return 1;
    }
}
