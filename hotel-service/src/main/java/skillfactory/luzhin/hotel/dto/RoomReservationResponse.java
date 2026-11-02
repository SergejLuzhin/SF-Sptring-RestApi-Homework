package skillfactory.luzhin.hotel.dto;

import skillfactory.luzhin.hotel.entity.RoomReservationStatus;

/**
 * Результат подтверждения или освобождения занятости номера.
 *
 * @param bookingId идентификатор бронирования
 * @param roomId идентификатор номера
 * @param status состояние записи о занятости
 */
public record RoomReservationResponse(
        Long bookingId,
        Long roomId,
        RoomReservationStatus status
) {
}
