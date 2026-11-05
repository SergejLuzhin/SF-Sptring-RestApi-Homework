package skillfactory.luzhin.booking.dto;

import java.util.UUID;

/**
 * Запрос Booking Service на освобождение блокировки в Hotel Service.
 *
 * @param bookingId идентификатор бронирования
 * @param requestId идентификатор идемпотентности
 * @param reason причина освобождения
 */
public record ReleaseReservationRequest(
        Long bookingId,
        UUID requestId,
        ReleaseReason reason
) {
}
