package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.CalculateRouteUseCase;
import br.furb.logistics.application.usecase.RecalculateRouteUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsEventListenerAdapter {

    private final CalculateRouteUseCase calculateRouteUseCase;
    private final RecalculateRouteUseCase recalculateRouteUseCase;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.inbound-queue:package-events-queue.fifo}")
    private String inboundQueue;

    @SqsListener("${app.messaging.inbound-queue:package-events-queue.fifo}")
    public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = requireText(root, "eventId");
            String eventType = requireText(root, "eventType");
            JsonNode payload = requireNode(root, "payload");
            String packageId = requireText(payload, "packageId");

            MDC.put("eventId", eventId);
            MDC.put("packageId", packageId);
            try {
                log.info("[sqs-listener] Received {} from {}", eventType, inboundQueue);

                switch (eventType) {
                    case "package.created" -> calculateRouteUseCase.execute(eventId, packageId,
                            requireText(payload, "senderCep"), requireText(payload, "recipientCep"));
                    case "package.destination.changed" -> recalculateRouteUseCase.execute(eventId, packageId,
                            requireText(payload, "newCep"));
                    default -> log.warn("[sqs-listener] Unknown event type: {}", eventType);
                }
            } finally {
                MDC.remove("eventId");
                MDC.remove("packageId");
            }
        } catch (Exception e) {
            log.error("[sqs-listener] Error processing message from {}", inboundQueue, e);
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
