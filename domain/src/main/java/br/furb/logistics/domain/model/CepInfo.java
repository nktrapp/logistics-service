package br.furb.logistics.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CepInfo {

    private final String cep;
    private final String city;
    private final String state;
    private final String neighborhood;
}
