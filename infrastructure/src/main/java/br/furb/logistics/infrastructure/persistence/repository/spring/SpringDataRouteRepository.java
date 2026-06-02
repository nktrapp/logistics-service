package br.furb.logistics.infrastructure.persistence.repository.spring;

import br.furb.logistics.infrastructure.persistence.document.RouteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataRouteRepository extends MongoRepository<RouteDocument, String> {

    Optional<RouteDocument> findByPackageId(String packageId);
}
