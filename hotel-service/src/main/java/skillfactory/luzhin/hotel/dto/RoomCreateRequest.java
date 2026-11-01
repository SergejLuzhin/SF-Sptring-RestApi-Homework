package skillfactory.luzhin.hotel.dto;

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
public record RoomCreateRequest(
        @NotNull @Positive Long hotelId,
        @NotBlank @Size(max = 50) String number,
        Boolean available
) {
}
