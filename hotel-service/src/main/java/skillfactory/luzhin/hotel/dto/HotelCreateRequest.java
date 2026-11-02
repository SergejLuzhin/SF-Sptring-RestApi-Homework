package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Данные для создания отеля.
 *
 * @param name название отеля
 * @param address адрес отеля
 */
@Schema(description = "Данные для создания отеля")
public record HotelCreateRequest(
        @Schema(description = "Название отеля", example = "Grand Hotel", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String name,
        @Schema(description = "Адрес отеля", example = "Moscow, Tverskaya 1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String address
) {
}
