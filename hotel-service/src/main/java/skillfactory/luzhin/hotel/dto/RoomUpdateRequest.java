package skillfactory.luzhin.hotel.dto;

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
public record RoomUpdateRequest(
        @Positive Long hotelId,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 50) String number,
        Boolean available
) {
}
