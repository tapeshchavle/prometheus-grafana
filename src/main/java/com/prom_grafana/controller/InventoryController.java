package com.prom_grafana.controller;

import com.prom_grafana.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * InventoryController — drives the following metrics:
 *
 *   inventory_items_total            (Gauge)
 *   inventory_low_stock_alerts_total (Counter)
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final MetricsService metrics;

    public InventoryController(MetricsService metrics) {
        this.metrics = metrics;
    }

    /** GET /api/inventory — current stock level */
    @GetMapping
    public ResponseEntity<?> getInventory() {
        int stock = metrics.getInventoryItems();
        String level = stock < 20 ? "LOW" : stock < 50 ? "MEDIUM" : "HEALTHY";
        return ResponseEntity.ok(Map.of(
                "itemsInStock", stock,
                "level",        level,
                "lowStockThreshold", 20
        ));
    }

    /** PUT /api/inventory/restock — add stock */
    @PutMapping("/restock")
    public ResponseEntity<?> restock(@RequestBody Map<String, Integer> body) {
        int qty = body.getOrDefault("quantity", 10);
        metrics.restockInventory(qty);
        return ResponseEntity.ok(Map.of(
                "restocked",    qty,
                "itemsInStock", metrics.getInventoryItems()
        ));
    }

    /** PUT /api/inventory/consume — consume stock (to trigger low-stock alert) */
    @PutMapping("/consume")
    public ResponseEntity<?> consume(@RequestBody Map<String, Integer> body) {
        int qty = body.getOrDefault("quantity", 5);
        metrics.consumeInventory(qty);
        int remaining = metrics.getInventoryItems();
        return ResponseEntity.ok(Map.of(
                "consumed",     qty,
                "itemsInStock", remaining,
                "alert",        remaining < 20 ? "LOW STOCK!" : "OK"
        ));
    }
}
