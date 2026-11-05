package skillfactory.luzhin.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Принимает или создаёт сквозной correlation ID и передаёт его downstream-сервису.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /**
     * Добавляет correlation ID в исходящий запрос и ответ Gateway.
     *
     * @param exchange текущий HTTP-обмен
     * @param chain цепочка обработки Gateway
     * @return асинхронное завершение обработки запроса
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = normalize(exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        log.debug(
                "Gateway request; correlationId={}, method={}, path={}",
                correlationId,
                request.getMethod(),
                request.getPath().value()
        );
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * Сохраняет безопасный входной идентификатор либо создаёт новый UUID.
     *
     * @param correlationId входной correlation ID
     * @return идентификатор, пригодный для заголовков и логов
     */
    private String normalize(String correlationId) {
        if (correlationId != null && SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }
}
