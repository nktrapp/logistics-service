package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.mapper.RouteDocumentMapper;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.RouteMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoRouteRepositoryAdapter implements RouteRepositoryPort {

    private final RouteMongoRepository mongoRepository;

    @Override
    public Route save(Route route) {
        return RouteDocumentMapper.toDomain(
                mongoRepository.save(RouteDocumentMapper.toDocument(route))
        );
    }

    @Override
    public Optional<Route> findById(String id) {
        return mongoRepository.findById(id).map(RouteDocumentMapper::toDomain);
    }

    @Override
    public Optional<Route> findByPackageId(String packageId) {
        return mongoRepository.findByPackageId(packageId).map(RouteDocumentMapper::toDomain);
    }
}
