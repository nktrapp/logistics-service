package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.application.usecase.GetRouteUseCase;
import br.furb.logistics.domain.model.RouteStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteRestAdapter.class)
@DisplayName("RouteRestAdapter")
class RouteRestAdapterTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetRouteUseCase getRouteUseCase;

    @Test
    @DisplayName("GET /api/v1/routes/{id} returns 200 when the route exists")
    void shouldGetRouteById() throws Exception {
        when(getRouteUseCase.findById("route-1")).thenReturn(Optional.of(sampleRoute()));

        mockMvc.perform(get("/api/v1/routes/{id}", "route-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("route-1"))
                .andExpect(jsonPath("$.packageId").value("pkg-1"));
    }

    @Test
    @DisplayName("GET /api/v1/routes/{id} returns 404 when the route does not exist")
    void shouldReturnNotFoundById() throws Exception {
        when(getRouteUseCase.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/routes/{id}", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/routes?packageId=... returns 200 when a route exists for the package")
    void shouldGetRouteByPackageId() throws Exception {
        when(getRouteUseCase.findByPackageId("pkg-1")).thenReturn(Optional.of(sampleRoute()));

        mockMvc.perform(get("/api/v1/routes").param("packageId", "pkg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageId").value("pkg-1"));
    }

    @Test
    @DisplayName("GET /api/v1/routes without packageId returns 400")
    void shouldReturnBadRequestWhenPackageIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/routes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/routes?packageId=... returns 404 when no route exists for the package")
    void shouldReturnNotFoundByPackageId() throws Exception {
        when(getRouteUseCase.findByPackageId("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/routes").param("packageId", "missing"))
                .andExpect(status().isNotFound());
    }

    private RouteResponse sampleRoute() {
        return new RouteResponse(
                "route-1",
                "pkg-1",
                "origin-1",
                "dest-1",
                List.of(),
                50.0,
                6,
                RouteStatus.CALCULATED,
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }
}
