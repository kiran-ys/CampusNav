package com.campusnav.app;

import com.campusnav.model.LocationType;
import com.campusnav.service.CampusService;

public final class SampleDataLoader {
    private SampleDataLoader() { }

    public static void load(CampusService service) {
        service.addLocation("GATE-A", "Main Gate", LocationType.GATE, "Primary visitor entrance");
        service.addLocation("PARK", "Visitor Parking", LocationType.PARKING, "Parking beside the main gate");
        service.addLocation("ADMIN", "Administration Block", LocationType.ADMINISTRATION, "Admissions and university administration");
        service.addLocation("LIB", "Central Library", LocationType.LIBRARY, "Books, journals and study spaces");
        service.addLocation("CSE", "Computer Science Block", LocationType.ACADEMIC_BLOCK, "Classrooms and faculty offices");
        service.addLocation("LAB", "Innovation Laboratory", LocationType.LABORATORY, "Project and research laboratory");
        service.addLocation("CAFE", "Campus Cafeteria", LocationType.CAFETERIA, "Student and staff dining area");
        service.addLocation("HOSTEL-A", "Hostel A", LocationType.HOSTEL, "Student residence");
        service.addLocation("HEALTH", "Health Centre", LocationType.HEALTH_CENTRE, "First aid and campus medical support");
        service.addLocation("SPORT", "Sports Complex", LocationType.SPORTS_FACILITY, "Indoor and outdoor sports facilities");
        service.addLocation("ARCHIVE", "Records Archive", LocationType.SERVICE_CENTRE, "Illustrative disconnected location");

        service.addRoute("GATE-A", "PARK", 90);
        service.addRoute("GATE-A", "ADMIN", 180);
        service.addRoute("GATE-A", "LIB", 520);
        service.addRoute("PARK", "SPORT", 210);
        service.addRoute("ADMIN", "LIB", 120);
        service.addRoute("ADMIN", "CSE", 510);
        service.addRoute("LIB", "CSE", 140);
        service.addRoute("LIB", "CAFE", 110);
        service.addRoute("CSE", "LAB", 80);
        service.addRoute("CSE", "HOSTEL-A", 260);
        service.addRoute("CAFE", "HOSTEL-A", 150);
        service.addRoute("CAFE", "HEALTH", 100);
        service.addRoute("HOSTEL-A", "SPORT", 190);
    }
}
