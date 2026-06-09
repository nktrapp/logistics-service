package br.furb.logistics.infrastructure.config;

import br.furb.logistics.domain.event.HubConnectionsCreatedEvent;
import br.furb.logistics.domain.event.HubCreatedEvent;
import br.furb.logistics.domain.event.RouteCalculatedEvent;
import br.furb.logistics.domain.event.RouteFailedEvent;
import br.furb.logistics.domain.event.RouteRecalculatedEvent;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.infrastructure.adapter.out.integration.viacep.ViaCepResponse;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(NativeHintsConfig.JacksonBindingHints.class)
public class NativeHintsConfig {

    static class JacksonBindingHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            new BindingReflectionHintsRegistrar().registerReflectionHints(
                    hints.reflection(),
                    ViaCepResponse.class,
                    CepInfo.class,
                    RouteCalculatedEvent.Payload.class,
                    RouteRecalculatedEvent.Payload.class,
                    RouteFailedEvent.Payload.class,
                    HubCreatedEvent.Payload.class,
                    HubConnectionsCreatedEvent.Payload.class);
        }
    }
}
