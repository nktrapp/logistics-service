package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.document.InboxDocument;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.InboxMongoRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class MongoInboxRepositoryAdapter implements InboxRepositoryPort {

    private final InboxMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public boolean existsByEventId(String eventId) {
        return mongoRepository.existsByEventId(eventId);
    }

    // Upsert idempotente: um insert duplicado dentro da transação Mongo a abortaria; o match no upsert a mantém sã.
    @Override
    public boolean saveIfAbsent(String eventId, String eventType) {
        Query query = Query.query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .setOnInsert("eventId", eventId)
                .setOnInsert("eventType", eventType)
                .setOnInsert("receivedAt", Instant.now());

        UpdateResult result = mongoTemplate.upsert(query, update, InboxDocument.class);
        return result.getUpsertedId() != null;
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
