package br.furb.logistics.application.dto;

public record HubResponse(
        String id,
        String name,
        String cep,
        String city,
        String state,
        double latitude,
        double longitude,
        boolean active
) {}
