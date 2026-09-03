package com.campusnav.auth;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminAuthService {
    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    private final String username;
    private final byte[] passwordDigest;
    private final boolean trustedMode;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public AdminAuthService(String username, String password) {
        this.username = require(username, "Administrator username");
        this.passwordDigest = digest(require(password, "CAMPUSNAV_ADMIN_PASSWORD"));
        this.trustedMode = false;
    }

    private AdminAuthService() { username = "test"; passwordDigest = new byte[0]; trustedMode = true; }
    public static AdminAuthService trustedForTests() { return new AdminAuthService(); }
    public boolean required() { return !trustedMode; }

    public Login login(String suppliedUsername, String suppliedPassword, String remoteAddress) {
        if (trustedMode) return new Login("trusted", "trusted", Instant.now().plus(SESSION_LIFETIME));
        String remote = remoteAddress == null ? "unknown" : remoteAddress;
        Attempts current = attempts.compute(remote, (key, old) -> old == null || old.started.plus(ATTEMPT_WINDOW).isBefore(Instant.now()) ? new Attempts(0, Instant.now()) : old);
        if (current.count >= MAX_ATTEMPTS) throw new IllegalArgumentException("Too many failed login attempts. Try again later.");
        boolean validUser = MessageDigest.isEqual(digest(username), digest(suppliedUsername == null ? "" : suppliedUsername));
        boolean validPassword = MessageDigest.isEqual(passwordDigest, digest(suppliedPassword == null ? "" : suppliedPassword));
        if (!validUser || !validPassword) {
            attempts.put(remote, new Attempts(current.count + 1, current.started));
            throw new IllegalArgumentException("Invalid administrator username or password.");
        }
        attempts.remove(remote);
        String token = token(); String csrf = token(); Instant expires = Instant.now().plus(SESSION_LIFETIME);
        sessions.put(token, new Session(csrf, expires));
        return new Login(token, csrf, expires);
    }

    public String csrf(HttpExchange exchange) {
        if (trustedMode) return "trusted";
        Session session = session(exchange);
        return session == null ? null : session.csrf;
    }

    public boolean authenticated(HttpExchange exchange) { return trustedMode || session(exchange) != null; }

    public void requireMutation(HttpExchange exchange) {
        if (trustedMode) return;
        Session session = session(exchange);
        if (session == null) throw new SecurityException("Administrator login is required.");
        String supplied = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (supplied == null || !MessageDigest.isEqual(digest(session.csrf), digest(supplied))) {
            throw new SecurityException("The security token is missing or invalid. Please sign in again.");
        }
    }

    public void logout(HttpExchange exchange) {
        String token = cookie(exchange, "campusnav_session");
        if (token != null) sessions.remove(token);
    }

    private Session session(HttpExchange exchange) {
        String token = cookie(exchange, "campusnav_session");
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null || session.expires.isBefore(Instant.now())) { sessions.remove(token); return null; }
        return session;
    }

    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static byte[] digest(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String cookie(HttpExchange exchange, String name) { String raw=exchange.getRequestHeaders().getFirst("Cookie"); if(raw==null)return null; for(String part:raw.split(";")){String[]pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals(name))return pair[1];}return null; }
    private static String require(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" must not be blank.");return value;}
    private record Session(String csrf, Instant expires) { }
    private record Attempts(int count, Instant started) { }
    public record Login(String token, String csrf, Instant expires) { }
}
