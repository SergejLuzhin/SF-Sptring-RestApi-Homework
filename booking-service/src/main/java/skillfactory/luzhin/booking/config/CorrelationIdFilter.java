package skillfactory.luzhin.booking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Принимает сквозные идентификаторы запроса и очищает MDC после его обработки.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String BOOKING_ID_HEADER = "X-Booking-ID";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /**
     * Помещает идентификаторы HTTP-запроса в MDC и возвращает correlation ID клиенту.
     *
     * @param request входной HTTP-запрос
     * @param response HTTP-ответ
     * @param filterChain следующая цепочка фильтров
     * @throws ServletException ошибка сервлета
     * @throws IOException ошибка ввода-вывода
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = normalizeCorrelationId(request.getHeader(CORRELATION_ID_HEADER));
        MDC.put(MdcScope.CORRELATION_ID, correlationId);
        putIfValidUuid(request.getHeader(REQUEST_ID_HEADER), MdcScope.REQUEST_ID);
        putIfValidLong(request.getHeader(BOOKING_ID_HEADER), MdcScope.BOOKING_ID);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcScope.CORRELATION_ID);
            MDC.remove(MdcScope.REQUEST_ID);
            MDC.remove(MdcScope.BOOKING_ID);
        }
    }

    /**
     * Сохраняет безопасный входной correlation ID либо создаёт новый UUID.
     *
     * @param correlationId входной идентификатор
     * @return идентификатор, пригодный для заголовков и логов
     */
    private String normalizeCorrelationId(String correlationId) {
        if (correlationId != null && SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Добавляет в MDC только корректный UUID из заголовка.
     *
     * @param value значение заголовка
     * @param key имя MDC-ключа
     */
    private void putIfValidUuid(String value, String key) {
        try {
            if (value != null) {
                MDC.put(key, UUID.fromString(value).toString());
            }
        } catch (IllegalArgumentException ignored) {
            MDC.remove(key);
        }
    }

    /**
     * Добавляет в MDC только положительный числовой идентификатор брони.
     *
     * @param value значение заголовка
     * @param key имя MDC-ключа
     */
    private void putIfValidLong(String value, String key) {
        try {
            if (value != null && Long.parseLong(value) > 0) {
                MDC.put(key, value);
            }
        } catch (NumberFormatException ignored) {
            MDC.remove(key);
        }
    }
}
