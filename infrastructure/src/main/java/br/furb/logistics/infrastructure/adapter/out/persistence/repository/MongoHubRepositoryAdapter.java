package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.mapper.HubDocumentMapper;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.HubMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoHubRepositoryAdapter implements HubRepositoryPort {

    private final HubMongoRepository mongoRepository;

    @Override
    public Hub save(Hub hub) {
        return HubDocumentMapper.toDomain(
                mongoRepository.save(HubDocumentMapper.toDocument(hub))
        );
    }

    @Override
    public Optional<Hub> findById(String id) {
        return mongoRepository.findById(id).map(HubDocumentMapper::toDomain);
    }

    @Override
    public List<Hub> findByCity(String city) {
        return mongoRepository.findByCity(city).stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findByCityAndState(String city, String state) {
        return mongoRepository.findByCityAndState(city, state).stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findAll() {
        return mongoRepository.findAll().stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findAllActive() {
        return mongoRepository.findByActiveTrue().stream()
                .map(HubDocumentMapper::toDomain).toList();
    }
}
