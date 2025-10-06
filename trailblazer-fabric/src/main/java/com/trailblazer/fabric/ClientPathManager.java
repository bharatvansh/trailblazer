package com.trailblazer.fabric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.persistence.PathPersistenceManager;

/**
 * Manages client-side path storage and recording.
 */
public class ClientPathManager {
    public enum PathOrigin {
        LOCAL,
        IMPORTED,
        SERVER_OWNED,
        SERVER_SHARED
    }
    private final Map<UUID, PathData> myPaths = new HashMap<>();
    private final Map<UUID, PathData> sharedPaths = new HashMap<>();
    private final Map<UUID, PathOrigin> pathOrigins = new HashMap<>();
    private final Set<UUID> visiblePaths = new HashSet<>();
    private PathData livePath = null;
    private PathData localRecording = null;
    private boolean recording = false;
    private Vector3d lastCapturedPoint = null;
    private PathPersistenceManager persistence;
    private int maxPointsPerPath = 5000;
    private UUID localPlayerUuid;
    private int nextPathNumber = 1;

    public void attachPersistence(PathPersistenceManager persistence, int maxPointsPerPath) {
        this.persistence = persistence;
        this.maxPointsPerPath = maxPointsPerPath;
        recalculateNextPathNumber();
    }

    public void addMyPath(PathData path) {
        putPath(path, PathOrigin.LOCAL);
    }

    public void addSharedPath(PathData path) {
        putPath(path, PathOrigin.SERVER_SHARED);
        setPathVisible(path.getPathId()); // Make shared paths visible by default
    }

    public void addImportedPath(PathData path) {
        putPath(path, PathOrigin.IMPORTED);
    }

    public void removePath(UUID pathId) {
        myPaths.remove(pathId);
        sharedPaths.remove(pathId);
        visiblePaths.remove(pathId);
        pathOrigins.remove(pathId);
    }

    public void setPathVisible(UUID pathId) {
        if (myPaths.containsKey(pathId) || sharedPaths.containsKey(pathId)) {
            visiblePaths.add(pathId);
        }
    }

    public void setPathHidden(UUID pathId) {
        visiblePaths.remove(pathId);
    }

    public void togglePathVisibility(UUID pathId) {
        if (visiblePaths.contains(pathId)) {
            setPathHidden(pathId);
        } else {
            setPathVisible(pathId);
        }
    }

    public void deletePath(UUID pathId) {
        PathOrigin origin = pathOrigins.getOrDefault(pathId, PathOrigin.LOCAL);
        myPaths.remove(pathId);
        visiblePaths.remove(pathId);
        pathOrigins.remove(pathId);
        if ((origin == PathOrigin.LOCAL || origin == PathOrigin.IMPORTED) && persistence != null) {
            persistence.deleteLocal(pathId);
        }
        recalculateNextPathNumber();
    }

    public void removeSharedPath(UUID pathId) {
        sharedPaths.remove(pathId);
        visiblePaths.remove(pathId);
        pathOrigins.remove(pathId);
    }

    public void removeServerPath(UUID pathId) {
        PathOrigin origin = pathOrigins.get(pathId);
        if (origin == PathOrigin.SERVER_OWNED) {
            myPaths.remove(pathId);
        } else if (origin == PathOrigin.SERVER_SHARED) {
            sharedPaths.remove(pathId);
        }
        visiblePaths.remove(pathId);
        pathOrigins.remove(pathId);
    }

    public boolean isPathVisible(UUID pathId) {
        return visiblePaths.contains(pathId);
    }

    public void hideAllPaths() {
        visiblePaths.clear();
    }

    /**
     * Returns the path currently being recorded, if any.
     * @return The live PathData object, or null if not recording.
     */
    public PathData getLivePath() { return livePath; }
    public PathData getLocalRecordingPath() { return localRecording; }

    /**
     * Updates the points for the live path. This is called when a {@code LivePathUpdatePayload} is received.
     * @param points The new list of points for the path.
     */
    public void updateLivePath(List<Vector3d> points) {
        if (livePath == null) {
            // If this is the first update, create a new PathData object to represent the live path.
            // We use a fixed UUID for the live path so we can easily identify it.
            UUID livePathId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            livePath = new PathData(livePathId, "LiveRecording", UUID.randomUUID(), "Me", 0L, "", points);
        } else {
            // Otherwise, just update the points.
            livePath.getPoints().clear();
            livePath.getPoints().addAll(points);
        }
        // Receiving live updates implies we are in a recording session.
        recording = true;
    }

    /**
     * Clears the live path data. Called when a {@code StopLivePathPayload} is received.
     */
    public void stopLivePath() { livePath = null; }

    // --- Recording state helpers (client-side optimistic) ---
    public boolean isRecording() {
        return recording;
    }

    public void startRecordingLocal() {
        if (recording) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            TrailblazerFabricClient.LOGGER.warn("Cannot start recording: client not ready");
            return;
        }
        recording = true;
        UUID id = UUID.randomUUID();
        UUID ownerUuid = localPlayerUuid != null ? localPlayerUuid : (client.getSession() != null ? client.getSession().getUuidOrNull() : UUID.randomUUID());
        String ownerName = resolveLocalPlayerName("Player");
        String dimension = client.player.getWorld().getRegistryKey().getValue().toString();
        localRecording = new PathData(id, "Path-" + nextPathNumber, ownerUuid, ownerName, System.currentTimeMillis(), dimension, new ArrayList<>());
        addMyPath(localRecording);
        setPathVisible(localRecording.getPathId());
        lastCapturedPoint = null;
        if (persistence != null) persistence.markDirty(localRecording.getPathId());
        nextPathNumber++;
    }

    public void stopRecordingLocal() {
        if (!recording) return;
        recording = false;
        if (localRecording != null && persistence != null) {
            persistence.markDirty(localRecording.getPathId());
        }
        localRecording = null;
        lastCapturedPoint = null;
    }

    public void cancelRecordingLocal() {
        if (!recording) return;
        recording = false;
        if (localRecording != null) {
            // Remove the partially recorded path (do not persist)
            UUID id = localRecording.getPathId();
            myPaths.remove(id);
            visiblePaths.remove(id);
            pathOrigins.remove(id);
            localRecording = null;
            lastCapturedPoint = null;
            recalculateNextPathNumber();
        }
    }

    /** Called each client tick to append points when recording locally. */
    public void tickRecording(MinecraftClient client) {
        if (!recording || localRecording == null) return;
        if (client == null || client.player == null) return;
        PlayerEntity player = client.player;
        Vector3d current = new Vector3d(player.getX(), player.getY(), player.getZ());
        List<Vector3d> pts = localRecording.getPoints();
        if (pts.isEmpty()) {
            pts.add(current);
            lastCapturedPoint = current;
            if (persistence != null) persistence.markDirty(localRecording.getPathId());
            return;
        }
        if (lastCapturedPoint == null) {
            lastCapturedPoint = current;
        }
        double dx = current.getX() - lastCapturedPoint.getX();
        double dy = current.getY() - lastCapturedPoint.getY();
        double dz = current.getZ() - lastCapturedPoint.getZ();
        double distSq = dx*dx + dy*dy + dz*dz;
        if (distSq >= 0.04) { // moved >= ~0.2 blocks
            pts.add(current);
            lastCapturedPoint = current;
            if (pts.size() > maxPointsPerPath && persistence != null) {
                persistence.enforcePointLimit(localRecording);
            }
            if (persistence != null) persistence.markDirty(localRecording.getPathId());
        }
    }

    public Collection<PathData> getVisiblePaths() {
        List<PathData> result = new ArrayList<>();
        for (UUID id : visiblePaths) {
            PathData path = myPaths.get(id);
            if (path == null) {
                path = sharedPaths.get(id);
            }
            if (path != null) {
                result.add(path);
            }
        }
        return result;
    }

    public Collection<PathData> getMyPaths() {
        return myPaths.values();
    }

    public Collection<PathData> getSharedPaths() {
        List<PathData> imported = new ArrayList<>();
        for (PathData path : myPaths.values()) {
            if (getPathOrigin(path.getPathId()) == PathOrigin.IMPORTED) {
                imported.add(path);
            }
        }
        if (!sharedPaths.isEmpty()) {
            imported.addAll(sharedPaths.values());
        }
        return imported;
    }

    public PathOrigin getPathOrigin(UUID pathId) {
        return pathOrigins.getOrDefault(pathId, PathOrigin.LOCAL);
    }

    public boolean isServerBacked(UUID pathId) {
        PathOrigin origin = getPathOrigin(pathId);
        return origin == PathOrigin.SERVER_OWNED || origin == PathOrigin.SERVER_SHARED;
    }

    public boolean isLocalPath(UUID pathId) {
        PathOrigin origin = getPathOrigin(pathId);
        return origin == PathOrigin.LOCAL || origin == PathOrigin.IMPORTED;
    }

    public void setLocalPlayerUuid(UUID uuid) {
        this.localPlayerUuid = uuid;
    }

    public UUID getLocalPlayerUuid() {
        return localPlayerUuid;
    }

    public void onPathUpdated(PathData path) {
        UUID id = path.getPathId();
        if (myPaths.containsKey(id)) {
            myPaths.put(id, path);
            if (isLocalPath(id) && persistence != null) {
                persistence.markDirty(id);
            }
        } else if (sharedPaths.containsKey(id)) {
            sharedPaths.put(id, path);
        }
    }

    public void applyServerSync(Collection<PathData> serverPaths) {
        Set<UUID> preserveVisible = new HashSet<>();
        Set<UUID> previouslyKnown = new HashSet<>();
        for (UUID id : pathOrigins.keySet()) {
            if (isServerBacked(id)) {
                previouslyKnown.add(id);
                if (visiblePaths.contains(id)) {
                    preserveVisible.add(id);
                }
            }
        }

        // remove previous server-backed paths
        for (UUID id : new ArrayList<>(previouslyKnown)) {
            myPaths.remove(id);
            sharedPaths.remove(id);
            visiblePaths.remove(id);
            pathOrigins.remove(id);
        }

        for (PathData path : serverPaths) {
            PathOrigin origin = determineServerOrigin(path);
            putPath(path, origin);
            UUID id = path.getPathId();
            boolean known = previouslyKnown.contains(id);
            if (preserveVisible.contains(id)) {
                visiblePaths.add(id);
            } else if (!known) {
                // default visibility for brand new server paths
                visiblePaths.add(id);
            }
        }
        recalculateNextPathNumber();
    }

    public void applyServerShare(PathData path) {
        if (path == null) {
            return;
        }
        UUID newId = UUID.randomUUID();
        UUID ownerUuid = localPlayerUuid != null ? localPlayerUuid : path.getOwnerUUID();
        String ownerName = resolveLocalPlayerName(path.getOwnerName());
        String displayName = uniquePathName(path.getPathName());
        List<Vector3d> copiedPoints = new ArrayList<>(path.getPoints());
        PathData imported = new PathData(newId, displayName, ownerUuid, ownerName,
                System.currentTimeMillis(), path.getDimension(), copiedPoints, path.getColorArgb());
        UUID originPathId = path.getOriginPathId() != null ? path.getOriginPathId() : path.getPathId();
        UUID originOwner = path.getOriginOwnerUUID() != null ? path.getOriginOwnerUUID() : path.getOwnerUUID();
        String originOwnerName = path.getOriginOwnerName() != null ? path.getOriginOwnerName() : path.getOwnerName();
        imported.setOrigin(originPathId, originOwner, originOwnerName);

        addImportedPath(imported);
        setPathVisible(imported.getPathId());
        if (persistence != null) {
            persistence.markDirty(imported.getPathId());
        }
        recalculateNextPathNumber();
    }

    public void loadDummyPath() {
        UUID dummyId = UUID.randomUUID();
        List<Vector3d> points = Arrays.asList(
                new Vector3d(0, 100, 0),
                new Vector3d(10, 105, 5),
                new Vector3d(20, 100, 15),
                new Vector3d(15, 98, 25),
                new Vector3d(5, 100, 20),
                new Vector3d(0, 100, 0)
        );
    PathData dummyPath = new PathData(dummyId, "DummyPath", UUID.randomUUID(), "TestPlayer", 0L, "minecraft:overworld", points);

        addMyPath(dummyPath);
        setPathVisible(dummyId);
        TrailblazerFabricClient.LOGGER.info("Loaded dummy path for rendering test.");
    }

    private void putPath(PathData path, PathOrigin origin) {
        if (origin == PathOrigin.SERVER_SHARED) {
            sharedPaths.put(path.getPathId(), path);
        } else {
            myPaths.put(path.getPathId(), path);
        }
        pathOrigins.put(path.getPathId(), origin);
    }

    private PathOrigin determineServerOrigin(PathData path) {
        if (localPlayerUuid != null && localPlayerUuid.equals(path.getOwnerUUID())) {
            return PathOrigin.SERVER_OWNED;
        }
        return PathOrigin.SERVER_SHARED;
    }

    /**
     * Recalculate the next numeric suffix to use for auto-named "Path-N" paths.
     * This inspects currently-loaded local paths and sets the internal counter so
     * newly-created paths won't collide with existing ones.
     */
    public void recalculateNextPathNumber() {
        int maxNum = 0;
        for (PathData path : myPaths.values()) {
            if (path.getPathName().startsWith("Path-")) {
                try {
                    String numStr = path.getPathName().substring(5);
                    int num = Integer.parseInt(numStr);
                    if (num > maxNum) {
                        maxNum = num;
                    }
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    // Ignore paths with malformed names
                }
            }
        }
        nextPathNumber = maxNum + 1;
    }

    private String resolveLocalPlayerName(String fallback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            String username = client.getSession().getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        return fallback != null && !fallback.isBlank() ? fallback : "Player";
    }

    private String uniquePathName(String proposed) {
        String base = (proposed == null || proposed.isBlank()) ? "Shared Path" : proposed.trim();
        String candidate = base;
        int index = 2;
        while (pathNameExists(candidate)) {
            candidate = base + " (" + index++ + ")";
        }
        return candidate;
    }

    private boolean pathNameExists(String name) {
        for (PathData path : myPaths.values()) {
            if (path.getPathName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        for (PathData path : sharedPaths.values()) {
            if (path.getPathName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public void replaceLocalWithServerCopy(PathData serverCopy) {
        if (serverCopy == null || serverCopy.getOriginPathId() == null) {
            return;
        }

        // The originPathId of the server copy holds the original client-side UUID.
        UUID originalClientId = serverCopy.getOriginPathId();

        // Remove the old local path.
        deletePath(originalClientId);

        // Add the new server-authoritative path.
        putPath(serverCopy, PathOrigin.SERVER_OWNED);
        setPathVisible(serverCopy.getPathId());

        // Trigger a persistence save.
        if (persistence != null) {
            persistence.markDirty(serverCopy.getPathId());
        }
    }
}