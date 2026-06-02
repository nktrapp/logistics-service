package br.furb.logistics.core.usecase;

import br.furb.logistics.core.dto.HubResponse;
import br.furb.logistics.core.mapper.HubMapper;
import br.furb.logistics.domain.port.HubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ListHubsUseCase {

    private final HubRepository hubRepository;

    public List<HubResponse> execute() {
        log.info("[list-hubs] Listing all hubs");

        return hubRepository.findAll().stream()
                .map(HubMapper.INSTANCE::toResponse)
                .toList();
    }
}
