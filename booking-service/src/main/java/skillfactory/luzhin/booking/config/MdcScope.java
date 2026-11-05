package skillfactory.luzhin.booking.config;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Временно добавляет идентификаторы бронирования в MDC и восстанавливает прежний контекст.
 */
public final class MdcScope implements AutoCloseable {

    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String BOOKING_ID = "bookingId";

    private final Map<String, String> previousValues = new HashMap<>();

    /**
     * Создаёт контекст с доступными идентификаторами операции.
     *
     * @param requestId идентификатор идемпотентности
     * @param bookingId идентификатор бронирования
     */
    private MdcScope(UUID requestId, Long bookingId) {
        put(REQUEST_ID, requestId == null ? null : requestId.toString());
        put(BOOKING_ID, bookingId == null ? null : bookingId.toString());
    }

    /**
     * Открывает MDC-контекст для использования в try-with-resources.
     *
     * @param requestId идентификатор идемпотентности
     * @param bookingId идентификатор бронирования
     * @return контекст, который необходимо закрыть
     */
    public static MdcScope open(UUID requestId, Long bookingId) {
        return new MdcScope(requestId, bookingId);
    }

    /**
     * Восстанавливает значения MDC, существовавшие до открытия контекста.
     */
    @Override
    public void close() {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    /**
     * Добавляет одно значение и запоминает предыдущее состояние ключа.
     *
     * @param key имя MDC-ключа
     * @param value новое значение
     */
    private void put(String key, String value) {
        previousValues.put(key, MDC.get(key));
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
