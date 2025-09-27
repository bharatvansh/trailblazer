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
 * Manages path data on the client side.
 * For now, it's a simple in-memory store.
 */
public class ClientPathManager {
    public enum PathOrigin {
        LOCAL,
        SERVER_OWNED,
        SERVER_SHARED
    }
    // A map to hold all of the player's own paths.
    private final Map<UUID, PathData> myPaths = new HashMap<>();
    // A map to hold all paths shared with the player.
    private final Map<UUID, PathData> sharedPaths = new HashMap<>();
    // Tracks where each path came from.
    private final Map<UUID, PathOrigin> pathOrigins = new HashMap<>();
    // A set to track which paths should currently be visible.
    private final Set<UUID> visiblePaths = new HashSet<>();
    // A special field to hold a path currently being streamed by server (live preview)
    private PathData livePath = null;
    // Local recording path (client-originated) separate from server live path
    private PathData localRecording = null;
    // Tracks whether a recording session is currently active (local)
    private boolean recording = false;
    private Vector3d lastCapturedPoint = null;
    private PathPersistenceManager persistence; // injected
    private int maxPointsPerPath = 5000; // updated from config
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
        if (origin == PathOrigin.LOCAL && persistence != null) {
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
        recording = true;
        UUID id = UUID.randomUUID();
        localRecording = new PathData(id, "Path-" + nextPathNumber, UUID.randomUUID(), "Player", System.currentTimeMillis(), "minecraft:overworld", new ArrayList<>());
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

    /** Called each client tick to append points when recording locally. */
    public void tickRecording(MinecraftClient client) {
        if (!recording || localRecording == null) return;
        PlayerEntity player = client.player;
        if (player == null) return;
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
        return sharedPaths.values();
    }

    public PathOrigin getPathOrigin(UUID pathId) {
        return pathOrigins.getOrDefault(pathId, PathOrigin.LOCAL);
    }

    public boolean isServerBacked(UUID pathId) {
        PathOrigin origin = getPathOrigin(pathId);
        return origin == PathOrigin.SERVER_OWNED || origin == PathOrigin.SERVER_SHARED;
    }

    public boolean isLocalPath(UUID pathId) {
        return getPathOrigin(pathId) == PathOrigin.LOCAL;
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
        PathOrigin origin = determineServerOrigin(path);
        putPath(path, origin);
        setPathVisible(path.getPathId());
    }

    // --- FOR TESTING ---
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

    private void recalculateNextPathNumber() {
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
}