package skillfactory.luzhin.booking.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Запрос Booking Service на создание блокировки в Hotel Service.
 *
 * @param bookingId идентификатор бронирования
 * @param requestId идентификатор идемпотентности
 * @param startDate дата заезда
 * @param endDate дата выезда
 */
public record ConfirmAvailabilityRequest(
        Long bookingId,
        UUID requestId,
        LocalDate startDate,
        LocalDate endDate
) {
}
