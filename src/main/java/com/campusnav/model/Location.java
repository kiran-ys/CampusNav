package com.campusnav.model;

import java.util.Objects;

public final class Location {
    private final String id;
    private final String name;
    private final LocationType type;
    private final String description;
    private final Double latitude;
    private final Double longitude;

    public Location(String id, String name, LocationType type, String description) {
        this(id, name, type, description, null, null);
    }

    public Location(String id, String name, LocationType type, String description, Double latitude, Double longitude) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        if (latitude != null && (latitude < -90 || latitude > 90)) throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        if (longitude != null && (longitude < -180 || longitude > 180)) throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String id() { return id; }
    public String name() { return name; }
    public LocationType type() { return type; }
    public String description() { return description; }
    public Double latitude() { return latitude; }
    public Double longitude() { return longitude; }
    public boolean hasCoordinates() { return latitude != null; }

    @Override
    public String toString() {
        return "%s | %s | %s | %s".formatted(id, name, type.displayName(), description);
    }
}
