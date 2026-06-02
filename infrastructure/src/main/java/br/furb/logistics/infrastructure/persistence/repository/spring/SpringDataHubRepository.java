package br.furb.logistics.infrastructure.persistence.repository.spring;

import br.furb.logistics.infrastructure.persistence.document.HubDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataHubRepository extends MongoRepository<HubDocument, String> {

    List<HubDocument> findByCity(String city);

    List<HubDocument> findByCityAndState(String city, String state);

    List<HubDocument> findByActiveTrue();
}
