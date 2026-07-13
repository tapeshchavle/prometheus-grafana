package com.prom_grafana.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies common tags to every metric emitted by this application.
 * Tags are visible in Prometheus labels and Grafana variables.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "application", "prom-grafana",
                        "env",         "dev",
                        "team",        "platform"
                );
    }
}
