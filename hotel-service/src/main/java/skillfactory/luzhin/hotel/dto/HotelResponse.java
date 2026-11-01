package skillfactory.luzhin.hotel.dto;

/**
 * Представление отеля в ответах API.
 *
 * @param id идентификатор отеля
 * @param name название отеля
 * @param address адрес отеля
 */
public record HotelResponse(Long id, String name, String address) {
}
