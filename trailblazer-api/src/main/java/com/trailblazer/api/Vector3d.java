package com.trailblazer.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable 3D vector for coordinate storage.
 */
public class Vector3d implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double x;
    private final double y;
    private final double z;

    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return "Vector3d{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector3d vector3d = (Vector3d) o;
        return Double.compare(vector3d.x, x) == 0 &&
                Double.compare(vector3d.y, y) == 0 &&
                Double.compare(vector3d.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}