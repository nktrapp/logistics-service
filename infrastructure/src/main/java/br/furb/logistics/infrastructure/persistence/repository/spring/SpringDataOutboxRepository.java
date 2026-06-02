package br.furb.logistics.infrastructure.persistence.repository.spring;

import br.furb.logistics.infrastructure.persistence.document.OutboxDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataOutboxRepository extends MongoRepository<OutboxDocument, String> {

    List<OutboxDocument> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Optional<OutboxDocument> findByEventId(String eventId);
}
