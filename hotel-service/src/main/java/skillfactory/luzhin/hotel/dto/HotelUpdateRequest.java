package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Данные для частичного обновления отеля.
 *
 * @param name новое название отеля
 * @param address новый адрес отеля
 */
@Schema(description = "Частичное обновление отеля; изменяются только переданные поля")
public record HotelUpdateRequest(
        @Schema(description = "Новое название отеля", example = "Grand Hotel")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String name,
        @Schema(description = "Новый адрес отеля", example = "Moscow, Tverskaya 1")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String address
) {
}
