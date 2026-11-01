package skillfactory.luzhin.hotel.dto;

/**
 * Представление номера в ответах API.
 *
 * @param id идентификатор номера
 * @param hotelId идентификатор отеля
 * @param number номер комнаты
 * @param available эксплуатационная доступность
 * @param timesBooked количество успешных бронирований
 */
public record RoomResponse(Long id, Long hotelId, String number, boolean available, long timesBooked) {
}
