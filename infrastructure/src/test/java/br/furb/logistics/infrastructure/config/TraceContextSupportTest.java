package br.furb.logistics.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceContextSupport")
class TraceContextSupportTest {

    @Test
    @DisplayName("extracts W3C context from SQS message attribute headers")
    void shouldExtractW3cContextFromSqsMessageAttributeHeaders() {
        TraceContextSupport.TraceCarrier carrier = TraceContextSupport.TraceCarrier.fromHeaders(Map.of(
                "Sqs_MA_traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "Sqs_MA_tracestate", "rojo=00f067aa0ba902b7"
        ));

        assertThat(carrier.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(carrier.tracestate()).isEqualTo("rojo=00f067aa0ba902b7");
    }
}
