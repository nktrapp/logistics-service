package br.furb.logistics.infrastructure.persistence.repository;

import br.furb.logistics.domain.port.InboxRepository;
import br.furb.logistics.infrastructure.persistence.document.InboxDocument;
import br.furb.logistics.infrastructure.persistence.repository.spring.SpringDataInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class MongoInboxRepository implements InboxRepository {

    private final SpringDataInboxRepository springDataRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return springDataRepository.existsByEventId(eventId);
    }

    @Override
    public boolean saveIfAbsent(String eventId, String eventType) {
        try {
            save(eventId, eventType);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void save(String eventId, String eventType) {
        InboxDocument document = InboxDocument.builder()
                .eventId(eventId)
                .eventType(eventType)
                .receivedAt(Instant.now())
                .build();

        springDataRepository.save(document);
    }
}
