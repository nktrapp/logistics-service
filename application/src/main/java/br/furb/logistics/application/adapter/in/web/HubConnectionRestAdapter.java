package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.application.usecase.RegisterHubConnectionUseCase;
import br.furb.logistics.domain.model.HubConnection;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hubs/connections")
@RequiredArgsConstructor
public class HubConnectionRestAdapter {

    private final RegisterHubConnectionUseCase registerHubConnectionUseCase;

    @PostMapping
    public ResponseEntity<HubConnection> register(@Valid @RequestBody RegisterConnectionCommand command) {
        HubConnection connection = registerHubConnectionUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(connection);
    }
}
