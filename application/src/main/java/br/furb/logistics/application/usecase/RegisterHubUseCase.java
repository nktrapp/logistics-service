package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.dto.RegisterHubCommand;
import br.furb.logistics.application.mapper.HubMapper;
import br.furb.logistics.application.usecase.transaction.PersistRegisteredHubUseCase;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class RegisterHubUseCase {

    private final CepLookupPort cepLookupPort;
    private final MunicipalityGeocodingPort municipalityGeocodingPort;
    private final PersistRegisteredHubUseCase persistRegisteredHubUseCase;

    public HubResponse execute(RegisterHubCommand command) {
        log.info("[register-hub] Registering hub '{}' with CEP {}", command.name(), command.cep());

        CepInfo cepInfo = cepLookupPort.findByCep(command.cep())
                .orElseThrow(() -> new CepValidationException(command.cep()));

        Hub.HubBuilder hubBuilder = Hub.builder()
                .name(command.name())
                .cep(cepInfo.getCep())
                .city(cepInfo.getCity())
                .state(cepInfo.getState())
                .active(true);

        Optional<Coordinates> coordinates = resolveCoordinates(cepInfo);
        if (coordinates.isPresent()) {
            hubBuilder.latitude(coordinates.get().latitude())
                    .longitude(coordinates.get().longitude());
        } else {
            log.warn("[register-hub] Coordinates not resolved for {}/{} (ibge {}); hub will be created without "
                    + "automatic mesh connections", cepInfo.getCity(), cepInfo.getState(), cepInfo.getIbgeCode());
        }

        Hub saved = persistRegisteredHubUseCase.execute(hubBuilder.build());

        log.info("[register-hub] Hub '{}' registered with id {}", saved.getName(), saved.getId());

        return HubMapper.INSTANCE.toResponse(saved);
    }

    private Optional<Coordinates> resolveCoordinates(CepInfo cepInfo) {
        Optional<Coordinates> byIbgeCode = municipalityGeocodingPort.findByIbgeCode(cepInfo.getIbgeCode());
        if (byIbgeCode.isPresent()) {
            return byIbgeCode;
        }
        return municipalityGeocodingPort.findByCityState(cepInfo.getCity(), cepInfo.getState());
    }
}
