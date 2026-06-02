package br.furb.logistics.infrastructure.persistence.repository.spring;

import br.furb.logistics.infrastructure.persistence.document.HubConnectionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataHubConnectionRepository extends MongoRepository<HubConnectionDocument, String> {

    List<HubConnectionDocument> findByOriginHubId(String originHubId);
}
