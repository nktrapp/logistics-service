package br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.logistics.infrastructure.adapter.out.persistence.document.OutboxDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxMongoRepository extends MongoRepository<OutboxDocument, String> {

    List<OutboxDocument> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Optional<OutboxDocument> findByEventId(String eventId);
}
