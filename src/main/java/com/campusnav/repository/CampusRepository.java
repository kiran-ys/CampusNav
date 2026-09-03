package com.campusnav.repository;

import com.campusnav.model.Location;
import com.campusnav.model.Route;
import com.campusnav.model.UsageAnalytics;

import java.util.List;
import java.util.Optional;

public interface CampusRepository {
    default void verifyAvailable() { }
    void addLocation(Location location);
    void updateLocation(Location location);
    void deleteLocation(String id);
    Optional<Location> findLocationById(String id);
    List<Location> findLocationsByName(String query);
    List<Location> findAllLocations();
    void addRoute(Route route);
    void updateRoute(Route route);
    void deleteRoute(String sourceId, String destinationId);
    List<Route> findAllRoutes();
    default void recordUsage(String eventType, String algorithm, boolean success) { }
    default UsageAnalytics usageAnalytics() { return new UsageAnalytics(0,0,0,0,0,List.of()); }
}
