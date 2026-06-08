package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.BuildHubConnectionsUseCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventListenerAdapter {

    private final BuildHubConnectionsUseCase buildHubConnectionsUseCase;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.hub-events-queue:hub-events-queue.fifo}")
    private String hubEventsQueue;

    @SqsListener("${app.messaging.hub-events-queue:hub-events-queue.fifo}")
    public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = requireText(root, "eventId");
            String eventType = requireText(root, "eventType");
            JsonNode payload = requireNode(root, "payload");
            String hubId = requireText(payload, "hubId");

            MDC.put("eventId", eventId);
            MDC.put("hubId", hubId);
            try {
                log.info("[hub-listener] Received {} from {}", eventType, hubEventsQueue);

                switch (eventType) {
                    case "hub.created" -> buildHubConnectionsUseCase.execute(eventId, hubId);
                    case "hub.connections.created" -> log.debug("[hub-listener] Ignoring observability event {}", eventType);
                    default -> log.warn("[hub-listener] Unknown event type: {}", eventType);
                }
            } finally {
                MDC.remove("eventId");
                MDC.remove("hubId");
            }
        } catch (Exception e) {
            log.error("[hub-listener] Error processing message from {}", hubEventsQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
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
