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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.api.PathNameSanitizer;

public class PathDataManager {

    private final File dataFolder;
    private final Gson gson;
    private final AtomicInteger nextServerPathLetter = new AtomicInteger(0);
    public static final int MAX_POINTS_PER_PATH = 5000;

    public PathDataManager(TrailblazerPlugin plugin) {
        this.dataFolder = new File(plugin.getDataFolder(), "paths");
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // Per-path locks so concurrent operations on different paths do not contend.
    private final ConcurrentHashMap<UUID, ReentrantLock> pathLocks = new ConcurrentHashMap<>();

    public void savePath(PathData path) {
        if (path == null || path.getPathId() == null) {
            throw new IllegalArgumentException("Path and pathId must not be null");
        }
        File pathFile = new File(dataFolder, path.getPathId().toString() + ".json");
        ReentrantLock lock = acquireLock(path.getPathId());
        try {
            try (FileWriter writer = new FileWriter(pathFile)) {
                gson.toJson(path, writer);
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to save path " + path.getPathName());
            e.printStackTrace();
        } finally {
            releaseLock(path.getPathId(), lock);
        }
    }

    public String getNextServerPathName() {
        int current = nextServerPathLetter.getAndIncrement();
        String letter = String.valueOf((char) ('A' + current));
        return "Path-" + letter;
    }

    public List<PathData> loadPaths(UUID playerUUID) {
        List<PathData> playerPaths = new ArrayList<>();
        File[] pathFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (pathFiles == null) {
            return playerPaths;
        }

        for (File pathFile : pathFiles) {
            UUID pathId = extractPathId(pathFile.getName());
            if (pathId == null) {
                TrailblazerPlugin.getPluginLogger().warning("Skipping path file with invalid name: " + pathFile.getName());
                continue;
            }

            ReentrantLock lock = acquireLock(pathId);
            try (FileReader reader = new FileReader(pathFile)) {
                PathData pathData = gson.fromJson(reader, PathData.class);
                if (pathData == null || !isValidPathData(pathData)) {
                    TrailblazerPlugin.getPluginLogger().warning("Skipping invalid path data file: " + pathFile.getName());
                    continue;
                }
                if (pathData.getOwnerUUID().equals(playerUUID) || pathData.getSharedWith().contains(playerUUID)) {
                    // Sanitize name post-deserialization to harden against tampered JSON
                    String original = pathData.getPathName();
                    String sanitized = PathNameSanitizer.sanitize(original);
                    if (!sanitized.equals(original)) {
                        pathData.setPathName(sanitized);
                        // Persist corrected name asynchronously (reuse save logic)
                        savePath(pathData);
                    }
                    playerPaths.add(pathData);
                }
            } catch (IOException e) {
                TrailblazerPlugin.getPluginLogger().severe("Failed to load a path file for " + playerUUID);
                e.printStackTrace();
            } finally {
                releaseLock(pathId, lock);
            }
        }
        return playerPaths;
    }

    public boolean deletePath(UUID playerUUID, UUID pathId) {
        if (pathId == null) {
            return false;
        }
        File pathFile = new File(dataFolder, pathId.toString() + ".json");
        ReentrantLock lock = acquireLock(pathId);
        try {
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
        } finally {
            releaseLock(pathId, lock);
        }
    }

    public SharedCopyResult ensureSharedCopy(PathData source, UUID targetUuid, String targetName) {
        if (source == null || targetUuid == null || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Source path, target UUID, and target name must be provided");
        }
        List<PathData> existing = loadPaths(targetUuid);
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
        savePath(copy);
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
        if (pathId == null) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path with null identifier");
            return;
        }
        String sanitized = com.trailblazer.api.PathNameSanitizer.sanitize(newName);
        File pathFile = new File(dataFolder, pathId.toString() + ".json");
        if (!pathFile.exists()) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path that does not exist: " + pathId);
            return;
        }

        ReentrantLock lock = acquireLock(pathId);
        try (FileReader reader = new FileReader(pathFile)) {
            PathData pathData = gson.fromJson(reader, PathData.class);
            if (pathData != null && pathData.getOwnerUUID().equals(playerUUID)) {
                pathData.setPathName(sanitized);
                savePath(pathData);
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to rename path: " + pathId);
            e.printStackTrace();
        } finally {
            releaseLock(pathId, lock);
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
                    path.setPathName(com.trailblazer.api.PathNameSanitizer.sanitize(newName));
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

    public static boolean isValidPathData(PathData path) {
        return path.getPathId() != null
            && path.getOwnerUUID() != null
            && path.getPoints() != null
            && path.getPoints().size() <= MAX_POINTS_PER_PATH;
    }

    private ReentrantLock acquireLock(UUID pathId) {
        ReentrantLock lock = pathLocks.computeIfAbsent(pathId, id -> new ReentrantLock());
        lock.lock();
        return lock;
    }

    private void releaseLock(UUID pathId, ReentrantLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private UUID extractPathId(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) {
            return null;
        }
        String raw = fileName.substring(0, fileName.length() - 5);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}