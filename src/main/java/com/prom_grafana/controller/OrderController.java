package com.prom_grafana.controller;

import com.prom_grafana.model.Order;
import com.prom_grafana.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OrderController — drives the following metrics:
 *
 *   orders_created_total             (Counter)
 *   orders_failed_total              (Counter)
 *   order_processing_duration_seconds (Timer / Histogram)
 *   orders_active                    (Gauge)
 *   http_server_requests_*           (auto — by Actuator)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MetricsService metrics;
    private final Map<String, Order> store = new ConcurrentHashMap<>();

    public OrderController(MetricsService metrics) {
        this.metrics = metrics;
    }

    /** POST /api/orders — create an order */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String  product  = (String) body.getOrDefault("product",  "widget");
        int     quantity = (int)    body.getOrDefault("quantity", 1);

        // Simulate 15 % failure rate to generate orders_failed_total
        if (ThreadLocalRandom.current().nextInt(100) < 15) {
            metrics.recordOrderFailed();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Validation failed: invalid product code"));
        }

        Timer.Sample sample = metrics.startOrderTimer();
        metrics.incrementActiveOrders();
        try {
            // Simulate processing time (50–300 ms)
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 300));
            Order order = new Order(product, quantity);
            order.setStatus("COMPLETED");
            store.put(order.getId(), order);
            metrics.recordOrderCreated();
            metrics.consumeInventory(quantity);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordOrderFailed();
            return ResponseEntity.internalServerError().body(Map.of("error", "Processing interrupted"));
        } finally {
            metrics.stopOrderTimer(sample);
            metrics.decrementActiveOrders();
        }
    }

    /** GET /api/orders — list all orders */
    @GetMapping
    public ResponseEntity<Collection<Order>> listOrders() {
        return ResponseEntity.ok(store.values());
    }

    /** GET /api/orders/{id} — get single order */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        Order o = store.get(id);
        if (o == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(o);
    }

    /** DELETE /api/orders/{id} — cancel an order */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelOrder(@PathVariable String id) {
        if (store.remove(id) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
