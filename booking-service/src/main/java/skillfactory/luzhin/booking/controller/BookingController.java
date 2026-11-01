package skillfactory.luzhin.booking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.service.BookingCreationResult;
import skillfactory.luzhin.booking.service.BookingService;

import java.util.List;

/**
 * REST-контроллер создания и управления личными бронированиями.
 */
@RestController
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    /**
     * Создаёт бронирование или возвращает исходный результат повторного requestId.
     *
     * @param request параметры бронирования
     * @param jwt JWT текущего пользователя
     * @return ответ 201 для новой брони или 200 для идемпотентного повтора
     */
    @PostMapping("/booking")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        BookingCreationResult result = bookingService.create(jwt.getSubject(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * Возвращает историю бронирований текущего пользователя.
     *
     * @param jwt JWT текущего пользователя
     * @return список личных бронирований
     */
    @GetMapping("/bookings")
    public List<BookingResponse> findHistory(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.findHistory(jwt.getSubject());
    }

    /**
     * Возвращает личное бронирование по идентификатору.
     *
     * @param id идентификатор бронирования
     * @param jwt JWT текущего пользователя
     * @return бронирование текущего пользователя
     */
    @GetMapping("/booking/{id}")
    public BookingResponse findById(
            @Positive @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.findById(jwt.getSubject(), id);
    }

    /**
     * Отменяет личное бронирование по идентификатору.
     *
     * @param id идентификатор бронирования
     * @param jwt JWT текущего пользователя
     */
    @DeleteMapping("/booking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @Positive @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        bookingService.cancel(jwt.getSubject(), id);
    }
}
