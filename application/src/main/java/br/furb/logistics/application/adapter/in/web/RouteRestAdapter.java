package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.application.usecase.GetRouteUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.util.StringUtils.hasText;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteRestAdapter {

    private final GetRouteUseCase getRouteUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponse> getById(@PathVariable String id) {
        return getRouteUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<RouteResponse> getByPackageId(@RequestParam(required = false) String packageId) {
        if (!hasText(packageId)) {
            return ResponseEntity.badRequest().build();
        }
        return getRouteUseCase.findByPackageId(packageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
