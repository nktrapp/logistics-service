package br.furb.logistics.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.beans.ConstructorProperties;

@Getter
public class CepInfo {

    private final String cep;
    private final String city;
    private final String state;
    private final String neighborhood;

    @Builder
    @ConstructorProperties({"cep", "city", "state", "neighborhood"})
    public CepInfo(String cep, String city, String state, String neighborhood) {
        this.cep = cep;
        this.city = city;
        this.state = state;
        this.neighborhood = neighborhood;
    }
}
