package com.campusnav.model;

import java.util.Objects;

public final class Location {
    private final String id;
    private final String name;
    private final LocationType type;
    private final String description;

    public Location(String id, String name, LocationType type, String description) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
    }

    public String id() { return id; }
    public String name() { return name; }
    public LocationType type() { return type; }
    public String description() { return description; }

    @Override
    public String toString() {
        return "%s | %s | %s | %s".formatted(id, name, type.displayName(), description);
    }
}
