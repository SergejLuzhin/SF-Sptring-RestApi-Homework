package skillfactory.luzhin.booking.service;

import skillfactory.luzhin.booking.entity.BookingStatus;

import java.util.UUID;

/**
 * Данные бронирования, необходимые для отмены и удалённого release.
 *
 * @param id идентификатор бронирования
 * @param roomId идентификатор номера
 * @param requestId идентификатор идемпотентности
 * @param status текущее состояние бронирования
 */
public record BookingCancellation(
        Long id,
        Long roomId,
        UUID requestId,
        BookingStatus status
) {
}
