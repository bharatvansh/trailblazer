package com.trailblazer.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;

public class PathDataManager {

    private final File dataFolder;
    private final Gson gson;
    private int nextServerPathLetter = 0;

    public PathDataManager(TrailblazerPlugin plugin) {
        this.dataFolder = new File(plugin.getDataFolder(), "paths");
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // Coarse-grained lock to protect file I/O operations on path JSON files.
    private final Object ioLock = new Object();

    public void savePath(PathData path) {
        File pathFile = new File(dataFolder, path.getPathId().toString() + ".json");
        synchronized (ioLock) {
            try (FileWriter writer = new FileWriter(pathFile)) {
                gson.toJson(path, writer);
            } catch (IOException e) {
                TrailblazerPlugin.getPluginLogger().severe("Failed to save path " + path.getPathName());
                e.printStackTrace();
            }
        }
    }

    public String getNextServerPathName() {
        String letter = String.valueOf((char)('A' + nextServerPathLetter));
        nextServerPathLetter++;
        return "Path-" + letter;
    }

    public List<PathData> loadPaths(UUID playerUUID) {
        List<PathData> playerPaths = new ArrayList<>();
        synchronized (ioLock) {
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
        }
        return playerPaths;
    }

    public boolean deletePath(UUID playerUUID, UUID pathId) {
        File pathFile = new File(dataFolder, pathId.toString() + ".json");
        synchronized (ioLock) {
            if (!pathFile.exists()) {
                return false;
            }

            PathData pathData;
            try (FileReader reader = new FileReader(pathFile)) {
                pathData = gson.fromJson(reader, PathData.class);
            } catch (IOException e) {
                TrailblazerPlugin.getPluginLogger().severe("Failed to read path for deletion: " + pathId);
                e.printStackTrace();
                return false;
            }

            if (pathData == null) {
                return false;
            }

            if (pathData.getOwnerUUID().equals(playerUUID)) {
                if (!pathFile.delete()) {
                    TrailblazerPlugin.getPluginLogger().severe("Failed to delete path file: " + pathFile.getAbsolutePath());
                    return false;
                }
                return true;
            }

            boolean removed = pathData.getSharedWith().remove(playerUUID);
            if (removed) {
                savePath(pathData);
            }
            return removed;
        }
    }

    public SharedCopyResult ensureSharedCopy(PathData source, UUID targetUuid, String targetName) {
        if (source == null || targetUuid == null || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Source path, target UUID, and target name must be provided");
        }
        List<PathData> existing;
        synchronized (ioLock) {
            existing = loadPaths(targetUuid);
        }
        UUID originPathId = resolveOriginPathId(source);
        Optional<PathData> alreadyOwned = existing.stream()
                .filter(p -> targetUuid.equals(p.getOwnerUUID()))
                .filter(p -> resolveOriginPathId(p).equals(originPathId))
                .findFirst();
        if (alreadyOwned.isPresent()) {
            return new SharedCopyResult(alreadyOwned.get(), false);
        }

        String newName = uniquePathName(source.getPathName(), existing);
        List<Vector3d> copiedPoints = new ArrayList<>(source.getPoints());
        PathData copy = new PathData(UUID.randomUUID(), newName, targetUuid, targetName,
                System.currentTimeMillis(), source.getDimension(), copiedPoints, source.getColorArgb());
        copy.setOrigin(originPathId, resolveOriginOwner(source), resolveOriginOwnerName(source));
        synchronized (ioLock) {
            savePath(copy);
        }
        return new SharedCopyResult(copy, true);
    }

    private UUID resolveOriginPathId(PathData path) {
        UUID origin = path.getOriginPathId();
        return origin != null ? origin : path.getPathId();
    }

    private UUID resolveOriginOwner(PathData path) {
        UUID owner = path.getOriginOwnerUUID();
        return owner != null ? owner : path.getOwnerUUID();
    }

    private String resolveOriginOwnerName(PathData path) {
        String originName = path.getOriginOwnerName();
        if (originName != null && !originName.isBlank()) {
            return originName;
        }
        return path.getOwnerName();
    }

    private String uniquePathName(String proposed, List<PathData> existing) {
        String base = (proposed == null || proposed.isBlank()) ? "Shared Path" : proposed.trim();
        String candidate = base;
        int index = 2;
        while (nameExists(existing, candidate)) {
            candidate = base + " (" + index++ + ")";
        }
        return candidate;
    }

    private boolean nameExists(List<PathData> existing, String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        for (PathData path : existing) {
            if (path.getPathName() != null && path.getPathName().toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    public static class SharedCopyResult {
        private final PathData path;
        private final boolean created;

        public SharedCopyResult(PathData path, boolean created) {
            this.path = path;
            this.created = created;
        }

        public PathData getPath() {
            return path;
        }

        public boolean wasCreated() {
            return created;
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