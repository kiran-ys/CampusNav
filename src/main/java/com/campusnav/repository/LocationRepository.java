package com.campusnav.repository;

import com.campusnav.model.Location;
import com.campusnav.model.Route;
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

    @Override
    public void addLocation(Location location) {
        if (locations.putIfAbsent(location.id(), location) != null) {
            throw new IllegalArgumentException("Location ID already exists: " + location.id());
        }
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
    public List<Route> findAllRoutes() { return List.copyOf(routes); }
}
