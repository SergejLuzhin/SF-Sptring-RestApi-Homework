package skillfactory.luzhin.hotel.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import skillfactory.luzhin.hotel.dto.ConfirmAvailabilityRequest;
import skillfactory.luzhin.hotel.dto.ReleaseReservationRequest;
import skillfactory.luzhin.hotel.dto.RoomReservationResponse;
import skillfactory.luzhin.hotel.service.RoomReservationService;

/**
 * Внутренний REST-контроллер подтверждения и освобождения занятости номеров.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Validated
public class RoomReservationController {

    private final RoomReservationService roomReservationService;

    /**
     * Подтверждает доступность номера и создаёт активную блокировку.
     *
     * @param id идентификатор номера
     * @param request параметры бронирования
     * @return результат подтверждения доступности
     */
    @PostMapping("/{id}/confirm-availability")
    public RoomReservationResponse confirmAvailability(
            @Positive @PathVariable Long id,
            @Valid @RequestBody ConfirmAvailabilityRequest request
    ) {
        return roomReservationService.confirmAvailability(id, request);
    }

    /**
     * Освобождает номер после компенсации или отмены пользователем.
     *
     * @param id идентификатор номера
     * @param request параметры освобождения
     * @return результат освобождения номера
     */
    @PostMapping("/{id}/release")
    public RoomReservationResponse release(
            @Positive @PathVariable Long id,
            @Valid @RequestBody ReleaseReservationRequest request
    ) {
        return roomReservationService.release(id, request);
    }
}
