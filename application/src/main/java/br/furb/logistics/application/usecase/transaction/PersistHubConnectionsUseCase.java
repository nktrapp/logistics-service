package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.HubConnectionsCreatedEvent;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PersistHubConnectionsUseCase {

    private final HubConnectionRepositoryPort hubConnectionRepository;
    private final OutboxRepositoryPort outboxRepository;
    private final InboxRepositoryPort inboxRepository;

    @Transactional
    public List<HubConnection> execute(String eventId, String hubId, List<HubConnection> connections) {
        if (!inboxRepository.saveIfAbsent(eventId, "hub.created")) {
            log.info("[hub-mesh] Event {} already processed, skipping", eventId);
            return List.of();
        }

        if (connections.isEmpty()) {
            log.info("[hub-mesh] No automatic connections to create for hub {}", hubId);
            return List.of();
        }

        List<HubConnection> saved = connections.stream()
                .map(hubConnectionRepository::save)
                .toList();

        List<String> neighborHubIds = saved.stream()
                .map(HubConnection::getDestinationHubId)
                .toList();

        outboxRepository.save(HubConnectionsCreatedEvent.builder()
                .payload(HubConnectionsCreatedEvent.Payload.builder()
                        .hubId(hubId)
                        .neighborHubIds(neighborHubIds)
                        .build())
                .build());

        log.info("[hub-mesh] Created {} automatic connections for hub {}", saved.size(), hubId);
        return saved;
    }
}
