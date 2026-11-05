package skillfactory.luzhin.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Представление номера в ответах API.
 *
 * @param id идентификатор номера
 * @param hotelId идентификатор отеля
 * @param number номер комнаты
 * @param available эксплуатационная доступность
 * @param timesBooked количество успешных бронирований
 */
@Schema(description = "Номер отеля в ответах API")
public record RoomResponse(
        @Schema(description = "Идентификатор номера", example = "10") Long id,
        @Schema(description = "Идентификатор отеля", example = "1") Long hotelId,
        @Schema(description = "Номер комнаты", example = "101") String number,
        @Schema(description = "Эксплуатационная доступность, не свободность на конкретные даты", example = "true")
        boolean available,
        @Schema(description = "Количество успешных бронирований", example = "4") long timesBooked
) {
}
