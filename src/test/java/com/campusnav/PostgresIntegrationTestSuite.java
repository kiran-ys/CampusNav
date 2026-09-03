package com.campusnav;

import com.campusnav.database.DatabaseMigrator;
import com.campusnav.model.LocationType;
import com.campusnav.repository.PostgresCampusRepository;
import com.campusnav.service.CampusService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class PostgresIntegrationTestSuite {
    private int passed;
    private int failed;

    public static void main(String[] args) throws Exception { new PostgresIntegrationTestSuite().run(); }

    private void run() throws Exception {
        String url = System.getenv().getOrDefault("CAMPUSNAV_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5432/campusnav_test");
        String user = System.getenv().getOrDefault("CAMPUSNAV_TEST_DB_USER", "campusnav");
        String password = System.getenv().getOrDefault("CAMPUSNAV_TEST_DB_PASSWORD", "campusnav");

        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            DatabaseMigrator.migrate(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("TRUNCATE TABLE routes, locations RESTART IDENTITY");
            }
        }

        PostgresCampusRepository repository = new PostgresCampusRepository(url, user, password, true);
        CampusService first = new CampusService(repository);
        test("stores locations", () -> {
            first.addLocation("AA", "Gate", LocationType.GATE, "Entrance");
            first.addLocation("BB", "Library", LocationType.LIBRARY, "Books");
            first.addLocation("CC", "Laboratory", LocationType.LABORATORY, "Projects");
            check(first.locations().size() == 3, "expected three stored locations");
        });
        test("stores routes", () -> {
            first.addRoute("AA", "BB", 90);
            first.addRoute("BB", "CC", 60);
            check(first.routes().size() == 2, "expected two stored routes");
        });
        test("restores data after restart", () -> {
            CampusService restarted = new CampusService(new PostgresCampusRepository(url, user, password, true));
            check(restarted.locations().size() == 3, "locations were not restored");
            check(restarted.routes().size() == 2, "routes were not restored");
            var path = restarted.findShortestRoute("AA", "CC");
            check(path.found(), "restored graph path was not found");
            check(path.totalDistanceMetres() == 150, "restored shortest distance should be 150");
        });
        test("database rejects duplicate location IDs", () ->
                expectFailure(() -> first.addLocation("AA", "Again", LocationType.OTHER, "Duplicate"), "already exists"));
        test("database rejects reverse duplicate routes", () ->
                expectFailure(() -> first.addRoute("BB", "AA", 12), "already exists"));

        System.out.printf("%nPostgreSQL integration tests: %d passed, %d failed%n", passed, failed);
        if (failed > 0) throw new AssertionError("PostgreSQL integration suite failed.");
    }

    private void test(String name, Checked action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable exception) {
            failed++;
            System.out.println("FAIL  " + name + " -> " + exception.getMessage());
        }
    }

    private static void expectFailure(Checked action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + message);
        } catch (IllegalArgumentException exception) {
            check(exception.getMessage().contains(message), "Unexpected message: " + exception.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
}
