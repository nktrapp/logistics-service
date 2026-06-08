package br.furb.logistics.application.adapter.in.web;

import br.furb.logistics.application.usecase.GetRouteUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(RouteRestAdapter.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "MONGODB_URI=mongodb://localhost:27017/logistics_test",
        "REDIS_HOST=localhost",
        "APP_ENVIRONMENT=prod",
        "APP_VERSION=test-version"
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Logistics structured logging profile")
class LogisticsStructuredLoggingProfileTest {

    private static final Logger log = LoggerFactory.getLogger(LogisticsStructuredLoggingProfileTest.class);

    @MockitoBean
    GetRouteUseCase getRouteUseCase;

    @Test
    @DisplayName("Prod logs expose trace and span ids as top-level JSON fields")
    void shouldExposeTraceAndSpanIdsInProdStructuredLogs(CapturedOutput output) {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        try {
            log.info("structured-log-correlation-smoke");
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }

        assertThat(output.getOut())
                .contains("\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"")
                .contains("\"spanId\":\"00f067aa0ba902b7\"")
                .contains("\"serviceName\":\"logistics-service\"")
                .contains("\"environment\":\"prod\"")
                .contains("\"version\":\"test-version\"");
    }
}
