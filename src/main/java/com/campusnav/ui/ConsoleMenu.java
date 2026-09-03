package com.campusnav.ui;

import com.campusnav.model.Location;
import com.campusnav.model.LocationType;
import com.campusnav.model.PathResult;
import com.campusnav.model.Route;
import com.campusnav.service.CampusService;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class ConsoleMenu {
    private final CampusService service; private final Scanner scanner; private final PrintStream out;
    public ConsoleMenu(CampusService service, InputStream input, PrintStream output) {
        this.service = service; this.scanner = new Scanner(input); this.out = output;
    }

    public void run() {
        out.println("\nCampusNav - Smart Campus Management & Route Optimization System");
        out.println("Representative demonstration data is pre-loaded. Distances are illustrative.");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("Choose an option: ");
            try {
                switch (choice) {
                    case "1" -> addLocation(); case "2" -> viewLocations(); case "3" -> searchById();
                    case "4" -> searchByName(); case "5" -> addRoute(); case "6" -> viewRoutes();
                    case "7" -> findAnyRoute(); case "8" -> findShortestRoute();
                    case "0" -> running = false; default -> out.println("Invalid choice. Enter a number from 0 to 8.");
                }
            } catch (IllegalArgumentException ex) { out.println("Error: " + ex.getMessage()); }
        }
        out.println("CampusNav closed safely.");
    }

    private void printMenu() {
        out.println("\n1. Add location        2. View locations"); out.println("3. Search by ID        4. Search by name");
        out.println("5. Add route           6. View routes"); out.println("7. Find any route      8. Find shortest route"); out.println("0. Exit");
    }
    private void addLocation() {
        out.println("Available types: " + java.util.Arrays.stream(LocationType.values()).map(LocationType::displayName).collect(Collectors.joining(", ")));
        Location result = service.addLocation(readLine("ID: "), readLine("Name: "), LocationType.parse(readLine("Type: ")), readLine("Description: "));
        out.println("Location added: " + result);
    }
    private void viewLocations() { printLocations(service.locations()); }
    private void searchById() { service.findLocationById(readLine("Location ID: ")).ifPresentOrElse(l -> out.println("Found: " + l), () -> out.println("No location matches that ID.")); }
    private void searchByName() { printLocations(service.findLocationsByName(readLine("Location name: "))); }
    private void addRoute() {
        String source=readLine("Source location ID: "), destination=readLine("Destination location ID: ");
        int distance=readInt("Distance in metres: "); Route route=service.addRoute(source,destination,distance);
        out.printf("Route added: %s <-> %s (%d m)%n",route.sourceId(),route.destinationId(),route.distanceMetres());
    }
    private void viewRoutes() {
        List<Route> routes=service.routes(); if(routes.isEmpty()){out.println("No routes are available.");return;}
        out.println("Routes:"); routes.forEach(r->out.printf("  %s <-> %s : %d m%n",r.sourceId(),r.destinationId(),r.distanceMetres()));
    }
    private void findAnyRoute() { displayPath("BFS route", service.findAnyRoute(readLine("Source ID: "),readLine("Destination ID: "))); }
    private void findShortestRoute() { displayPath("Dijkstra shortest route", service.findShortestRoute(readLine("Source ID: "),readLine("Destination ID: "))); }
    private void displayPath(String label, PathResult result) {
        if(!result.found()){out.println("No route exists between the selected locations.");return;}
        out.println(label + ": " + result.locations().stream().map(Location::name).collect(Collectors.joining(" -> ")));
        result.segments().forEach(s->out.printf("  %s -> %s : %d m%n",s.source().name(),s.destination().name(),s.distanceMetres()));
        out.println("Total distance: " + result.totalDistanceMetres() + " m");
    }
    private void printLocations(List<Location> values) { if(values.isEmpty()){out.println("No matching locations found.");return;} out.println("Locations:"); values.forEach(l->out.println("  "+l)); }
    private int readInt(String prompt) { String value=readLine(prompt); try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new IllegalArgumentException("Enter a valid whole number.");} }
    private String readLine(String prompt) { out.print(prompt); if(!scanner.hasNextLine()) return "0"; return scanner.nextLine(); }
}
