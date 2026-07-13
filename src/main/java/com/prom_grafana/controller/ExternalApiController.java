package com.prom_grafana.controller;

import com.prom_grafana.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ExternalApiController — simulates calls to an external payment gateway.
 *
 * Metrics driven:
 *   external_api_calls_total         (Counter)
 *   external_api_errors_total        (Counter)
 *   external_api_duration_seconds    (Timer / Histogram)
 */
@RestController
@RequestMapping("/api/external")
public class ExternalApiController {

    private final MetricsService metrics;

    public ExternalApiController(MetricsService metrics) {
        this.metrics = metrics;
    }

    /**
     * POST /api/external/payment — simulate a payment gateway call.
     * 20 % error rate, 100–800 ms latency.
     */
    @PostMapping("/payment")
    public ResponseEntity<?> processPayment(@RequestBody Map<String, Object> body) {
        Timer.Sample sample = metrics.startExternalCall();
        try {
            // Simulate network latency
            Thread.sleep(ThreadLocalRandom.current().nextLong(100, 800));

            // Simulate 20 % gateway error
            if (ThreadLocalRandom.current().nextInt(100) < 20) {
                metrics.recordExternalError();
                return ResponseEntity.status(502).body(Map.of(
                        "error",   "Payment gateway timeout",
                        "gateway", "payment-gateway"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status",        "APPROVED",
                    "transactionId", "TXN-" + System.currentTimeMillis(),
                    "amount",        body.getOrDefault("amount", 0)
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordExternalError();
            return ResponseEntity.internalServerError().body(Map.of("error", "Interrupted"));
        } finally {
            metrics.stopExternalCall(sample);
        }
    }

    /**
     * GET /api/external/status — health check of external service (fast path).
     */
    @GetMapping("/status")
    public ResponseEntity<?> gatewayStatus() {
        return ResponseEntity.ok(Map.of(
                "gateway", "payment-gateway",
                "status",  "UP",
                "latencyMs", ThreadLocalRandom.current().nextInt(5, 50)
        ));
    }
}
