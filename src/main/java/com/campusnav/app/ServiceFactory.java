package com.campusnav.app;

import com.campusnav.repository.PostgresCampusRepository;
import com.campusnav.service.CampusService;

import java.util.Locale;
import java.util.Map;

public final class ServiceFactory {
    private ServiceFactory() { }

    public static CampusService createFromEnvironment() { return create(System.getenv()); }

    static CampusService create(Map<String, String> environment) {
        String storage = environment.getOrDefault("CAMPUSNAV_STORAGE", "memory").trim().toLowerCase(Locale.ROOT);
        CampusService service;
        if ("memory".equals(storage)) {
            service = new CampusService();
        } else if ("postgres".equals(storage)) {
            String url = databaseUrl(environment);
            String user = environment.getOrDefault("CAMPUSNAV_DB_USER", "campusnav");
            String password = environment.getOrDefault("CAMPUSNAV_DB_PASSWORD", "campusnav");
            boolean migrate = booleanValue(environment, "CAMPUSNAV_DB_MIGRATE", true);
            service = new CampusService(new PostgresCampusRepository(url, user, password, migrate));
        } else {
            throw new IllegalArgumentException("CAMPUSNAV_STORAGE must be 'memory' or 'postgres'.");
        }
        if (booleanValue(environment, "CAMPUSNAV_SEED", true)) SampleDataLoader.ensure(service);
        return service;
    }

    private static String databaseUrl(Map<String, String> environment) {
        String explicit = environment.get("CAMPUSNAV_DB_URL");
        if (explicit != null && !explicit.isBlank()) return explicit;
        String host = environment.getOrDefault("CAMPUSNAV_DB_HOST", "127.0.0.1");
        String port = environment.getOrDefault("CAMPUSNAV_DB_PORT", "5432");
        String database = environment.getOrDefault("CAMPUSNAV_DB_NAME", "campusnav");
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    private static boolean booleanValue(Map<String, String> environment, String key, boolean defaultValue) {
        String raw = environment.get(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        throw new IllegalArgumentException(key + " must be true or false.");
    }
}
