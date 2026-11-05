package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;

/**
 * Результат подтверждения или освобождения занятости номера.
 *
 * @param bookingId идентификатор бронирования
 * @param roomId идентификатор номера
 * @param status состояние записи о занятости
 */
@Schema(description = "INTERNAL: результат подтверждения или освобождения занятости")
public record RoomReservationResponse(
        @Schema(description = "Идентификатор бронирования", example = "25") Long bookingId,
        @Schema(description = "Идентификатор номера", example = "10") Long roomId,
        @Schema(description = "Состояние записи о занятости", example = "HELD", allowableValues = {"HELD", "RELEASED"})
        RoomReservationStatus status
) {
}
