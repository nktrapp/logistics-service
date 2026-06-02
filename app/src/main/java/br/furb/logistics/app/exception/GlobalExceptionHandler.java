package br.furb.logistics.app.exception;

import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.exception.RouteCalculationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HubNotFoundException.class)
    public ProblemDetail handleHubNotFound(HubNotFoundException ex) {
        log.warn("[exception-handler] {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Hub Not Found");
        problem.setType(URI.create("https://api.furb.br/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(CepValidationException.class)
    public ProblemDetail handleCepValidation(CepValidationException ex) {
        log.warn("[exception-handler] {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("CEP Validation Failed");
        problem.setType(URI.create("https://api.furb.br/errors/cep-validation"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(RouteCalculationException.class)
    public ProblemDetail handleRouteCalculation(RouteCalculationException ex) {
        log.warn("[exception-handler] {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Route Calculation Failed");
        problem.setType(URI.create("https://api.furb.br/errors/route-calculation"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("[exception-handler] Validation failed: {}", details);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://api.furb.br/errors/validation"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("[exception-handler] Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.furb.br/errors/internal"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
