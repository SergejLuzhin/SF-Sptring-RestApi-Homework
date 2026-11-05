package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Данные для создания номера.
 *
 * @param hotelId идентификатор отеля
 * @param number номер комнаты
 * @param available эксплуатационная доступность
 */
@Schema(description = "Данные для создания номера")
public record RoomCreateRequest(
        @Schema(description = "Идентификатор отеля", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long hotelId,
        @Schema(description = "Номер комнаты, уникальный в пределах отеля", example = "101",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 50) String number,
        @Schema(description = "Эксплуатационная доступность; по умолчанию true, не означает свободность на даты",
                example = "true", defaultValue = "true")
        Boolean available
) {
}
