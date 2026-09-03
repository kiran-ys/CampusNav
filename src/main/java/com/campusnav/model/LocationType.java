package com.campusnav.model;

import java.util.Locale;

public enum LocationType {
    ACADEMIC_BLOCK("Academic Block"),
    ADMINISTRATION("Administration"),
    LABORATORY("Laboratory"),
    LIBRARY("Library"),
    AUDITORIUM("Auditorium"),
    HOSTEL("Hostel"),
    CAFETERIA("Cafeteria"),
    SPORTS_FACILITY("Sports Facility"),
    HEALTH_CENTRE("Health Centre"),
    PARKING("Parking"),
    GATE("Gate"),
    SERVICE_CENTRE("Service Centre"),
    OTHER("Other");

    private final String displayName;

    LocationType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static LocationType parse(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        for (LocationType type : values()) {
            if (type.name().equals(normalized)
                    || type.displayName.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown location type: " + value);
    }
}
