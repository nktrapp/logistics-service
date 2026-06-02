package br.furb.logistics.infrastructure.persistence.repository.spring;

import br.furb.logistics.infrastructure.persistence.document.InboxDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataInboxRepository extends MongoRepository<InboxDocument, String> {

    boolean existsByEventId(String eventId);
}
