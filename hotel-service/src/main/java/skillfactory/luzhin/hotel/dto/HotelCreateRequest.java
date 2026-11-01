package skillfactory.luzhin.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Данные для создания отеля.
 *
 * @param name название отеля
 * @param address адрес отеля
 */
public record HotelCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String address
) {
}
