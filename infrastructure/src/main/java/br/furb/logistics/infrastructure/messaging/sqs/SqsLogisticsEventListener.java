package br.furb.logistics.infrastructure.messaging.sqs;

import br.furb.logistics.core.usecase.CalculateRouteUseCase;
import br.furb.logistics.core.usecase.RecalculateRouteUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsLogisticsEventListener {

    private final CalculateRouteUseCase calculateRouteUseCase;
    private final RecalculateRouteUseCase recalculateRouteUseCase;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.inbound-queue:package-events-queue}")
    private String inboundQueue;

    @SqsListener("${app.messaging.inbound-queue:package-events-queue}")
    public void onMessage(String message) {
        try {
            log.info("[sqs-listener] Received message from {}", inboundQueue);

            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();
            String eventType = root.get("eventType").asText();
            JsonNode payload = root.get("payload");

            switch (eventType) {
                case "package.created" -> {
                    String packageId = payload.get("packageId").asText();
                    String senderCep = payload.get("senderCep").asText();
                    String recipientCep = payload.get("recipientCep").asText();
                    calculateRouteUseCase.execute(eventId, packageId, senderCep, recipientCep);
                }
                case "package.destination.changed" -> {
                    String packageId = payload.get("packageId").asText();
                    String newCep = payload.get("newCep").asText();
                    recalculateRouteUseCase.execute(eventId, packageId, newCep);
                }
                default -> log.warn("[sqs-listener] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[sqs-listener] Error processing message from {}", inboundQueue, e);
            throw new RuntimeException("Failed to process SQS message", e);
        }
    }
}
