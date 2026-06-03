package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.RouteFailedEvent;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class PersistFailedRouteUseCase {

    private final OutboxRepositoryPort outboxRepository;
    private final InboxRepositoryPort inboxRepository;

    @Transactional
    public void execute(String eventId, String sourceEventType, String packageId, String reason) {
        if (!inboxRepository.saveIfAbsent(eventId, sourceEventType)) {
            log.info("[route-failed] Event {} already processed, skipping", eventId);
            return;
        }

        outboxRepository.save(RouteFailedEvent.builder()
                .payload(RouteFailedEvent.Payload.builder()
                        .packageId(packageId)
                        .reason(reason)
                        .build())
                .build());

        log.warn("[route-failed] Emitted route.failed for package {} (reason: {})", packageId, reason);
    }
}
