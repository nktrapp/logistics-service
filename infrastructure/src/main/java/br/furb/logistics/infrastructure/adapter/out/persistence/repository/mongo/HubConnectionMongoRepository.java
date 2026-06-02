package br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.logistics.infrastructure.adapter.out.persistence.document.HubConnectionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HubConnectionMongoRepository extends MongoRepository<HubConnectionDocument, String> {

    List<HubConnectionDocument> findByOriginHubId(String originHubId);
}
