package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.HubCreatedEvent;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita transacional do cadastro de hub: persiste o hub e emite hub.created no outbox de forma
 * atômica. ViaCEP e geocoding ficam fora desta transação (resolvidos por {@code RegisterHubUseCase}).
 */
@Slf4j
@RequiredArgsConstructor
public class PersistRegisteredHubUseCase {

    private final HubRepositoryPort hubRepository;
    private final OutboxRepositoryPort outboxRepository;

    @Transactional
    public Hub execute(Hub hub) {
        Hub saved = hubRepository.save(hub);
        outboxRepository.save(HubCreatedEvent.builder()
                .payload(HubCreatedEvent.Payload.builder()
                        .hubId(saved.getId())
                        .build())
                .build());

        log.info("[register-hub] Hub {} persisted and hub.created enqueued", saved.getId());
        return saved;
    }
}
