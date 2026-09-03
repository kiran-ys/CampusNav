package com.campusnav.app;

import com.campusnav.model.LocationType;
import com.campusnav.service.CampusService;

public final class SampleDataLoader {
    private SampleDataLoader() { }

    public static void load(CampusService service) {
        ensure(service);
    }

    public static void ensure(CampusService service) {
        location(service,"GATE-A", "Main Gate", LocationType.GATE, "Primary visitor entrance");
        location(service,"PARK", "Visitor Parking", LocationType.PARKING, "Parking beside the main gate");
        location(service,"ADMIN", "Administration Block", LocationType.ADMINISTRATION, "Admissions and university administration");
        location(service,"LIB", "Central Library", LocationType.LIBRARY, "Books, journals and study spaces");
        location(service,"CSE", "Computer Science Block", LocationType.ACADEMIC_BLOCK, "Classrooms and faculty offices");
        location(service,"LAB", "Innovation Laboratory", LocationType.LABORATORY, "Project and research laboratory");
        location(service,"CAFE", "Campus Cafeteria", LocationType.CAFETERIA, "Student and staff dining area");
        location(service,"HOSTEL-A", "Hostel A", LocationType.HOSTEL, "Student residence");
        location(service,"HEALTH", "Health Centre", LocationType.HEALTH_CENTRE, "First aid and campus medical support");
        location(service,"SPORT", "Sports Complex", LocationType.SPORTS_FACILITY, "Indoor and outdoor sports facilities");
        location(service,"ARCHIVE", "Records Archive", LocationType.SERVICE_CENTRE, "Illustrative disconnected location");
        location(service,"HRD", "HRD Department", LocationType.ADMINISTRATION, "Human resources and development department");
        location(service,"TECH-LAB", "Technical Laboratory", LocationType.LABORATORY, "Technical training and practical laboratory");
        location(service,"CONTROL", "Campus Control Room", LocationType.SERVICE_CENTRE, "Campus operations, safety and emergency coordination");
        location(service,"GIRLS-HOSTEL", "Girls Hostel", LocationType.HOSTEL, "Residential facility for women students");
        location(service,"PLAYGROUND", "Campus Playground", LocationType.SPORTS_FACILITY, "Outdoor games and student recreation ground");

        route(service,"GATE-A", "PARK", 90);
        route(service,"GATE-A", "ADMIN", 180);
        route(service,"GATE-A", "LIB", 520);
        route(service,"PARK", "SPORT", 210);
        route(service,"ADMIN", "LIB", 120);
        route(service,"ADMIN", "CSE", 510);
        route(service,"LIB", "CSE", 140);
        route(service,"LIB", "CAFE", 110);
        route(service,"CSE", "LAB", 80);
        route(service,"CSE", "HOSTEL-A", 260);
        route(service,"CAFE", "HOSTEL-A", 150);
        route(service,"CAFE", "HEALTH", 100);
        route(service,"HOSTEL-A", "SPORT", 190);
        route(service,"ADMIN","HRD",95);route(service,"HRD","CSE",170);
        route(service,"CSE","TECH-LAB",70);route(service,"TECH-LAB","LAB",60);
        route(service,"GATE-A","CONTROL",130);route(service,"CONTROL","ADMIN",85);
        route(service,"CAFE","GIRLS-HOSTEL",180);route(service,"HEALTH","GIRLS-HOSTEL",130);
        route(service,"GIRLS-HOSTEL","PLAYGROUND",160);route(service,"PLAYGROUND","SPORT",75);route(service,"PLAYGROUND","CAFE",220);
    }

    private static void location(CampusService service,String id,String name,LocationType type,String description){if(service.findLocationById(id).isEmpty())service.addLocation(id,name,type,description);}
    private static void route(CampusService service,String source,String destination,int distance){boolean exists=service.routes().stream().anyMatch(r->r.sourceId().equals(source)&&r.destinationId().equals(destination)||r.sourceId().equals(destination)&&r.destinationId().equals(source));if(!exists)service.addRoute(source,destination,distance);}
}
