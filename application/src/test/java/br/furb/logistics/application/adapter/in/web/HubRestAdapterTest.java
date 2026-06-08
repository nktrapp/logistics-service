package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.usecase.GetHubUseCase;
import br.furb.logistics.application.usecase.ListHubsUseCase;
import br.furb.logistics.application.usecase.RegisterHubUseCase;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.exception.HubNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HubRestAdapter.class)
@DisplayName("HubRestAdapter")
class HubRestAdapterTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegisterHubUseCase registerHubUseCase;
    @MockitoBean
    ListHubsUseCase listHubsUseCase;
    @MockitoBean
    GetHubUseCase getHubUseCase;

    @Test
    @DisplayName("POST /api/v1/hubs with a valid command returns 201 and the created hub")
    void shouldRegisterHub() throws Exception {
        when(registerHubUseCase.execute(any())).thenReturn(sampleHub());

        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hub Centro\",\"cep\":\"89010000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("hub-1"))
                .andExpect(jsonPath("$.city").value("Blumenau"));
    }

    @Test
    @DisplayName("POST /api/v1/hubs with an invalid payload returns 400")
    void shouldRejectInvalidHubPayload() throws Exception {
        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ab\",\"cep\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/hubs with an unresolvable CEP returns 400")
    void shouldReturnBadRequestWhenCepValidationFails() throws Exception {
        when(registerHubUseCase.execute(any())).thenThrow(new CepValidationException("00000000"));

        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hub Centro\",\"cep\":\"00000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/hubs returns 200 and the list of hubs")
    void shouldListHubs() throws Exception {
        when(listHubsUseCase.execute()).thenReturn(List.of(sampleHub()));

        mockMvc.perform(get("/api/v1/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hub-1"));
    }

    @Test
    @DisplayName("GET /api/v1/hubs/{id} returns 200 when the hub exists")
    void shouldGetHubById() throws Exception {
        when(getHubUseCase.execute("hub-1")).thenReturn(sampleHub());

        mockMvc.perform(get("/api/v1/hubs/{id}", "hub-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hub-1"));
    }

    @Test
    @DisplayName("GET /api/v1/hubs/{id} returns 404 when the hub does not exist")
    void shouldReturnNotFoundWhenHubMissing() throws Exception {
        when(getHubUseCase.execute("missing")).thenThrow(new HubNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/hubs/{id}", "missing"))
                .andExpect(status().isNotFound());
    }

    private HubResponse sampleHub() {
        return new HubResponse("hub-1", "Hub Centro", "89010000", "Blumenau", "SC", 0.0, 0.0, true);
    }
}
