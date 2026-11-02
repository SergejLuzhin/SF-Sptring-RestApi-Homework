package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Данные для частичного обновления номера.
 *
 * @param hotelId идентификатор нового отеля
 * @param number новый номер комнаты
 * @param available новая эксплуатационная доступность
 */
@Schema(description = "Частичное обновление номера; изменяются только переданные поля")
public record RoomUpdateRequest(
        @Schema(description = "Идентификатор нового отеля", example = "1") @Positive Long hotelId,
        @Schema(description = "Новый номер комнаты", example = "102")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 50) String number,
        @Schema(description = "Новая эксплуатационная доступность", example = "true") Boolean available
) {
}
