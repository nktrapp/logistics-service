package br.furb.logistics.infrastructure.adapter.out.integration.viacep;

import br.furb.logistics.domain.model.CepInfo;

public class ViaCepMapper {

    private ViaCepMapper() {}

    public static CepInfo toDomain(ViaCepResponse response) {
        return CepInfo.builder()
                .cep(response.getCep().replace("-", ""))
                .city(response.getLocalidade())
                .state(response.getUf())
                .neighborhood(response.getBairro())
                .ibgeCode(response.getIbge())
                .build();
    }
}
