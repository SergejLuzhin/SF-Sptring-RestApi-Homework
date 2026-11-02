package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "INTERNAL: подтверждение доступности номера; startDate < endDate")
public record ConfirmAvailabilityRequest(
        @Schema(description = "Идентификатор бронирования", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long bookingId,
        @Schema(description = "UUID идемпотентности операции", example = "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID requestId,
        @Schema(description = "Дата заезда, включительно", example = "2026-11-10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate startDate,
        @Schema(description = "Дата выезда, исключительно", example = "2026-11-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate endDate
) {
}
