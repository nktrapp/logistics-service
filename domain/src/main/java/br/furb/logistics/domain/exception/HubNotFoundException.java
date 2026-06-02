package br.furb.logistics.domain.exception;

public class HubNotFoundException extends RuntimeException {

    public HubNotFoundException(String hubId) {
        super("Hub not found for id: " + hubId);
    }
}
