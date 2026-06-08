package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.usecase.RegisterHubConnectionUseCase;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.model.HubConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HubConnectionRestAdapter.class)
@DisplayName("HubConnectionRestAdapter")
class HubConnectionRestAdapterTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegisterHubConnectionUseCase registerHubConnectionUseCase;

    @Test
    @DisplayName("POST /api/v1/hubs/connections with a valid command returns 201 and the created connection")
    void shouldRegisterConnection() throws Exception {
        when(registerHubConnectionUseCase.execute(any())).thenReturn(sampleConnection());

        mockMvc.perform(post("/api/v1/hubs/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHubId\":\"origin-1\",\"destinationHubId\":\"dest-1\",\"distanceKm\":50.0,\"transitTimeHours\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originHubId").value("origin-1"))
                .andExpect(jsonPath("$.destinationHubId").value("dest-1"));
    }

    @Test
    @DisplayName("POST /api/v1/hubs/connections with an invalid payload returns 400")
    void shouldRejectInvalidConnectionPayload() throws Exception {
        mockMvc.perform(post("/api/v1/hubs/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHubId\":\"\",\"destinationHubId\":\"dest-1\",\"distanceKm\":-5,\"transitTimeHours\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/hubs/connections referencing a missing hub returns 404")
    void shouldReturnNotFoundWhenHubMissing() throws Exception {
        when(registerHubConnectionUseCase.execute(any())).thenThrow(new HubNotFoundException("origin-1"));

        mockMvc.perform(post("/api/v1/hubs/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originHubId\":\"origin-1\",\"destinationHubId\":\"dest-1\",\"distanceKm\":50.0,\"transitTimeHours\":6}"))
                .andExpect(status().isNotFound());
    }

    private HubConnection sampleConnection() {
        return HubConnection.builder()
                .id("conn-1")
                .originHubId("origin-1")
                .destinationHubId("dest-1")
                .distanceKm(new BigDecimal("50.0"))
                .transitTimeHours(6)
                .build();
    }
}
