package skillfactory.luzhin.hotel.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Данные для частичного обновления отеля.
 *
 * @param name новое название отеля
 * @param address новый адрес отеля
 */
public record HotelUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String name,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String address
) {
}
