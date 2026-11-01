package skillfactory.luzhin.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Запрос на создание бронирования.
 *
 * @param requestId уникальный идентификатор запроса
 * @param roomId идентификатор выбранного номера
 * @param startDate дата заезда
 * @param endDate дата выезда
 * @param autoSelect признак автоматического подбора номера
 */
public record BookingCreateRequest(
        @NotNull UUID requestId,
        @Positive Long roomId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Boolean autoSelect
) {
}
