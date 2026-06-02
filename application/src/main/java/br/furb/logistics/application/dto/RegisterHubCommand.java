package br.furb.logistics.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterHubCommand(
        @NotBlank @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP inválido")
        String cep
) {}
