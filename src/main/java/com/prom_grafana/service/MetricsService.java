package com.prom_grafana.service;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central service wiring all custom Micrometer metrics.
 *
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  METRIC TYPES                                                │
 *  │  Counter   – monotonically increasing count                  │
 *  │  Gauge     – point-in-time value (can go up or down)         │
 *  │  Timer     – latency + throughput combined                   │
 *  │  DistributionSummary – histogram of numeric observations     │
 *  └──────────────────────────────────────────────────────────────┘
 */
@Service
public class MetricsService {

    // ── Order Metrics ────────────────────────────────────────────
    private final Counter ordersCreatedCounter;
    private final Counter ordersFailedCounter;
    private final Timer   orderProcessingTimer;
    private final AtomicInteger activeOrders = new AtomicInteger(0);

    // ── Inventory Metrics ─────────────────────────────────────────
    private final AtomicInteger inventoryItems   = new AtomicInteger(100);
    private final Counter       lowStockAlerts;

    // ── User Session Metrics ──────────────────────────────────────
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final Counter       loginSuccessCounter;
    private final Counter       loginFailureCounter;

    // ── External API Metrics ──────────────────────────────────────
    private final Counter externalApiCallsCounter;
    private final Counter externalApiErrorsCounter;
    private final Timer   externalApiTimer;

    public MetricsService(MeterRegistry registry) {

        // ── Orders ────────────────────────────────────────────────
        ordersCreatedCounter = Counter.builder("orders.created.total")
                .description("Total number of orders successfully created")
                .tag("type", "order")
                .register(registry);

        ordersFailedCounter = Counter.builder("orders.failed.total")
                .description("Total number of failed orders")
                .tag("reason", "validation_error")
                .register(registry);

        orderProcessingTimer = Timer.builder("order.processing.duration.seconds")
                .description("Time taken to process an order end-to-end")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        Gauge.builder("orders.active", activeOrders, AtomicInteger::get)
                .description("Number of orders currently being processed")
                .register(registry);

        // ── Inventory ─────────────────────────────────────────────
        Gauge.builder("inventory.items.total", inventoryItems, AtomicInteger::get)
                .description("Total items currently in stock")
                .register(registry);

        lowStockAlerts = Counter.builder("inventory.low.stock.alerts.total")
                .description("Number of times inventory dropped below threshold")
                .register(registry);

        // ── User Sessions ─────────────────────────────────────────
        Gauge.builder("active.user.sessions", activeSessions, AtomicInteger::get)
                .description("Number of concurrent active user sessions")
                .register(registry);

        loginSuccessCounter = Counter.builder("user.login.total")
                .description("Total login attempts")
                .tag("status", "success")
                .register(registry);

        loginFailureCounter = Counter.builder("user.login.total")
                .description("Total login attempts")
                .tag("status", "failure")
                .register(registry);

        // ── External API ──────────────────────────────────────────
        externalApiCallsCounter = Counter.builder("external.api.calls.total")
                .description("Total calls made to external APIs")
                .tag("service", "payment-gateway")
                .register(registry);

        externalApiErrorsCounter = Counter.builder("external.api.errors.total")
                .description("Total failed external API calls")
                .tag("service", "payment-gateway")
                .register(registry);

        externalApiTimer = Timer.builder("external.api.duration.seconds")
                .description("Latency of external API calls")
                .publishPercentiles(0.5, 0.95, 0.99)
                .tag("service", "payment-gateway")
                .register(registry);
    }

    // ── Public API ────────────────────────────────────────────────

    public void recordOrderCreated()     { ordersCreatedCounter.increment(); }
    public void recordOrderFailed()      { ordersFailedCounter.increment(); }
    public Timer.Sample startOrderTimer() { return Timer.start(); }
    public void stopOrderTimer(Timer.Sample sample) { sample.stop(orderProcessingTimer); }
    public void incrementActiveOrders()  { activeOrders.incrementAndGet(); }
    public void decrementActiveOrders()  { activeOrders.decrementAndGet(); }

    public int  getInventoryItems()      { return inventoryItems.get(); }
    public void restockInventory(int qty){ inventoryItems.addAndGet(qty); }
    public void consumeInventory(int qty){
        int newVal = inventoryItems.addAndGet(-qty);
        if (newVal < 20) lowStockAlerts.increment();
    }

    public void recordLoginSuccess()     { loginSuccessCounter.increment(); activeSessions.incrementAndGet(); }
    public void recordLoginFailure()     { loginFailureCounter.increment(); }
    public void recordLogout()           { activeSessions.decrementAndGet(); }

    public Timer.Sample startExternalCall()          { externalApiCallsCounter.increment(); return Timer.start(); }
    public void stopExternalCall(Timer.Sample sample) { sample.stop(externalApiTimer); }
    public void recordExternalError()                { externalApiErrorsCounter.increment(); }
}
