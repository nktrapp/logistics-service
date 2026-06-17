package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.dto.RegisterHubCommand;
import br.furb.logistics.application.usecase.GetHubUseCase;
import br.furb.logistics.application.usecase.ListHubsUseCase;
import br.furb.logistics.application.usecase.RegisterHubUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hubs")
@RequiredArgsConstructor
@Tag(name = "Hubs", description = "Cadastro e consulta de hubs logísticos")
public class HubRestAdapter {

    private final RegisterHubUseCase registerHubUseCase;
    private final ListHubsUseCase listHubsUseCase;
    private final GetHubUseCase getHubUseCase;

    @Operation(summary = "Cadastra um hub",
            description = "Registra um hub a partir do nome e CEP; a cidade, estado e coordenadas são "
                    + "resolvidos pelo CEP. O hub pode ser auto-conectado aos vizinhos mais próximos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Hub cadastrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<HubResponse> register(@Valid @RequestBody RegisterHubCommand command) {
        HubResponse response = registerHubUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Lista hubs")
    @ApiResponse(responseCode = "200", description = "Lista de hubs")
    @GetMapping
    public ResponseEntity<List<HubResponse>> list() {
        List<HubResponse> response = listHubsUseCase.execute();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Busca um hub por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hub encontrado"),
            @ApiResponse(responseCode = "404", description = "Hub não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<HubResponse> getById(
            @Parameter(description = "Identificador do hub", example = "665f1b2c9a3e4d0012ab34cd")
            @PathVariable String id) {
        HubResponse response = getHubUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}
