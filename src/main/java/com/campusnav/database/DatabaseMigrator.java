package com.campusnav.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrator {
    private DatabaseMigrator() { }

    public static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS locations (
                        id VARCHAR(40) PRIMARY KEY,
                        name VARCHAR(120) NOT NULL CHECK (BTRIM(name) <> ''),
                        type VARCHAR(40) NOT NULL,
                        description VARCHAR(500) NOT NULL CHECK (BTRIM(description) <> ''),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS routes (
                        id BIGSERIAL PRIMARY KEY,
                        source_id VARCHAR(40) NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
                        destination_id VARCHAR(40) NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
                        distance_metres INTEGER NOT NULL CHECK (distance_metres > 0),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (source_id <> destination_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_routes_endpoint_pair
                    ON routes (LEAST(source_id, destination_id), GREATEST(source_id, destination_id))
                    """);
            statement.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION");
            statement.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS usage_events (
                        id BIGSERIAL PRIMARY KEY,
                        event_type VARCHAR(40) NOT NULL,
                        algorithm VARCHAR(20),
                        success BOOLEAN NOT NULL,
                        occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_events_occurred_at ON usage_events (occurred_at)");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
