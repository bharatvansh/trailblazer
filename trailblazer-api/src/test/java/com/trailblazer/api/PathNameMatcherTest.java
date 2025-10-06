package com.trailblazer.api;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class PathNameMatcherTest {

    private PathData createPath(String name) {
        return new PathData(
            UUID.randomUUID(),
            name,
            UUID.randomUUID(),
            "TestOwner",
            System.currentTimeMillis(),
            "minecraft:overworld",
            new ArrayList<>()
        );
    }

    @Test
    void findByNameReturnsEmptyForNullCollection() {
        Optional<PathData> result = PathNameMatcher.findByName(null, "test");
        assertFalse(result.isPresent());
    }

    @Test
    void findByNameReturnsEmptyForNullName() {
        Collection<PathData> paths = List.of(createPath("test"));
        Optional<PathData> result = PathNameMatcher.findByName(paths, null);
        assertFalse(result.isPresent());
    }

    @Test
    void findByNameFindsExactMatch() {
        PathData path1 = createPath("PathA");
        PathData path2 = createPath("PathB");
        Collection<PathData> paths = List.of(path1, path2);

        Optional<PathData> result = PathNameMatcher.findByName(paths, "PathA");
        assertTrue(result.isPresent());
        assertEquals(path1, result.get());
    }

    @Test
    void findByNameIsCaseInsensitive() {
        PathData path = createPath("PathA");
        Collection<PathData> paths = List.of(path);

        assertTrue(PathNameMatcher.findByName(paths, "patha").isPresent());
        assertTrue(PathNameMatcher.findByName(paths, "PATHA").isPresent());
        assertTrue(PathNameMatcher.findByName(paths, "PaThA").isPresent());
    }

    @Test
    void findByNameTrimsInput() {
        PathData path = createPath("PathA");
        Collection<PathData> paths = List.of(path);

        Optional<PathData> result = PathNameMatcher.findByName(paths, "  PathA  ");
        assertTrue(result.isPresent());
        assertEquals(path, result.get());
    }

    @Test
    void findByNameReturnsEmptyForNoMatch() {
        PathData path = createPath("PathA");
        Collection<PathData> paths = List.of(path);

        Optional<PathData> result = PathNameMatcher.findByName(paths, "PathB");
        assertFalse(result.isPresent());
    }

    @Test
    void findByNameReturnsFirstMatch() {
        PathData path1 = createPath("Test");
        PathData path2 = createPath("Test");  // Duplicate name
        Collection<PathData> paths = List.of(path1, path2);

        Optional<PathData> result = PathNameMatcher.findByName(paths, "Test");
        assertTrue(result.isPresent());
        assertEquals(path1, result.get());
    }

    @Test
    void findByNameSkipsNullPaths() {
        PathData path = createPath("PathA");
        List<PathData> paths = new ArrayList<>();
        paths.add(null);
        paths.add(path);

        Optional<PathData> result = PathNameMatcher.findByName(paths, "PathA");
        assertTrue(result.isPresent());
        assertEquals(path, result.get());
    }

    @Test
    void getSuggestionsReturnsEmptyForNullStream() {
        List<String> suggestions = PathNameMatcher.getSuggestions(null, "test", 10);
        assertThat(suggestions).isEmpty();
    }

    @Test
    void getSuggestionsReturnsMatchingPrefixes() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("PathB"),
            createPath("TestPath"),
            createPath("Path123")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), "Path", 10
        );

        assertThat(suggestions)
            .hasSize(3)
            .contains("PathA", "PathB", "Path123")
            .doesNotContain("TestPath");
    }

    @Test
    void getSuggestionsIsCaseInsensitive() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("pathB")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), "path", 10
        );

        assertThat(suggestions).hasSize(2);
    }

    @Test
    void getSuggestionsRespectsLimit() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("PathB"),
            createPath("PathC"),
            createPath("PathD")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), "Path", 2
        );

        assertThat(suggestions).hasSize(2);
    }

    @Test
    void getSuggestionsRemovesDuplicates() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("PathA"),
            createPath("PathB")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), "Path", 10
        );

        assertThat(suggestions).hasSize(2).contains("PathA", "PathB");
    }

    @Test
    void getSuggestionsHandlesEmptyPrefix() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("TestB")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), "", 10
        );

        assertThat(suggestions).hasSize(2);
    }

    @Test
    void getSuggestionsHandlesNullPrefix() {
        List<PathData> paths = List.of(
            createPath("PathA"),
            createPath("TestB")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(
            paths.stream(), null, 10
        );

        assertThat(suggestions).hasSize(2);
    }

    @Test
    void getSuggestionsSkipsNullPaths() {
        Stream<PathData> stream = Stream.of(
            createPath("PathA"),
            null,
            createPath("PathB")
        );

        List<String> suggestions = PathNameMatcher.getSuggestions(stream, "Path", 10);

        assertThat(suggestions).hasSize(2);
    }

    @Test
    void findByNameWithTwoCollectionsSearchesPrimaryFirst() {
        PathData primary1 = createPath("PathA");
        PathData primary2 = createPath("PathB");
        PathData secondary1 = createPath("PathC");
        
        Collection<PathData> primaryPaths = List.of(primary1, primary2);
        Collection<PathData> secondaryPaths = List.of(secondary1);

        Optional<PathData> result = PathNameMatcher.findByName(
            primaryPaths, secondaryPaths, "PathA"
        );
        
        assertTrue(result.isPresent());
        assertEquals(primary1, result.get());
    }

    @Test
    void findByNameWithTwoCollectionsFallsBackToSecondary() {
        PathData primary = createPath("PathA");
        PathData secondary = createPath("PathB");
        
        Collection<PathData> primaryPaths = List.of(primary);
        Collection<PathData> secondaryPaths = List.of(secondary);

        Optional<PathData> result = PathNameMatcher.findByName(
            primaryPaths, secondaryPaths, "PathB"
        );
        
        assertTrue(result.isPresent());
        assertEquals(secondary, result.get());
    }

    @Test
    void findByNameWithTwoCollectionsPrimaryTakesPrecedence() {
        // If same name exists in both, primary should win
        PathData primary = createPath("Duplicate");
        PathData secondary = createPath("Duplicate");
        
        Collection<PathData> primaryPaths = List.of(primary);
        Collection<PathData> secondaryPaths = List.of(secondary);

        Optional<PathData> result = PathNameMatcher.findByName(
            primaryPaths, secondaryPaths, "Duplicate"
        );
        
        assertTrue(result.isPresent());
        assertEquals(primary, result.get());
    }

    @Test
    void findByNameWithTwoCollectionsReturnsEmptyIfNotFound() {
        PathData primary = createPath("PathA");
        PathData secondary = createPath("PathB");
        
        Collection<PathData> primaryPaths = List.of(primary);
        Collection<PathData> secondaryPaths = List.of(secondary);

        Optional<PathData> result = PathNameMatcher.findByName(
            primaryPaths, secondaryPaths, "PathC"
        );
        
        assertFalse(result.isPresent());
    }
}
