package br.furb.logistics.infrastructure.persistence.repository;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.port.RouteRepository;
import br.furb.logistics.infrastructure.persistence.mapper.RouteDocumentMapper;
import br.furb.logistics.infrastructure.persistence.repository.spring.SpringDataRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoRouteRepository implements RouteRepository {

    private final SpringDataRouteRepository springDataRepository;

    @Override
    public Route save(Route route) {
        return RouteDocumentMapper.toDomain(
                springDataRepository.save(RouteDocumentMapper.toDocument(route))
        );
    }

    @Override
    public Optional<Route> findById(String id) {
        return springDataRepository.findById(id).map(RouteDocumentMapper::toDomain);
    }

    @Override
    public Optional<Route> findByPackageId(String packageId) {
        return springDataRepository.findByPackageId(packageId).map(RouteDocumentMapper::toDomain);
    }
}
