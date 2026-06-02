package br.furb.logistics.app.config;

import br.furb.logistics.domain.port.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxMetricsConfig {

    /**
     * Exposes the number of outbox events that exhausted all publish retries (status FAILED) and require manual
     * replay, as the {@code outbox.failed.count} gauge (visible via the actuator metrics endpoint).
     */
    @Bean
    public Gauge outboxFailedGauge(MeterRegistry meterRegistry, OutboxRepository outboxRepository) {
        return Gauge.builder("outbox.failed.count", outboxRepository, OutboxRepository::countFailed)
                .description("Outbox events that exhausted all publish retries and require manual replay")
                .register(meterRegistry);
    }
}
