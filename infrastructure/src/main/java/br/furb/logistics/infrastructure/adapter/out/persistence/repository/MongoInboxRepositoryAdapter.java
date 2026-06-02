package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.document.InboxDocument;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.InboxMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class MongoInboxRepositoryAdapter implements InboxRepositoryPort {

    private final InboxMongoRepository mongoRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return mongoRepository.existsByEventId(eventId);
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

        mongoRepository.save(document);
    }
}
