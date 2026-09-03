package com.campusnav.repository;

import com.campusnav.database.DatabaseMigrator;
import com.campusnav.model.Location;
import com.campusnav.model.LocationType;
import com.campusnav.model.Route;
import com.campusnav.model.DailyUsage;
import com.campusnav.model.UsageAnalytics;
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
        String sql = "INSERT INTO locations (id, name, type, description, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location.id());
            statement.setString(2, location.name());
            statement.setString(3, location.type().name());
            statement.setString(4, location.description());
            if(location.latitude()==null)statement.setNull(5,java.sql.Types.DOUBLE);else statement.setDouble(5,location.latitude());
            if(location.longitude()==null)statement.setNull(6,java.sql.Types.DOUBLE);else statement.setDouble(6,location.longitude());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalArgumentException("Location ID already exists: " + location.id());
            }
            throw databaseFailure("add location", exception);
        }
    }

    @Override public void updateLocation(Location location){String sql="UPDATE locations SET name=?,type=?,description=?,latitude=?,longitude=? WHERE id=?";try(Connection c=connection();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,location.name());s.setString(2,location.type().name());s.setString(3,location.description());if(location.latitude()==null)s.setNull(4,java.sql.Types.DOUBLE);else s.setDouble(4,location.latitude());if(location.longitude()==null)s.setNull(5,java.sql.Types.DOUBLE);else s.setDouble(5,location.longitude());s.setString(6,location.id());if(s.executeUpdate()==0)throw new IllegalArgumentException("Location does not exist: "+location.id());}catch(SQLException e){throw databaseFailure("update location",e);}}
    @Override public void deleteLocation(String id){try(Connection c=connection();PreparedStatement s=c.prepareStatement("DELETE FROM locations WHERE id=?")){s.setString(1,id);if(s.executeUpdate()==0)throw new IllegalArgumentException("Location does not exist: "+id);}catch(SQLException e){if("23503".equals(e.getSQLState()))throw new IllegalArgumentException("Location is connected to routes and cannot be deleted.");throw databaseFailure("delete location",e);}}

    @Override
    public Optional<Location> findLocationById(String id) {
        String canonical = InputValidator.canonicalId(id, "Location ID");
        String sql = "SELECT id, name, type, description, latitude, longitude FROM locations WHERE id = ?";
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
        String sql = "SELECT id, name, type, description, latitude, longitude FROM locations ORDER BY created_at, id";
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

    @Override public void updateRoute(Route route){String sql="UPDATE routes SET distance_metres=? WHERE (source_id=? AND destination_id=?) OR (source_id=? AND destination_id=?)";try(Connection c=connection();PreparedStatement s=c.prepareStatement(sql)){s.setInt(1,route.distanceMetres());s.setString(2,route.sourceId());s.setString(3,route.destinationId());s.setString(4,route.destinationId());s.setString(5,route.sourceId());if(s.executeUpdate()==0)throw new IllegalArgumentException("Route does not exist.");}catch(SQLException e){throw databaseFailure("update route",e);}}
    @Override public void deleteRoute(String source,String destination){String sql="DELETE FROM routes WHERE (source_id=? AND destination_id=?) OR (source_id=? AND destination_id=?)";try(Connection c=connection();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,source);s.setString(2,destination);s.setString(3,destination);s.setString(4,source);if(s.executeUpdate()==0)throw new IllegalArgumentException("Route does not exist.");}catch(SQLException e){throw databaseFailure("delete route",e);}}

    @Override public void recordUsage(String eventType,String algorithm,boolean success){try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO usage_events(event_type,algorithm,success) VALUES(?,?,?)")){s.setString(1,eventType);s.setString(2,algorithm);s.setBoolean(3,success);s.executeUpdate();}catch(SQLException e){throw databaseFailure("record usage",e);}}
    @Override public UsageAnalytics usageAnalytics(){String totals="SELECT COUNT(*) FILTER(WHERE event_type='ROUTE_QUERY') total,COUNT(*) FILTER(WHERE event_type='ROUTE_QUERY' AND success) successful,COUNT(*) FILTER(WHERE algorithm='BFS') bfs,COUNT(*) FILTER(WHERE algorithm='DIJKSTRA') dijkstra,COUNT(*) FILTER(WHERE event_type='ADMIN_MUTATION') mutations FROM usage_events";String daily="SELECT TO_CHAR(DATE(occurred_at),'YYYY-MM-DD') day,COUNT(*) count FROM usage_events WHERE event_type='ROUTE_QUERY' AND occurred_at>=CURRENT_DATE-INTERVAL '6 days' GROUP BY DATE(occurred_at) ORDER BY DATE(occurred_at)";try(Connection c=connection();PreparedStatement t=c.prepareStatement(totals);ResultSet r=t.executeQuery()){r.next();int total=r.getInt("total"),successful=r.getInt("successful"),bfs=r.getInt("bfs"),dijkstra=r.getInt("dijkstra"),mutations=r.getInt("mutations");List<DailyUsage> days=new ArrayList<>();try(PreparedStatement d=c.prepareStatement(daily);ResultSet dr=d.executeQuery()){while(dr.next())days.add(new DailyUsage(dr.getString("day"),dr.getInt("count")));}return new UsageAnalytics(total,successful,bfs,dijkstra,mutations,days);}catch(SQLException e){throw databaseFailure("load usage analytics",e);}}

    private Connection connection() throws SQLException { return DriverManager.getConnection(url, user, password); }

    private static Location readLocation(ResultSet results) throws SQLException {
        try {
            double lat=results.getDouble("latitude");Double latitude=results.wasNull()?null:lat;double lng=results.getDouble("longitude");Double longitude=results.wasNull()?null:lng;
            return new Location(results.getString("id"), results.getString("name"),
                    LocationType.valueOf(results.getString("type")), results.getString("description"),latitude,longitude);
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
