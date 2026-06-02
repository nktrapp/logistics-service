package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.mapper.HubMapper;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GetHubUseCase {

    private final HubRepositoryPort hubRepository;

    public HubResponse execute(String hubId) {
        log.info("[get-hub] Fetching hub {}", hubId);

        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new HubNotFoundException(hubId));

        return HubMapper.INSTANCE.toResponse(hub);
    }
}
