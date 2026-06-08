package br.furb.logistics.application.adapter.in.messaging;

import br.furb.logistics.application.usecase.BuildHubConnectionsUseCase;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("HubEventListenerAdapter")
class HubEventListenerAdapterTest {

    @Mock
    BuildHubConnectionsUseCase buildHubConnectionsUseCase;

    private HubEventListenerAdapter buildAdapter() {
        return new HubEventListenerAdapter(buildHubConnectionsUseCase, new ObjectMapper());
    }

    @Test
    @DisplayName("Given a hub.created message, should route it to BuildHubConnectionsUseCase with the parsed fields")
    void shouldRouteHubCreated() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-1","eventType":"hub.created","payload":{"hubId":"hub-1"}}
                """;

        adapter.onMessage(message);

        verify(buildHubConnectionsUseCase).execute("event-1", "hub-1");
    }

    @Test
    @DisplayName("Given a hub.connections.created message, should ignore it without invoking the mesh use case")
    void shouldIgnoreConnectionsCreated() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventId":"event-2","eventType":"hub.connections.created","payload":{"hubId":"hub-1"}}
                """;

        adapter.onMessage(message);

        verifyNoInteractions(buildHubConnectionsUseCase);
    }

    @Test
    @DisplayName("Given a message missing a required field, should wrap the failure so the message is retried")
    void shouldThrowWhenRequiredFieldMissing() {
        HubEventListenerAdapter adapter = buildAdapter();
        String message = """
                {"eventType":"hub.created","payload":{"hubId":"hub-1"}}
                """;

        assertThatThrownBy(() -> adapter.onMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verifyNoInteractions(buildHubConnectionsUseCase);
    }
}
