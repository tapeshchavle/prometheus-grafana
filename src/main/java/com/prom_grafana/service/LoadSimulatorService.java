package com.prom_grafana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LoadSimulatorService — auto-generates traffic every few seconds
 * so Prometheus metrics and Grafana dashboards are immediately populated
 * without manual curl calls.
 *
 * Fires on a fixed-rate schedule after app startup.
 */
@Service
@EnableScheduling
public class LoadSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(LoadSimulatorService.class);

    private final MetricsService metrics;
    private final ServletWebServerApplicationContext server;
    private final RestTemplate http = new RestTemplate();

    private static final String[] PRODUCTS = {"widget", "gadget", "doohickey", "thingamajig"};
    private static final String[] USERS    = {"alice", "bob", "carol"};
    private static final String[] PASSWORDS = {"password123", "securepass", "mypassword"};

    public LoadSimulatorService(MetricsService metrics,
                                ServletWebServerApplicationContext server) {
        this.metrics = metrics;
        this.server  = server;
    }

    /** Simulate order creation every 3 seconds */
    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public void simulateOrders() {
        String base = "http://localhost:" + server.getWebServer().getPort();
        try {
            String product  = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
            int    quantity = ThreadLocalRandom.current().nextInt(1, 10);
            http.postForEntity(base + "/api/orders",
                    Map.of("product", product, "quantity", quantity), Object.class);
        } catch (Exception e) {
            log.debug("Simulated order call resulted in error (expected): {}", e.getMessage());
        }
    }

    /** Simulate logins/logouts every 5 seconds */
    @Scheduled(fixedDelay = 5000, initialDelay = 8000)
    public void simulateLogins() {
        String base = "http://localhost:" + server.getWebServer().getPort();
        int idx = ThreadLocalRandom.current().nextInt(USERS.length);
        try {
            // 80 % success, 20 % wrong password
            boolean succeed = ThreadLocalRandom.current().nextInt(100) < 80;
            http.postForEntity(base + "/api/users/login",
                    Map.of("username", USERS[idx],
                           "password", succeed ? PASSWORDS[idx] : "wrong"),
                    Object.class);
        } catch (Exception ignored) { /* 401 is expected */ }
    }

    /** Simulate payment gateway calls every 4 seconds */
    @Scheduled(fixedDelay = 4000, initialDelay = 6000)
    public void simulatePayments() {
        String base = "http://localhost:" + server.getWebServer().getPort();
        try {
            double amount = ThreadLocalRandom.current().nextDouble(10, 500);
            http.postForEntity(base + "/api/external/payment",
                    Map.of("amount", amount), Object.class);
        } catch (Exception e) {
            log.debug("Simulated payment call: {}", e.getMessage());
        }
    }

    /** Simulate inventory consumption every 7 seconds */
    @Scheduled(fixedDelay = 7000, initialDelay = 10000)
    public void simulateInventory() {
        String base = "http://localhost:" + server.getWebServer().getPort();
        try {
            int qty = ThreadLocalRandom.current().nextInt(1, 15);
            http.put(base + "/api/inventory/consume",
                    Map.of("quantity", qty));
        } catch (Exception e) {
            log.debug("Simulated inventory call: {}", e.getMessage());
        }
        // Restock occasionally
        if (ThreadLocalRandom.current().nextInt(100) < 30) {
            try {
                http.put(base + "/api/inventory/restock",
                        Map.of("quantity", 50));
            } catch (Exception ignored) {}
        }
    }
}
