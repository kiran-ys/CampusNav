package com.campusnav.repository;

import com.campusnav.model.Location;
import com.campusnav.model.Route;

import java.util.List;
import java.util.Optional;

public interface CampusRepository {
    default void verifyAvailable() { }
    void addLocation(Location location);
    Optional<Location> findLocationById(String id);
    List<Location> findLocationsByName(String query);
    List<Location> findAllLocations();
    void addRoute(Route route);
    List<Route> findAllRoutes();
}
