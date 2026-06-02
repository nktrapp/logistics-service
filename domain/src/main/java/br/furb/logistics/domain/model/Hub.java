package br.furb.logistics.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Hub {

    private final String id;

    @NotBlank
    @Size(min = 3, max = 100)
    private final String name;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private final String cep;

    @NotBlank
    private final String city;

    @NotBlank
    @Size(min = 2, max = 2)
    private final String state;

    private final double latitude;

    private final double longitude;

    @Builder.Default
    private final boolean active = true;
}
