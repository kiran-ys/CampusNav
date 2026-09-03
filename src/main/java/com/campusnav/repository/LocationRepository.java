package com.campusnav.repository;

import com.campusnav.model.Location;
import com.campusnav.model.Route;
import com.campusnav.model.UsageAnalytics;
import com.campusnav.validation.InputValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LocationRepository implements CampusRepository {
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final List<Route> routes = new ArrayList<>();
    private int routeQueries, successfulQueries, bfsQueries, dijkstraQueries, adminMutations;

    @Override
    public void addLocation(Location location) {
        if (locations.putIfAbsent(location.id(), location) != null) {
            throw new IllegalArgumentException("Location ID already exists: " + location.id());
        }
    }

    @Override
    public void updateLocation(Location location) {
        if (!locations.containsKey(location.id())) throw new IllegalArgumentException("Location does not exist: " + location.id());
        locations.put(location.id(), location);
    }

    @Override
    public void deleteLocation(String id) {
        for (Route route : routes) if (route.sourceId().equals(id) || route.destinationId().equals(id)) {
            throw new IllegalArgumentException("Location is connected to routes and cannot be deleted.");
        }
        if (locations.remove(id) == null) throw new IllegalArgumentException("Location does not exist: " + id);
    }

    @Override
    public Optional<Location> findLocationById(String id) {
        return Optional.ofNullable(locations.get(InputValidator.canonicalId(id, "Location ID")));
    }

    @Override
    public List<Location> findLocationsByName(String query) {
        String needle = InputValidator.requiredText(query, "Search name").toLowerCase(Locale.ROOT);
        List<Location> result = new ArrayList<>();
        for (Location location : locations.values()) {
            if (location.name().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(location);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<Location> findAllLocations() {
        return List.copyOf(locations.values());
    }

    @Override
    public void addRoute(Route route) {
        for (Route existing : routes) {
            boolean sameDirection = existing.sourceId().equals(route.sourceId())
                    && existing.destinationId().equals(route.destinationId());
            boolean reverseDirection = existing.sourceId().equals(route.destinationId())
                    && existing.destinationId().equals(route.sourceId());
            if (sameDirection || reverseDirection) {
                throw new IllegalArgumentException("A route already exists between "
                        + route.sourceId() + " and " + route.destinationId() + ".");
            }
        }
        routes.add(route);
    }

    @Override
    public void updateRoute(Route route) {
        for (int i=0;i<routes.size();i++) {
            Route existing=routes.get(i);
            if (sameEndpoints(existing,route.sourceId(),route.destinationId())) { routes.set(i,route); return; }
        }
        throw new IllegalArgumentException("Route does not exist.");
    }

    @Override
    public void deleteRoute(String sourceId, String destinationId) {
        if (!routes.removeIf(route -> sameEndpoints(route,sourceId,destinationId))) throw new IllegalArgumentException("Route does not exist.");
    }

    private static boolean sameEndpoints(Route route,String sourceId,String destinationId) {
        return route.sourceId().equals(sourceId)&&route.destinationId().equals(destinationId)
                ||route.sourceId().equals(destinationId)&&route.destinationId().equals(sourceId);
    }

    @Override
    public List<Route> findAllRoutes() { return List.copyOf(routes); }

    @Override public void recordUsage(String eventType,String algorithm,boolean success){if("ROUTE_QUERY".equals(eventType)){routeQueries++;if(success)successfulQueries++;if("BFS".equals(algorithm))bfsQueries++;if("DIJKSTRA".equals(algorithm))dijkstraQueries++;}else if("ADMIN_MUTATION".equals(eventType))adminMutations++;}
    @Override public UsageAnalytics usageAnalytics() { return new UsageAnalytics(routeQueries,successfulQueries,bfsQueries,dijkstraQueries,adminMutations,List.of()); }
}
