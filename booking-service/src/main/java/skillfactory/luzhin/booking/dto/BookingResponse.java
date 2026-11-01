package skillfactory.luzhin.booking.dto;

import skillfactory.luzhin.booking.entity.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Публичное представление бронирования.
 *
 * @param id идентификатор бронирования
 * @param userId идентификатор владельца
 * @param roomId идентификатор номера
 * @param startDate дата заезда
 * @param endDate дата выезда
 * @param status состояние бронирования
 * @param createdAt время создания
 */
public record BookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        Instant createdAt
) {
}
