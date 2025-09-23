package com.trailblazer.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;

public class PathDataManager {

    private final File dataFolder;
    private final Gson gson;

    public PathDataManager(TrailblazerPlugin plugin) {
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void savePath(UUID playerUUID, PathData path) {
        File playerFolder = new File(dataFolder, playerUUID.toString());
        if (!playerFolder.exists() && !playerFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create player data folder for " + playerUUID);
            return;
        }

        File pathFile = new File(playerFolder, path.getPathId().toString() + ".json");
        try (FileWriter writer = new FileWriter(pathFile)) {
            gson.toJson(path, writer);
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to save path " + path.getPathName() + " for " + playerUUID);
            e.printStackTrace();
        }
    }

    public List<PathData> loadPaths(UUID playerUUID) {
        File playerFolder = new File(dataFolder, playerUUID.toString());
        List<PathData> playerPaths = new ArrayList<>();

        if (!playerFolder.exists()) {
            return playerPaths;
        }

        File[] pathFiles = playerFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (pathFiles == null) {
            return playerPaths;
        }

        for (File pathFile : pathFiles) {
            try (FileReader reader = new FileReader(pathFile)) {
                PathData pathData = gson.fromJson(reader, PathData.class);
                if (pathData != null) {
                    playerPaths.add(pathData);
                }
            } catch (IOException e) {
                TrailblazerPlugin.getPluginLogger().severe("Failed to load a path file for " + playerUUID);
                e.printStackTrace();
            }
        }
        return playerPaths;
    }

    public void deletePath(UUID playerUUID, UUID pathId) {
        File playerFolder = new File(dataFolder, playerUUID.toString());
        File pathFile = new File(playerFolder, pathId.toString() + ".json");

        if (pathFile.exists() && !pathFile.delete()) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to delete path file: " + pathFile.getAbsolutePath());
        }
    }

    public void renamePath(UUID playerUUID, UUID pathId, String newName) {
        File playerFolder = new File(dataFolder, playerUUID.toString());
        File pathFile = new File(playerFolder, pathId.toString() + ".json");

        if (!pathFile.exists()) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path that does not exist: " + pathId);
            return;
        }

        try (FileReader reader = new FileReader(pathFile)) {
            PathData pathData = gson.fromJson(reader, PathData.class);
            if (pathData != null) {
                pathData.setPathName(newName);
                try (FileWriter writer = new FileWriter(pathFile)) {
                    gson.toJson(pathData, writer);
                }
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to rename path: " + pathId);
            e.printStackTrace();
        }
    }
}