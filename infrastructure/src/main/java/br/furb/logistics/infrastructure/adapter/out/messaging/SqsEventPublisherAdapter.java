package br.furb.logistics.infrastructure.adapter.out.messaging;

import br.furb.logistics.domain.port.EventPublisherPort;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.tracing.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsEventPublisherAdapter implements EventPublisherPort {

    private final SqsTemplate sqsTemplate;
    private final TraceContextSupport traceContextSupport;

    @Override
    public void publish(String queueName, String messageBody, String groupId, String dedupId) {
        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startSpan("sqs.publish", Span.Kind.PRODUCER)) {
            span.tag("messaging.system", "sqs");
            span.tag("messaging.operation", "publish");
            span.tag("messaging.destination.name", queueName);
            span.tag("messaging.message.id", dedupId);

            try {
                Map<String, String> traceHeaders = traceContextSupport.currentTraceHeaders();
                log.info("[sqs-publisher] Publishing message to queue {} (group {})", queueName, groupId);
                sqsTemplate.send(to -> {
                    to.queue(queueName)
                            .payload(messageBody)
                            .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, groupId)
                            .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, dedupId);
                    traceHeaders.forEach(to::header);
                });
                log.debug("[sqs-publisher] Message published to queue {}", queueName);
            } catch (Exception e) {
                span.error(e);
                log.error("[sqs-publisher] Failed to publish message to queue {}", queueName, e);
                throw e;
            }
        }
    }
}
