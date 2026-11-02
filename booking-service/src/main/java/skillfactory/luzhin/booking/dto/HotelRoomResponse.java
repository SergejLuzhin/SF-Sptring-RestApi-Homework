package skillfactory.luzhin.booking.dto;

/**
 * Представление номера, возвращаемое Hotel Service.
 *
 * @param id идентификатор номера
 * @param hotelId идентификатор отеля
 * @param number номер комнаты
 * @param available эксплуатационная доступность
 * @param timesBooked число успешных бронирований
 */
public record HotelRoomResponse(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {
}
