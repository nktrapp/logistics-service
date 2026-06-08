package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.CalculateRouteUseCase;
import br.furb.logistics.application.usecase.RecalculateRouteUseCase;
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
@DisplayName("LogisticsEventListenerAdapter")
class LogisticsEventListenerAdapterTest {

    @Mock
    CalculateRouteUseCase calculateRouteUseCase;

    @Mock
    RecalculateRouteUseCase recalculateRouteUseCase;
    @Mock
    TraceContextSupport traceContextSupport;

    private LogisticsEventListenerAdapter buildAdapter() {
        lenient().when(traceContextSupport.startConsumerSpan(eq("sqs.receive"), any())).thenReturn(noopSpan());
        return new LogisticsEventListenerAdapter(
                calculateRouteUseCase,
                recalculateRouteUseCase,
                new ObjectMapper(),
                traceContextSupport
        );
    }

    @Test
    @DisplayName("Given a package.created message, should route it to CalculateRouteUseCase with the parsed fields")
    void shouldRoutePackageCreated() {
        LogisticsEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-1","eventType":"package.created",
                 "payload":{"packageId":"pkg-1","senderCep":"89010000","recipientCep":"89200000"}}
                """;

        adapter.onMessage(toMessage(message));

        verify(calculateRouteUseCase).execute("event-1", "pkg-1", "89010000", "89200000");
        verifyNoInteractions(recalculateRouteUseCase);
    }

    @Test
    @DisplayName("Given a package.destination.changed message, should route it to RecalculateRouteUseCase with the new CEP")
    void shouldRoutePackageDestinationChanged() {
        LogisticsEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-2","eventType":"package.destination.changed",
                 "payload":{"packageId":"pkg-1","senderCep":"89010000","newCep":"89200000"}}
                """;

        adapter.onMessage(toMessage(message));

        verify(recalculateRouteUseCase).execute("event-2", "pkg-1", "89010000", "89200000");
        verifyNoInteractions(calculateRouteUseCase);
    }

    @Test
    @DisplayName("Given an unknown event type, should ignore it without invoking any use case")
    void shouldIgnoreUnknownEventType() {
        LogisticsEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-3","eventType":"package.archived",
                 "payload":{"packageId":"pkg-1"}}
                """;

        adapter.onMessage(toMessage(message));

        verifyNoInteractions(calculateRouteUseCase, recalculateRouteUseCase);
    }

    @Test
    @DisplayName("Given a message missing a required field, should wrap the failure so the message is retried")
    void shouldThrowWhenRequiredFieldMissing() {
        LogisticsEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventType":"package.created","payload":{"packageId":"pkg-1","senderCep":"89010000","recipientCep":"89200000"}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(toMessage(message)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verifyNoInteractions(calculateRouteUseCase, recalculateRouteUseCase);
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
