package com.campusnav.service;

import com.campusnav.graph.CampusGraph;
import com.campusnav.model.Location;
import com.campusnav.model.LocationType;
import com.campusnav.model.PathResult;
import com.campusnav.model.PathSegment;
import com.campusnav.model.Route;
import com.campusnav.repository.CampusRepository;
import com.campusnav.repository.LocationRepository;
import com.campusnav.validation.InputValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CampusService {
    private final CampusRepository repository;
    private final CampusGraph graph;

    public CampusService() { this(new LocationRepository(), new CampusGraph()); }
    public CampusService(CampusRepository repository) { this(repository, new CampusGraph()); }
    public CampusService(CampusRepository repository, CampusGraph graph) {
        this.repository = repository;
        this.graph = graph;
        for (Location location : repository.findAllLocations()) graph.addVertex(location.id());
        for (Route route : repository.findAllRoutes()) {
            graph.addBidirectionalRoute(route.sourceId(), route.destinationId(), route.distanceMetres());
        }
    }

    public synchronized Location addLocation(String id, String name, LocationType type, String description) {
        String canonical = InputValidator.canonicalId(id, "Location ID");
        Location location = new Location(canonical, InputValidator.requiredText(name, "Name"), type,
                InputValidator.requiredText(description, "Description"));
        repository.addLocation(location); graph.addVertex(canonical); return location;
    }

    public synchronized List<Location> locations() { return repository.findAllLocations(); }
    public synchronized Optional<Location> findLocationById(String id) { return repository.findLocationById(id); }
    public synchronized List<Location> findLocationsByName(String name) { return repository.findLocationsByName(name); }

    public synchronized Route addRoute(String sourceId, String destinationId, int distanceMetres) {
        String source = requireLocationId(sourceId, "Source"); String destination = requireLocationId(destinationId, "Destination");
        InputValidator.positiveDistance(distanceMetres);
        if (source.equals(destination)) throw new IllegalArgumentException("Source and destination must be different.");
        Route route = new Route(source, destination, distanceMetres);
        repository.addRoute(route);
        graph.addBidirectionalRoute(source, destination, distanceMetres);
        return route;
    }

    public synchronized List<Route> routes() { return graph.routes(); }

    public synchronized void verifyStorageAvailable() { repository.verifyAvailable(); }

    public synchronized PathResult findAnyRoute(String sourceId, String destinationId) {
        String source = requireLocationId(sourceId, "Source"); String destination = requireLocationId(destinationId, "Destination");
        return toResult(graph.breadthFirstPath(source, destination), null);
    }

    public synchronized PathResult findShortestRoute(String sourceId, String destinationId) {
        String source = requireLocationId(sourceId, "Source"); String destination = requireLocationId(destinationId, "Destination");
        CampusGraph.WeightedPath path = graph.dijkstra(source, destination);
        return toResult(path.ids(), path.found() ? path.totalDistanceMetres() : null);
    }

    private PathResult toResult(List<String> ids, Integer knownTotal) {
        if (ids.isEmpty()) return PathResult.notFound();
        List<Location> pathLocations = ids.stream().map(id -> repository.findLocationById(id).orElseThrow()).toList();
        List<PathSegment> segments = new ArrayList<>(); int total = 0;
        for (int i = 0; i + 1 < pathLocations.size(); i++) {
            Location a = pathLocations.get(i); Location b = pathLocations.get(i + 1); int distance = graph.distanceBetween(a.id(), b.id());
            segments.add(new PathSegment(a, b, distance)); total += distance;
        }
        if (knownTotal != null && knownTotal != total) throw new IllegalStateException("Path distance mismatch.");
        return PathResult.found(pathLocations, segments, total);
    }

    private String requireLocationId(String value, String field) {
        String id = InputValidator.canonicalId(value, field + " location ID");
        if (repository.findLocationById(id).isEmpty()) throw new IllegalArgumentException(field + " location does not exist: " + id);
        return id;
    }
}
