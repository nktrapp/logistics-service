package br.furb.logistics.application.usecase;

import br.furb.logistics.application.service.HubMeshService;
import br.furb.logistics.application.usecase.transaction.PersistHubConnectionsUseCase;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Reage ao evento hub.created: calcula os K vizinhos mais próximos do hub recém-criado e persiste as
 * conexões automaticamente. O cálculo (carga de hubs + haversine) fica fora da transação; só a
 * escrita (inbox + conexões + outbox) é transacional, espelhando o fluxo de cálculo de rota.
 */
@Slf4j
@RequiredArgsConstructor
public class BuildHubConnectionsUseCase {

    private final HubRepositoryPort hubRepository;
    private final HubConnectionRepositoryPort hubConnectionRepository;
    private final InboxRepositoryPort inboxRepository;
    private final HubMeshService hubMeshService;
    private final PersistHubConnectionsUseCase persistHubConnectionsUseCase;
    private final int neighbors;
    private final int averageSpeedKmh;

    public void execute(String eventId, String hubId) {
        log.info("[hub-mesh] Building automatic connections for hub {}", hubId);

        if (inboxRepository.existsByEventId(eventId)) {
            log.info("[hub-mesh] Event {} already processed, skipping", eventId);
            return;
        }

        Optional<Hub> hub = hubRepository.findById(hubId);
        if (hub.isEmpty()) {
            log.warn("[hub-mesh] Hub {} not found, skipping mesh generation", hubId);
            return;
        }

        List<Hub> activeHubs = hubRepository.findAllActive();
        List<HubConnection> existingConnections = hubConnectionRepository.findAll();

        List<HubConnection> connections = hubMeshService.computeConnections(
                hub.get(), activeHubs, existingConnections, neighbors, averageSpeedKmh);

        persistHubConnectionsUseCase.execute(eventId, hubId, connections);
    }
}
