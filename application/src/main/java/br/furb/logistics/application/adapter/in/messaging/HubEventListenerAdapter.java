package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.BuildHubConnectionsUseCase;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventListenerAdapter {

    private final BuildHubConnectionsUseCase buildHubConnectionsUseCase;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContextSupport;

    @Value("${app.messaging.hub-events-queue:hub-events-queue.fifo}")
    private String hubEventsQueue;

    @SqsListener("${app.messaging.hub-events-queue:hub-events-queue.fifo}")
    public void onMessage(Message<String> message) {
        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startConsumerSpan("sqs.receive", message)) {
            span.tag("messaging.system", "sqs");
            span.tag("messaging.operation", "receive");
            span.tag("messaging.destination.name", hubEventsQueue);
            processMessage(message.getPayload(), span);
        }
    }

    private void processMessage(String messageBody, TraceContextSupport.ScopedSpan span) {
        String eventId = null;
        String eventType = null;
        String hubId = null;
        try {
            JsonNode root = objectMapper.readTree(messageBody);
            eventId = requireText(root, "eventId");
            eventType = requireText(root, "eventType");
            span.tag("event.id", eventId);
            span.tag("event.type", eventType);
            JsonNode payload = requireNode(root, "payload");
            hubId = requireText(payload, "hubId");
            span.tag("hub.id", hubId);

            MDC.put("eventId", eventId);
            MDC.put("eventType", eventType);
            MDC.put("hubId", hubId);
            log.info("[hub-listener] Received {} from {}", eventType, hubEventsQueue);

            switch (eventType) {
                case "hub.created" -> buildHubConnectionsUseCase.execute(eventId, hubId);
                case "hub.connections.created" -> log.debug("[hub-listener] Ignoring observability event {}", eventType);
                default -> log.warn("[hub-listener] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            span.error(e);
            log.error("[hub-listener] Error processing message from {}", hubEventsQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
        } finally {
            MDC.remove("eventId");
            MDC.remove("eventType");
            MDC.remove("hubId");
        }
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required event field: " + field);
        }
        return node.asText();
    }

    private static JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required event field: " + field);
        }
        return node;
    }
}
