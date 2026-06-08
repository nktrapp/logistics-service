package br.furb.logistics.infrastructure.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TraceContextSupport {

    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    private static final String SQS_MESSAGE_ATTRIBUTE_HEADER_PREFIX = "Sqs_MA_";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceCarrier captureCurrent() {
        TraceContext currentContext = tracer.currentTraceContext().context();
        if (currentContext == null) {
            return TraceCarrier.empty();
        }

        Map<String, String> carrier = new LinkedHashMap<>();
        propagator.inject(currentContext, carrier, Map::put);
        return TraceCarrier.from(carrier);
    }

    public Map<String, String> currentTraceHeaders() {
        TraceContext currentContext = tracer.currentTraceContext().context();
        if (currentContext == null) {
            return Map.of();
        }

        Map<String, String> carrier = new LinkedHashMap<>();
        propagator.inject(currentContext, carrier, Map::put);
        return carrier;
    }

    public ScopedSpan startSpan(String name) {
        return startSpan(name, TraceCarrier.empty(), null);
    }

    public ScopedSpan startSpan(String name, Span.Kind kind) {
        return startSpan(name, TraceCarrier.empty(), kind);
    }

    public ScopedSpan startSpan(String name, TraceCarrier carrier, Span.Kind kind) {
        Span.Builder builder = carrier.hasTraceparent()
                ? propagator.extract(carrier, TraceCarrier::get)
                : spanBuilderWithCurrentParent();

        builder.name(name);
        if (kind != null) {
            builder.kind(kind);
        }

        Span span = builder.start();
        Tracer.SpanInScope scope = tracer.withSpan(span);
        return new ScopedSpan(span, scope);
    }

    public ScopedSpan startConsumerSpan(String name, Message<?> message) {
        return startSpan(name, TraceCarrier.fromHeaders(message.getHeaders()), Span.Kind.CONSUMER);
    }

    private Span.Builder spanBuilderWithCurrentParent() {
        Span.Builder builder = tracer.spanBuilder();
        TraceContext currentContext = tracer.currentTraceContext().context();
        if (currentContext != null) {
            builder.setParent(currentContext);
        }
        return builder;
    }

    public record TraceCarrier(String traceparent, String tracestate) {

        public static TraceCarrier empty() {
            return new TraceCarrier(null, null);
        }

        public static TraceCarrier from(Map<String, String> carrier) {
            return new TraceCarrier(carrier.get(TRACEPARENT), carrier.get(TRACESTATE));
        }

        public static TraceCarrier fromHeaders(Map<String, ?> headers) {
            return new TraceCarrier(headerValue(headers, TRACEPARENT), headerValue(headers, TRACESTATE));
        }

        public boolean hasTraceparent() {
            return traceparent != null && !traceparent.isBlank();
        }

        public String get(String key) {
            if (TRACEPARENT.equalsIgnoreCase(key)) {
                return traceparent;
            }
            if (TRACESTATE.equalsIgnoreCase(key)) {
                return tracestate;
            }
            return null;
        }

        private static String headerValue(Map<String, ?> headers, String key) {
            Object value = findHeader(headers, key);
            if (value == null) {
                value = findHeader(headers, SQS_MESSAGE_ATTRIBUTE_HEADER_PREFIX + key);
            }
            if (value instanceof MessageAttributeValue attributeValue) {
                return attributeValue.stringValue();
            }
            return value == null ? null : value.toString();
        }

        private static Object findHeader(Map<String, ?> headers, String key) {
            Object value = headers.get(key);
            if (value == null) {
                value = headers.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null) {
                value = headers.get(key.toUpperCase(Locale.ROOT));
            }
            if (value == null) {
                value = headers.entrySet().stream()
                        .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            return value;
        }
    }

    public record ScopedSpan(Span span, Tracer.SpanInScope scope) implements AutoCloseable {

        public void tag(String key, String value) {
            if (value != null && !value.isBlank()) {
                span.tag(key, value);
            }
        }

        public void error(Throwable throwable) {
            span.error(throwable);
        }

        @Override
        public void close() {
            try {
                scope.close();
            } finally {
                span.end();
            }
        }
    }
}
