package com.campusnav.repository;

import com.campusnav.database.DatabaseMigrator;
import com.campusnav.model.Location;
import com.campusnav.model.LocationType;
import com.campusnav.model.Route;
import com.campusnav.validation.InputValidator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PostgresCampusRepository implements CampusRepository {
    private final String url;
    private final String user;
    private final String password;

    @Override
    public void verifyAvailable() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet results = statement.executeQuery()) {
            if (!results.next() || results.getInt(1) != 1) throw new SQLException("Database health query failed.");
        } catch (SQLException exception) {
            throw databaseFailure("verify database availability", exception);
        }
    }

    public PostgresCampusRepository(String url, String user, String password, boolean migrate) {
        this.url = require(url, "Database URL");
        this.user = require(user, "Database user");
        this.password = password == null ? "" : password;
        loadDriver();
        if (migrate) {
            try (Connection connection = connection()) {
                DatabaseMigrator.migrate(connection);
            } catch (SQLException exception) {
                throw databaseFailure("initialize database schema", exception);
            }
        }
    }

    @Override
    public void addLocation(Location location) {
        String sql = "INSERT INTO locations (id, name, type, description) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location.id());
            statement.setString(2, location.name());
            statement.setString(3, location.type().name());
            statement.setString(4, location.description());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalArgumentException("Location ID already exists: " + location.id());
            }
            throw databaseFailure("add location", exception);
        }
    }

    @Override
    public Optional<Location> findLocationById(String id) {
        String canonical = InputValidator.canonicalId(id, "Location ID");
        String sql = "SELECT id, name, type, description FROM locations WHERE id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, canonical);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readLocation(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("find location", exception);
        }
    }

    @Override
    public List<Location> findLocationsByName(String query) {
        String needle = InputValidator.requiredText(query, "Search name").toLowerCase(Locale.ROOT);
        return findAllLocations().stream()
                .filter(location -> location.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Override
    public List<Location> findAllLocations() {
        String sql = "SELECT id, name, type, description FROM locations ORDER BY created_at, id";
        List<Location> locations = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) locations.add(readLocation(results));
            return List.copyOf(locations);
        } catch (SQLException exception) {
            throw databaseFailure("list locations", exception);
        }
    }

    @Override
    public void addRoute(Route route) {
        String sql = "INSERT INTO routes (source_id, destination_id, distance_metres) VALUES (?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, route.sourceId());
            statement.setString(2, route.destinationId());
            statement.setInt(3, route.distanceMetres());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalArgumentException("A route already exists between "
                        + route.sourceId() + " and " + route.destinationId() + ".");
            }
            if ("23503".equals(exception.getSQLState())) {
                throw new IllegalArgumentException("A route references a location that does not exist.");
            }
            throw databaseFailure("add route", exception);
        }
    }

    @Override
    public List<Route> findAllRoutes() {
        String sql = "SELECT source_id, destination_id, distance_metres FROM routes ORDER BY id";
        List<Route> routes = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                routes.add(new Route(results.getString("source_id"), results.getString("destination_id"),
                        results.getInt("distance_metres")));
            }
            return List.copyOf(routes);
        } catch (SQLException exception) {
            throw databaseFailure("list routes", exception);
        }
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(url, user, password); }

    private static Location readLocation(ResultSet results) throws SQLException {
        try {
            return new Location(results.getString("id"), results.getString("name"),
                    LocationType.valueOf(results.getString("type")), results.getString("description"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Database contains an unsupported location type.", "22000", exception);
        }
    }

    private static void loadDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("PostgreSQL JDBC driver is missing. Run scripts/setup-postgres-driver.sh.", exception);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");
        return value.trim();
    }

    private static IllegalStateException databaseFailure(String action, SQLException exception) {
        return new IllegalStateException("Could not " + action + ": " + exception.getMessage(), exception);
    }
}
