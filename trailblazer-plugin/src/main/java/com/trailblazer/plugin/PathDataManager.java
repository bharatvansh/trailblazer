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

    private final File basePathsFolder;
    private final Gson gson;
    private final AtomicInteger nextServerPathLetter = new AtomicInteger(0);
    public static final int MAX_POINTS_PER_PATH = 5000;

    public PathDataManager(TrailblazerPlugin plugin) {
        this.basePathsFolder = new File(plugin.getDataFolder(), "paths");
        if (!this.basePathsFolder.exists() && !this.basePathsFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // Per-path locks so concurrent operations on different paths do not contend.
    private final ConcurrentHashMap<UUID, ReentrantLock> pathLocks = new ConcurrentHashMap<>();
    
    // Locks for sharing operations to prevent race conditions in duplicate detection.
    // Key format: "targetUuid:originPathId" to ensure only one sharing operation
    // for the same recipient + origin path combination can proceed at a time.
    private final ConcurrentHashMap<String, ReentrantLock> sharingLocks = new ConcurrentHashMap<>();

    public void savePath(UUID worldUid, PathData path) {
        if (path == null || path.getPathId() == null) {
            throw new IllegalArgumentException("Path and pathId must not be null");
        }
        File worldFolder = resolveWorldFolder(worldUid);
        File pathFile = new File(worldFolder, path.getPathId().toString() + ".json");
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

    public List<PathData> loadPaths(UUID worldUid, UUID playerUUID) {
        List<PathData> playerPaths = new ArrayList<>();
        File worldFolder = resolveWorldFolder(worldUid);
        File[] pathFiles = worldFolder.listFiles((dir, name) -> name.endsWith(".json"));
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
                // Only check ownership - sharedWith is no longer used for access control
                // All shared paths are now owned copies created via ensureSharedCopy()
                if (pathData.getOwnerUUID().equals(playerUUID)) {
                    // Sanitize name post-deserialization to harden against tampered JSON
                    String original = pathData.getPathName();
                    String sanitized = PathNameSanitizer.sanitize(original);
                    if (!sanitized.equals(original)) {
                        pathData.setPathName(sanitized);
                        // Persist corrected name asynchronously (reuse save logic)
                        savePath(worldUid, pathData);
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

    public boolean deletePath(UUID worldUid, UUID playerUUID, UUID pathId) {
        if (pathId == null) {
            return false;
        }
        File worldFolder = resolveWorldFolder(worldUid);
        File pathFile = new File(worldFolder, pathId.toString() + ".json");
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

            // Only allow deletion if player owns the path
            // Shared paths are now owned copies, so recipients delete their own copy, not remove from sharedWith
            if (pathData.getOwnerUUID().equals(playerUUID)) {
                if (!pathFile.delete()) {
                    TrailblazerPlugin.getPluginLogger().severe("Failed to delete path file: " + pathFile.getAbsolutePath());
                    return false;
                }
                return true;
            }

            // Player doesn't own this path, so they can't delete it
            return false;
        } finally {
            releaseLock(pathId, lock);
        }
    }

    public SharedCopyResult ensureSharedCopy(PathData source, UUID targetUuid, String targetName, UUID targetWorldUid) {
        if (source == null || targetUuid == null || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Source path, target UUID, and target name must be provided");
        }
        
        // Create a unique lock key for this specific sharing operation.
        // Same recipient + same origin path = same lock, preventing duplicate creation.
        UUID originPathId = resolveOriginPathId(source);
        String lockKey = targetUuid.toString() + ":" + originPathId.toString();
        
        // Get or create a lock for this sharing operation.
        // This ensures only one thread can check and create a copy for the same
        // recipient + origin path combination at a time.
        ReentrantLock sharingLock = sharingLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        
        sharingLock.lock();
        try {
            // Now safely check for duplicates while holding the lock.
            // This prevents race conditions where multiple threads might check
            // simultaneously and both decide to create a copy.
            List<PathData> existing = loadPaths(targetWorldUid, targetUuid);
            Optional<PathData> alreadyOwned = existing.stream()
                    .filter(p -> targetUuid.equals(p.getOwnerUUID()))
                    .filter(p -> resolveOriginPathId(p).equals(originPathId))
                    .findFirst();
            
            if (alreadyOwned.isPresent()) {
                // Duplicate found! Return existing copy without creating a new one.
                return new SharedCopyResult(alreadyOwned.get(), false);
            }
            
            // No duplicate found - safe to create a new copy.
            String newName = uniquePathName(source.getPathName(), existing);
            List<Vector3d> copiedPoints = new ArrayList<>(source.getPoints());
            PathData copy = new PathData(UUID.randomUUID(), newName, targetUuid, targetName,
                    System.currentTimeMillis(), source.getDimension(), copiedPoints, source.getColorArgb());
            copy.setOrigin(originPathId, resolveOriginOwner(source), resolveOriginOwnerName(source));
            savePath(targetWorldUid, copy);
            return new SharedCopyResult(copy, true);
        } finally {
            // Always release the lock, even if an exception occurs.
            sharingLock.unlock();
            
            // Clean up the lock if it's no longer in use to prevent memory leaks.
            // Use tryLock() to atomically check if the lock is truly free.
            // If we can acquire it immediately, no other thread has it, so it's safe to remove.
            if (sharingLock.tryLock()) {
                try {
                    // While holding the lock, check if any threads are queued.
                    // If not, it's safe to remove since we have exclusive access.
                    if (!sharingLock.hasQueuedThreads()) {
                        sharingLocks.remove(lockKey, sharingLock);
                    }
                } finally {
                    // Always release the lock we just acquired.
                    sharingLock.unlock();
                }
            }
            // If tryLock() failed, another thread acquired the lock, so do not remove.
        }
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

    public void renamePath(UUID worldUid, UUID playerUUID, UUID pathId, String newName) {
        if (pathId == null) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path with null identifier");
            return;
        }
        String sanitized = com.trailblazer.api.PathNameSanitizer.sanitize(newName);
        File worldFolder = resolveWorldFolder(worldUid);
        File pathFile = new File(worldFolder, pathId.toString() + ".json");
        if (!pathFile.exists()) {
            TrailblazerPlugin.getPluginLogger().warning("Attempted to rename a path that does not exist: " + pathId);
            return;
        }

        ReentrantLock lock = acquireLock(pathId);
        try (FileReader reader = new FileReader(pathFile)) {
            PathData pathData = gson.fromJson(reader, PathData.class);
            if (pathData != null && pathData.getOwnerUUID().equals(playerUUID)) {
                pathData.setPathName(sanitized);
                savePath(worldUid, pathData);
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to rename path: " + pathId);
            e.printStackTrace();
        } finally {
            releaseLock(pathId, lock);
        }
    }

    public List<PathData> updateMetadata(UUID worldUid, UUID playerUUID, UUID pathId, String newName, int colorArgb) {
        List<PathData> paths = loadPaths(worldUid, playerUUID);
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
                savePath(worldUid, path);
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
            
            // Clean up the lock if it's no longer in use to prevent memory leaks.
            // Use tryLock() to atomically check if the lock is truly free.
            // If we can acquire it immediately, no other thread has it, so it's safe to remove.
            if (lock.tryLock()) {
                try {
                    // While holding the lock, check if any threads are queued.
                    // If not, it's safe to remove since we have exclusive access.
                    if (!lock.hasQueuedThreads()) {
                        pathLocks.remove(pathId, lock);
                    }
                } finally {
                    // Always release the lock we just acquired.
                    lock.unlock();
                }
            }
            // If tryLock() failed, another thread acquired the lock, so do not remove.
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

    private File resolveWorldFolder(UUID worldUid) {
        if (worldUid == null) {
            throw new IllegalArgumentException("worldUid must not be null for per-world path storage");
        }
        String key = worldUid.toString();
        File worldFolder = new File(basePathsFolder, key);
        if (!worldFolder.exists() && !worldFolder.mkdirs()) {
            TrailblazerPlugin.getPluginLogger().severe("Could not create world paths folder: " + worldFolder.getAbsolutePath());
        }
        return worldFolder;
    }
}