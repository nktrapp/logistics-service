package br.furb.logistics.infrastructure.adapter.out.messaging;

import br.furb.logistics.infrastructure.config.TraceContextSupport;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsEventPublisherAdapter")
class SqsEventPublisherAdapterTest {

    @Mock
    SqsTemplate sqsTemplate;
    @Mock
    TraceContextSupport traceContextSupport;

    @Test
    @DisplayName("Given an event, should send it to the queue with FIFO and W3C trace headers")
    void shouldSendWithFifoAndTraceHeaders() {
        when(traceContextSupport.startSpan("sqs.publish", Span.Kind.PRODUCER)).thenReturn(noopSpan());
        when(traceContextSupport.currentTraceHeaders()).thenReturn(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "tracestate", "rojo=00f067aa0ba902b7"
        ));
        SqsEventPublisherAdapter adapter = new SqsEventPublisherAdapter(sqsTemplate, traceContextSupport);

        adapter.publish("logistics-events-queue.fifo", "message-body", "group-1", "dedup-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<SqsSendOptions<Object>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());

        @SuppressWarnings("unchecked")
        SqsSendOptions<Object> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);

        verify(options).queue("logistics-events-queue.fifo");
        verify(options).payload("message-body");
        verify(options).header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, "group-1");
        verify(options).header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, "dedup-1");
        verify(options).header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        verify(options).header("tracestate", "rojo=00f067aa0ba902b7");
    }

    private TraceContextSupport.ScopedSpan noopSpan() {
        return new TraceContextSupport.ScopedSpan(mock(Span.class), mock(Tracer.SpanInScope.class));
    }
}
