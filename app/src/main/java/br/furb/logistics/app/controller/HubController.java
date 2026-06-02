package br.furb.logistics.app.controller;

import br.furb.logistics.core.dto.HubResponse;
import br.furb.logistics.core.dto.RegisterHubCommand;
import br.furb.logistics.core.usecase.GetHubUseCase;
import br.furb.logistics.core.usecase.ListHubsUseCase;
import br.furb.logistics.core.usecase.RegisterHubUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class HubController {

    private final RegisterHubUseCase registerHubUseCase;
    private final ListHubsUseCase listHubsUseCase;
    private final GetHubUseCase getHubUseCase;

    @PostMapping
    public ResponseEntity<HubResponse> register(@Valid @RequestBody RegisterHubCommand command) {
        HubResponse response = registerHubUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<HubResponse>> list() {
        List<HubResponse> response = listHubsUseCase.execute();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HubResponse> getById(@PathVariable String id) {
        HubResponse response = getHubUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}
