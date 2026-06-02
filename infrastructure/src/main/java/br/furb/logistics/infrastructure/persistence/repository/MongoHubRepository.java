package br.furb.logistics.infrastructure.persistence.repository;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepository;
import br.furb.logistics.infrastructure.persistence.mapper.HubDocumentMapper;
import br.furb.logistics.infrastructure.persistence.repository.spring.SpringDataHubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoHubRepository implements HubRepository {

    private final SpringDataHubRepository springDataRepository;

    @Override
    public Hub save(Hub hub) {
        return HubDocumentMapper.toDomain(
                springDataRepository.save(HubDocumentMapper.toDocument(hub))
        );
    }

    @Override
    public Optional<Hub> findById(String id) {
        return springDataRepository.findById(id).map(HubDocumentMapper::toDomain);
    }

    @Override
    public List<Hub> findByCity(String city) {
        return springDataRepository.findByCity(city).stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findByCityAndState(String city, String state) {
        return springDataRepository.findByCityAndState(city, state).stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findAll() {
        return springDataRepository.findAll().stream()
                .map(HubDocumentMapper::toDomain).toList();
    }

    @Override
    public List<Hub> findAllActive() {
        return springDataRepository.findByActiveTrue().stream()
                .map(HubDocumentMapper::toDomain).toList();
    }
}
