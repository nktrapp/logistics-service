package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.application.mapper.HubConnectionMapper;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RegisterHubConnectionUseCase {

    private final HubConnectionRepositoryPort hubConnectionRepository;
    private final HubRepositoryPort hubRepository;

    public HubConnection execute(RegisterConnectionCommand command) {
        log.info("[register-connection] Connecting hub {} to hub {}", command.originHubId(), command.destinationHubId());

        hubRepository.findById(command.originHubId())
                .orElseThrow(() -> new HubNotFoundException(command.originHubId()));

        hubRepository.findById(command.destinationHubId())
                .orElseThrow(() -> new HubNotFoundException(command.destinationHubId()));

        HubConnection connection = HubConnectionMapper.INSTANCE.toDomain(command);

        HubConnection saved = hubConnectionRepository.save(connection);

        log.info("[register-connection] Connection created with id {}", saved.getId());

        return saved;
    }
}
