package br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.logistics.infrastructure.adapter.out.persistence.document.HubDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HubMongoRepository extends MongoRepository<HubDocument, String> {

    List<HubDocument> findByCity(String city);

    List<HubDocument> findByCityAndState(String city, String state);

    List<HubDocument> findByActiveTrue();
}
