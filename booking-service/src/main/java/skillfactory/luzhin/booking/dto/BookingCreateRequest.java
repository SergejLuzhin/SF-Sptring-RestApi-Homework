package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Создание брони вручную или с автоподбором; startDate < endDate")
public record BookingCreateRequest(
        @Schema(description = "UUID идемпотентности запроса", example = "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID requestId,
        @Schema(description = "Выбранный номер; обязателен при autoSelect=false и игнорируется при autoSelect=true",
                example = "10", nullable = true)
        @Positive Long roomId,
        @Schema(description = "Дата заезда, включительно", example = "2026-11-10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate startDate,
        @Schema(description = "Дата выезда, исключительно", example = "2026-11-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate endDate,
        @Schema(description = "Автоматически выбрать первый рекомендованный номер", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean autoSelect
) {
}
