package com.trailblazer.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vector3dTest {

    @Test
    void constructorSetsCoordinates() {
        Vector3d vector = new Vector3d(1.5, 2.5, 3.5);
        assertEquals(1.5, vector.getX());
        assertEquals(2.5, vector.getY());
        assertEquals(3.5, vector.getZ());
    }

    @Test
    void equalsComparesCoordinates() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(1.0, 2.0, 3.0);
        assertEquals(v1, v2);
    }

    @Test
    void equalsSameInstance() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        assertEquals(v1, v1);
    }

    @Test
    void notEqualsNull() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        assertNotEquals(v1, null);
    }

    @Test
    void notEqualsDifferentClass() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        assertNotEquals(v1, "not a vector");
    }

    @Test
    void notEqualsDifferentX() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(1.1, 2.0, 3.0);
        assertNotEquals(v1, v2);
    }

    @Test
    void notEqualsDifferentY() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(1.0, 2.1, 3.0);
        assertNotEquals(v1, v2);
    }

    @Test
    void notEqualsDifferentZ() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(1.0, 2.0, 3.1);
        assertNotEquals(v1, v2);
    }

    @Test
    void hashCodeConsistent() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(1.0, 2.0, 3.0);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void hashCodeDifferentForDifferentVectors() {
        Vector3d v1 = new Vector3d(1.0, 2.0, 3.0);
        Vector3d v2 = new Vector3d(4.0, 5.0, 6.0);
        // While hash codes can collide, these specific values should differ
        assertNotEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void toStringContainsCoordinates() {
        Vector3d vector = new Vector3d(1.5, 2.5, 3.5);
        String str = vector.toString();
        assertTrue(str.contains("1.5"));
        assertTrue(str.contains("2.5"));
        assertTrue(str.contains("3.5"));
        assertTrue(str.contains("Vector3d"));
    }

    @Test
    void immutabilityNoSetters() {
        // This test documents that Vector3d is immutable
        // It has no setters, only getters
        Vector3d vector = new Vector3d(1.0, 2.0, 3.0);
        
        // Verify getters work
        assertEquals(1.0, vector.getX());
        assertEquals(2.0, vector.getY());
        assertEquals(3.0, vector.getZ());
        
        // There's no way to change these values
        // This is enforced by the compiler - no setX, setY, setZ methods exist
    }

    @Test
    void supportsNegativeCoordinates() {
        Vector3d vector = new Vector3d(-1.5, -2.5, -3.5);
        assertEquals(-1.5, vector.getX());
        assertEquals(-2.5, vector.getY());
        assertEquals(-3.5, vector.getZ());
    }

    @Test
    void supportsZeroCoordinates() {
        Vector3d vector = new Vector3d(0.0, 0.0, 0.0);
        assertEquals(0.0, vector.getX());
        assertEquals(0.0, vector.getY());
        assertEquals(0.0, vector.getZ());
    }

    @Test
    void supportsLargeCoordinates() {
        double large = 1000000.0;
        Vector3d vector = new Vector3d(large, large, large);
        assertEquals(large, vector.getX());
        assertEquals(large, vector.getY());
        assertEquals(large, vector.getZ());
    }

    @Test
    void supportsSmallCoordinates() {
        double small = 0.0000001;
        Vector3d vector = new Vector3d(small, small, small);
        assertEquals(small, vector.getX());
        assertEquals(small, vector.getY());
        assertEquals(small, vector.getZ());
    }
}
