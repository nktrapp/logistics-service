package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.mapper.HubMapper;
import br.furb.logistics.domain.port.HubRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ListHubsUseCase {

    private final HubRepositoryPort hubRepository;

    public List<HubResponse> execute() {
        log.info("[list-hubs] Listing all hubs");

        return hubRepository.findAll().stream()
                .map(HubMapper.INSTANCE::toResponse)
                .toList();
    }
}
