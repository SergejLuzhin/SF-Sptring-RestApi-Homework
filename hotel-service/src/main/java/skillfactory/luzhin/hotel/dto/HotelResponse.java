package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Представление отеля в ответах API.
 *
 * @param id идентификатор отеля
 * @param name название отеля
 * @param address адрес отеля
 */
@Schema(description = "Отель в ответах API")
public record HotelResponse(
        @Schema(description = "Идентификатор отеля", example = "1") Long id,
        @Schema(description = "Название отеля", example = "Grand Hotel") String name,
        @Schema(description = "Адрес отеля", example = "Moscow, Tverskaya 1") String address
) {
}
