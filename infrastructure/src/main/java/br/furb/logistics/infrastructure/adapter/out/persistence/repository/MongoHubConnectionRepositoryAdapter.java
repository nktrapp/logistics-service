package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.mapper.HubConnectionDocumentMapper;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.HubConnectionMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MongoHubConnectionRepositoryAdapter implements HubConnectionRepositoryPort {

    private final HubConnectionMongoRepository mongoRepository;

    @Override
    public HubConnection save(HubConnection connection) {
        return HubConnectionDocumentMapper.toDomain(
                mongoRepository.save(HubConnectionDocumentMapper.toDocument(connection))
        );
    }

    @Override
    public List<HubConnection> findByOriginHubId(String originHubId) {
        return mongoRepository.findByOriginHubId(originHubId).stream()
                .map(HubConnectionDocumentMapper::toDomain).toList();
    }

    @Override
    public List<HubConnection> findAll() {
        return mongoRepository.findAll().stream()
                .map(HubConnectionDocumentMapper::toDomain).toList();
    }
}
