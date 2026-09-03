package com.campusnav.model;

import java.util.List;

public final class PathResult {
    private final boolean found;
    private final List<Location> locations;
    private final List<PathSegment> segments;
    private final int totalDistanceMetres;

    private PathResult(boolean found, List<Location> locations,
                       List<PathSegment> segments, int totalDistanceMetres) {
        this.found = found;
        this.locations = List.copyOf(locations);
        this.segments = List.copyOf(segments);
        this.totalDistanceMetres = totalDistanceMetres;
    }

    public static PathResult found(List<Location> locations, List<PathSegment> segments, int totalDistance) {
        return new PathResult(true, locations, segments, totalDistance);
    }

    public static PathResult notFound() {
        return new PathResult(false, List.of(), List.of(), 0);
    }

    public boolean found() { return found; }
    public List<Location> locations() { return locations; }
    public List<PathSegment> segments() { return segments; }
    public int totalDistanceMetres() { return totalDistanceMetres; }
}
