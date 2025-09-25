package com.trailblazer.api;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The core data structure representing a recorded path.
 * This object is designed to be serialized (e.g., to JSON) and sent over the network
 * or saved to a file. It is part of the shared API module.
 */
public class PathData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID pathId;
    private String pathName; // Can be renamed
    private final UUID ownerUUID;
    private final String ownerName;
    private final long creationTimestamp;
    private final String dimension;
    private final List<Vector3d> points;
    // Stored as 0xAARRGGBB. 0 or missing implies uninitialized -> lazily assigned.
    private int colorArgb; 

    public PathData(UUID pathId, String pathName, UUID ownerUUID, String ownerName, long creationTimestamp, String dimension, List<Vector3d> points) {
        // Validation to ensure data integrity
        Objects.requireNonNull(pathId, "Path ID cannot be null");
        Objects.requireNonNull(pathName, "Path Name cannot be null");
        Objects.requireNonNull(ownerUUID, "Owner UUID cannot be null");
        Objects.requireNonNull(ownerName, "Owner Name cannot be null");
        Objects.requireNonNull(dimension, "Dimension cannot be null");
        Objects.requireNonNull(points, "Points list cannot be null");

        this.pathId = pathId;
        this.pathName = pathName;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.creationTimestamp = creationTimestamp;
        this.dimension = dimension;
        this.points = points;
        this.colorArgb = 0; // Will be lazily assigned when accessed if still zero
    }

    /** New constructor including explicit color */
    public PathData(UUID pathId, String pathName, UUID ownerUUID, String ownerName, long creationTimestamp, String dimension, List<Vector3d> points, int colorArgb) {
        this(pathId, pathName, ownerUUID, ownerName, creationTimestamp, dimension, points);
        this.colorArgb = (colorArgb == 0 ? PathColors.assignColorFor(pathId) : colorArgb);
    }

    // Getters
    public UUID getPathId() {
        return pathId;
    }

    public String getPathName() {
        return pathName;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public String getDimension() {
        return dimension;
    }

    public List<Vector3d> getPoints() {
        return points;
    }

    /** Returns the ARGB color for this path, assigning one if missing (migration support). */
    public int getColorArgb() {
        if (colorArgb == 0) {
            colorArgb = PathColors.assignColorFor(pathId);
        }
        return colorArgb;
    }

    public void setColorArgb(int colorArgb) {
        if (colorArgb == 0) return; // ignore invalid
        this.colorArgb = colorArgb;
    }

    // Setter for renaming
    public void setPathName(String pathName) {
        Objects.requireNonNull(pathName, "Path Name cannot be null");
        this.pathName = pathName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathData pathData = (PathData) o;
        return pathId.equals(pathData.pathId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathId);
    }

    @Override
    public String toString() {
        return "PathData{" +
                "pathId=" + pathId +
                ", pathName='" + pathName + '\'' +
                ", ownerUUID=" + ownerUUID +
                ", pointCount=" + points.size() +
                '}';
    }
}