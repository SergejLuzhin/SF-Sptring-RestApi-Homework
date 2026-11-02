package skillfactory.luzhin.hotel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Запрос на освобождение ранее заблокированного номера.
 *
 * @param bookingId идентификатор бронирования
 * @param requestId идентификатор идемпотентности
 * @param reason причина освобождения номера
 */
public record ReleaseReservationRequest(
        @NotNull @Positive Long bookingId,
        @NotNull UUID requestId,
        @NotNull ReleaseReason reason
) {
}
