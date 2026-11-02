package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Личное бронирование пользователя")
public record BookingResponse(
        @Schema(description = "Идентификатор бронирования", example = "25") Long id,
        @Schema(description = "Идентификатор владельца", example = "2") Long userId,
        @Schema(description = "Идентификатор номера", example = "10") Long roomId,
        @Schema(description = "Дата заезда, включительно", example = "2026-11-10") LocalDate startDate,
        @Schema(description = "Дата выезда, исключительно", example = "2026-11-15") LocalDate endDate,
        @Schema(description = "Состояние бронирования", example = "CONFIRMED",
                allowableValues = {"PENDING", "CONFIRMED", "CANCELLED"}) BookingStatus status,
        @Schema(description = "Время создания в UTC", example = "2026-10-31T12:00:00Z") Instant createdAt
) {
}
