package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.BuildHubConnectionsUseCase;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("HubEventListenerAdapter")
class HubEventListenerAdapterTest {

    @Mock
    BuildHubConnectionsUseCase buildHubConnectionsUseCase;
    @Mock
    TraceContextSupport traceContextSupport;

    private HubEventListenerAdapter buildAdapter() {
        lenient().when(traceContextSupport.startConsumerSpan(eq("sqs.receive"), any())).thenReturn(noopSpan());
        return new HubEventListenerAdapter(buildHubConnectionsUseCase, new ObjectMapper(), traceContextSupport);
    }

    @Test
    @DisplayName("Given a hub.created message, should route it to BuildHubConnectionsUseCase with the parsed fields")
    void shouldRouteHubCreated() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-1","eventType":"hub.created","payload":{"hubId":"hub-1"}}
                """;

        adapter.onMessage(toMessage(message));

        verify(buildHubConnectionsUseCase).execute("event-1", "hub-1");
    }

    @Test
    @DisplayName("Given a hub.connections.created message, should ignore it without invoking the mesh use case")
    void shouldIgnoreConnectionsCreated() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-2","eventType":"hub.connections.created","payload":{"hubId":"hub-1"}}
                """;

        adapter.onMessage(toMessage(message));

        verifyNoInteractions(buildHubConnectionsUseCase);
    }

    @Test
    @DisplayName("Given a message missing a required field, should wrap the failure so the message is retried")
    void shouldThrowWhenRequiredFieldMissing() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventType":"hub.created","payload":{"hubId":"hub-1"}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(toMessage(message)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verifyNoInteractions(buildHubConnectionsUseCase);
    }

    private Message<String> toMessage(String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("Sqs_MA_traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .build();
    }

    private TraceContextSupport.ScopedSpan noopSpan() {
        return new TraceContextSupport.ScopedSpan(mock(Span.class), mock(Tracer.SpanInScope.class));
    }
}
