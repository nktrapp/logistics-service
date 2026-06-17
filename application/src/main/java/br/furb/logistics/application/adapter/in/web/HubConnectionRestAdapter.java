package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.application.usecase.RegisterHubConnectionUseCase;
import br.furb.logistics.domain.model.HubConnection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hubs/connections")
@RequiredArgsConstructor
@Tag(name = "Hub Connections", description = "Cadastro das conexões (arestas) entre hubs do grafo de rotas")
public class HubConnectionRestAdapter {

    private final RegisterHubConnectionUseCase registerHubConnectionUseCase;

    @Operation(summary = "Cadastra uma conexão entre hubs",
            description = "Cria uma aresta entre dois hubs com distância (km) e tempo de trânsito (horas), "
                    + "usada pelo cálculo de rotas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conexão cadastrada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<HubConnection> register(@Valid @RequestBody RegisterConnectionCommand command) {
        HubConnection connection = registerHubConnectionUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(connection);
    }
}
