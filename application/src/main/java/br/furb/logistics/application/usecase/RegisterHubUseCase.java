package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.dto.RegisterHubCommand;
import br.furb.logistics.application.mapper.HubMapper;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RegisterHubUseCase {

    private final HubRepositoryPort hubRepository;
    private final CepLookupPort cepLookupPort;

    public HubResponse execute(RegisterHubCommand command) {
        log.info("[register-hub] Registering hub '{}' with CEP {}", command.name(), command.cep());

        CepInfo cepInfo = cepLookupPort.findByCep(command.cep())
                .orElseThrow(() -> new CepValidationException(command.cep()));

        Hub hub = Hub.builder()
                .name(command.name())
                .cep(cepInfo.getCep())
                .city(cepInfo.getCity())
                .state(cepInfo.getState())
                .active(true)
                .build();

        Hub saved = hubRepository.save(hub);

        log.info("[register-hub] Hub '{}' registered with id {}", saved.getName(), saved.getId());

        return HubMapper.INSTANCE.toResponse(saved);
    }
}
