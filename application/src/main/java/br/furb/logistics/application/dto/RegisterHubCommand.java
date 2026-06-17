package br.furb.logistics.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastro de um hub")
public record RegisterHubCommand(
        @Schema(description = "Nome do hub (3 a 100 caracteres)", example = "Hub Blumenau")
        @NotBlank @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,

        @Schema(description = "CEP do hub (8 dígitos, sem hífen)", example = "89010000")
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP inválido")
        String cep
) {}
