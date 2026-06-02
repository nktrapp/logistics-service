package br.furb.logistics.infrastructure.persistence.repository;

import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepository;
import br.furb.logistics.infrastructure.persistence.mapper.HubConnectionDocumentMapper;
import br.furb.logistics.infrastructure.persistence.repository.spring.SpringDataHubConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MongoHubConnectionRepository implements HubConnectionRepository {

    private final SpringDataHubConnectionRepository springDataRepository;

    @Override
    public HubConnection save(HubConnection connection) {
        return HubConnectionDocumentMapper.toDomain(
                springDataRepository.save(HubConnectionDocumentMapper.toDocument(connection))
        );
    }

    @Override
    public List<HubConnection> findByOriginHubId(String originHubId) {
        return springDataRepository.findByOriginHubId(originHubId).stream()
                .map(HubConnectionDocumentMapper::toDomain).toList();
    }

    @Override
    public List<HubConnection> findAll() {
        return springDataRepository.findAll().stream()
                .map(HubConnectionDocumentMapper::toDomain).toList();
    }
}
