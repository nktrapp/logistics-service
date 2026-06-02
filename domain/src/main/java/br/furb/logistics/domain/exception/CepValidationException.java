package br.furb.logistics.domain.exception;

public class CepValidationException extends RuntimeException {

    public CepValidationException(String cep) {
        super("Invalid or not found CEP: " + cep);
    }
}
