package com.trailblazer.api;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Small centralized helper for path name matching and suggestions.
 * Keep logic deterministic and consistent between client and server.
 */
public final class PathNameMatcher {

    private PathNameMatcher() {}

    /**
     * Case-insensitive exact match against a collection of PathData. Returns the first match.
     */
    public static Optional<PathData> findByName(Collection<PathData> paths, String name) {
        if (paths == null || name == null) return Optional.empty();
        String norm = name.trim();
        for (PathData p : paths) {
            if (p == null || p.getPathName() == null) continue;
            if (p.getPathName().equalsIgnoreCase(norm)) return Optional.of(p);
        }
        return Optional.empty();
    }

    /**
     * Return up to `limit` suggestions from the provided stream of PathData
     * matching the given prefix (case-insensitive, startsWith). Duplicates
     * by name are removed and the original encounter order is preserved.
     */
    public static List<String> getSuggestions(Stream<PathData> paths, String prefix, int limit) {
        if (paths == null) return List.of();
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        return paths
            .filter(Objects::nonNull)
            .map(PathData::getPathName)
            .filter(Objects::nonNull)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(p))
            .distinct()
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());
    }

    /**
     * Convenience overload to accept two collections (e.g., myPaths + sharedPaths) and
     * search them in order (myPaths first, then shared). For exact find this returns
     * the first match encountered.
     */
    public static Optional<PathData> findByName(Collection<PathData> primary, Collection<PathData> secondary, String name) {
        Optional<PathData> p = findByName(primary, name);
        return p.isPresent() ? p : findByName(secondary, name);
    }
}
