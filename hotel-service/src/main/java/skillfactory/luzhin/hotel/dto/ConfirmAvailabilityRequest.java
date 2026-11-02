package skillfactory.luzhin.hotel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Запрос на подтверждение доступности номера для бронирования.
 *
 * @param bookingId идентификатор бронирования
 * @param requestId идентификатор идемпотентности
 * @param startDate дата заезда
 * @param endDate дата выезда
 */
public record ConfirmAvailabilityRequest(
        @NotNull @Positive Long bookingId,
        @NotNull UUID requestId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
