package com.prom_grafana.controller;

import com.prom_grafana.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserController — drives the following metrics:
 *
 *   active_user_sessions   (Gauge)
 *   user_login_total       (Counter — tag: status=success|failure)
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final MetricsService metrics;
    // Simple in-memory session store (token → username)
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    // Hardcoded users for demo — in prod this would hit a DB
    private static final Map<String, String> USERS = Map.of(
            "alice", "password123",
            "bob",   "securepass",
            "carol", "mypassword"
    );

    public UserController(MetricsService metrics) {
        this.metrics = metrics;
    }

    /** POST /api/users/login */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (USERS.getOrDefault(username, "").equals(password)) {
            String token = username + "-token-" + System.currentTimeMillis();
            activeSessions.add(token);
            metrics.recordLoginSuccess();
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "token",   token,
                    "user",    username
            ));
        } else {
            metrics.recordLoginFailure();
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid credentials"
            ));
        }
    }

    /** POST /api/users/logout */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String token = body.getOrDefault("token", "");
        if (activeSessions.remove(token)) {
            metrics.recordLogout();
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        }
        return ResponseEntity.status(404).body(Map.of("error", "Session not found"));
    }

    /** GET /api/users/sessions — current session count */
    @GetMapping("/sessions")
    public ResponseEntity<?> sessions() {
        return ResponseEntity.ok(Map.of(
                "activeSessions", activeSessions.size(),
                "tokens",         activeSessions
        ));
    }
}
