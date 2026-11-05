package skillfactory.luzhin.booking.service;

import skillfactory.luzhin.booking.dto.BookingResponse;

/**
 * Результат создания или идемпотентного повтора запроса.
 *
 * @param response представление бронирования
 * @param created признак создания новой записи
 */
public record BookingCreationResult(BookingResponse response, boolean created) {
}
