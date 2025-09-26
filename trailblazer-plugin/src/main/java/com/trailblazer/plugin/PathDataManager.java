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
        this.dataFolder = new File(plugin.getDataFolder(), "paths");
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void savePath(PathData path) {
        File pathFile = new File(dataFolder, path.getPathId().toString() + ".json");
        try (FileWriter writer = new FileWriter(pathFile)) {
            gson.toJson(path, writer);
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to save path " + path.getPathName());
            e.printStackTrace();
        }
    }

    public List<PathData> loadPaths(UUID playerUUID) {
        List<PathData> playerPaths = new ArrayList<>();
        File[] pathFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (pathFiles == null) {
            return playerPaths;
        }

        for (File pathFile : pathFiles) {
            try (FileReader reader = new FileReader(pathFile)) {
                PathData pathData = gson.fromJson(reader, PathData.class);
                if (pathData != null && (pathData.getOwnerUUID().equals(playerUUID) || pathData.getSharedWith().contains(playerUUID))) {
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
        File pathFile = new File(dataFolder, pathId.toString() + ".json");
        if (!pathFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(pathFile)) {
            PathData pathData = gson.fromJson(reader, PathData.class);
            if (pathData != null) {
                if (pathData.getOwnerUUID().equals(playerUUID)) {
                    if (!pathFile.delete()) {
                        TrailblazerPlugin.getPluginLogger().severe("Failed to delete path file: " + pathFile.getAbsolutePath());
                    }
                } else {
                    pathData.getSharedWith().remove(playerUUID);
                    savePath(pathData);
                }
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to process path for deletion: " + pathId);
            e.printStackTrace();
        }
    }

    public void renamePath(UUID playerUUID, UUID pathId, String newName) {
        File pathFile = new File(dataFolder, pathId.toString() + ".json");
        if (!pathFile.exists()) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path that does not exist: " + pathId);
            return;
        }

        try (FileReader reader = new FileReader(pathFile)) {
            PathData pathData = gson.fromJson(reader, PathData.class);
            if (pathData != null && pathData.getOwnerUUID().equals(playerUUID)) {
                pathData.setPathName(newName);
                savePath(pathData);
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to rename path: " + pathId);
            e.printStackTrace();
        }
    }

    public List<PathData> updateMetadata(UUID playerUUID, UUID pathId, String newName, int colorArgb) {
        List<PathData> paths = loadPaths(playerUUID);
        boolean updated = false;
        for (PathData path : paths) {
            if (!path.getPathId().equals(pathId)) {
                continue;
            }
            if (path.getOwnerUUID().equals(playerUUID)) {
                if (newName != null && !newName.isBlank()) {
                    path.setPathName(newName);
                }
                if (colorArgb != 0) {
                    path.setColorArgb(colorArgb);
                }
                savePath(path);
                updated = true;
            }
            break;
        }
        return updated ? paths : null;
    }
}