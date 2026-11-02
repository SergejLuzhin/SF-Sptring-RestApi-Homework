package skillfactory.luzhin.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет приём, создание и передачу сквозного correlation ID в Gateway.
 */
class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    /**
     * Проверяет сохранение входного correlation ID в downstream-запросе и ответе.
     */
    @Test
    void shouldPropagateIncomingCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/booking")
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "client-request-1"));
        AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();

        Mono<Void> result = filter.filter(exchange, downstreamExchange -> {
            downstreamCorrelationId.set(downstreamExchange.getRequest()
                    .getHeaders()
                    .getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        });

        result.block();
        assertThat(downstreamCorrelationId).hasValue("client-request-1");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER))
                .isEqualTo("client-request-1");
    }

    /**
     * Проверяет замену небезопасного входного значения новым UUID.
     */
    @Test
    void shouldGenerateSafeCorrelationIdWhenIncomingValueIsInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/booking")
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "invalid correlation id"));
        AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();

        Mono<Void> result = filter.filter(exchange, downstreamExchange -> {
            downstreamCorrelationId.set(downstreamExchange.getRequest()
                    .getHeaders()
                    .getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        });

        result.block();
        assertThat(UUID.fromString(downstreamCorrelationId.get())).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER))
                .isEqualTo(downstreamCorrelationId.get());
    }
}
