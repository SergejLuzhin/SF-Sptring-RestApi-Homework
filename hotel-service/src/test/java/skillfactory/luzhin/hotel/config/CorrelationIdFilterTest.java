package skillfactory.luzhin.hotel.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет наполнение и очистку MDC для входящих HTTP-запросов Hotel Service.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /**
     * Проверяет передачу сквозных заголовков в MDC и очистку контекста потока.
     *
     * @throws Exception ошибка выполнения фильтра
     */
    @Test
    void shouldPopulateAndClearMdc() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "client-request-1");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, requestId.toString());
        request.addHeader(CorrelationIdFilter.BOOKING_ID_HEADER, "25");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> context = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                context.set(MDC.getCopyOfContextMap()));

        assertThat(context.get())
                .containsEntry(MdcScope.CORRELATION_ID, "client-request-1")
                .containsEntry(MdcScope.REQUEST_ID, requestId.toString())
                .containsEntry(MdcScope.BOOKING_ID, "25");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("client-request-1");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    /**
     * Проверяет создание безопасного UUID при некорректном входном correlation ID.
     *
     * @throws Exception ошибка выполнения фильтра
     */
    @Test
    void shouldGenerateSafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "invalid correlation id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))).isNotNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
