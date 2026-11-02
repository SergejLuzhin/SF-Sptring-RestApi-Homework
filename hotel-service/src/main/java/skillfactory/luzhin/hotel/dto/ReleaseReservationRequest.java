package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Запрос на освобождение ранее заблокированного номера.
 *
 * @param bookingId идентификатор бронирования
 * @param requestId идентификатор идемпотентности
 * @param reason причина освобождения номера
 */
@Schema(description = "INTERNAL: идемпотентное освобождение номера")
public record ReleaseReservationRequest(
        @Schema(description = "Идентификатор бронирования", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long bookingId,
        @Schema(description = "UUID идемпотентности операции", example = "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID requestId,
        @Schema(description = "Причина освобождения", example = "COMPENSATION",
                allowableValues = {"COMPENSATION", "USER_CANCELLATION"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ReleaseReason reason
) {
}
