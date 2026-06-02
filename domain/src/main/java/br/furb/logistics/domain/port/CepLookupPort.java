package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.CepInfo;

import java.util.Optional;

public interface CepLookupPort {

    Optional<CepInfo> findByCep(String cep);
}
