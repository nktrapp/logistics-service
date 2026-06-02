package br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo;

import br.furb.logistics.infrastructure.adapter.out.persistence.document.RouteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RouteMongoRepository extends MongoRepository<RouteDocument, String> {

    Optional<RouteDocument> findByPackageId(String packageId);
}
