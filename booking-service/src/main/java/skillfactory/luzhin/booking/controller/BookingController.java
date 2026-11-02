package skillfactory.luzhin.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Бронирования", description = "Личные бронирования пользователя с ролью USER")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Создаёт бронирование или возвращает исходный результат повторного requestId.
     *
     * @param request параметры бронирования
     * @param jwt JWT текущего пользователя
     * @return ответ 201 для новой брони или 200 для идемпотентного повтора
     */
    @Operation(
            summary = "Создать бронирование",
            description = "Требуется роль USER. При autoSelect=true поле roomId игнорируется. "
                    + "Повтор того же requestId и параметров возвращает исходную бронь без новой блокировки."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Бронирование создано и подтверждено"),
            @ApiResponse(responseCode = "200", description = "Возвращён результат идемпотентного повтора"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры или интервал дат",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Выбранный номер не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Номер занят, нет свободных номеров или конфликт requestId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Hotel Service недоступен или компенсация не удалась",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/booking")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        BookingCreationResult result = bookingService.create(jwt.getSubject(), jwt.getTokenValue(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * Возвращает историю бронирований текущего пользователя.
     *
     * @param jwt JWT текущего пользователя
     * @return список личных бронирований
     */
    @Operation(summary = "Получить историю бронирований", description = "Требуется роль USER; возвращаются только свои брони.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "История получена"),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/bookings")
    public List<BookingResponse> findHistory(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return bookingService.findHistory(jwt.getSubject());
    }

    /**
     * Возвращает личное бронирование по идентификатору.
     *
     * @param id идентификатор бронирования
     * @param jwt JWT текущего пользователя
     * @return бронирование текущего пользователя
     */
    @Operation(summary = "Получить свою бронь", description = "Требуется роль USER и владение бронированием.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Бронирование найдено"),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Бронирование не найдено",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/booking/{id}")
    public BookingResponse findById(
            @Parameter(description = "Идентификатор бронирования", example = "25")
            @Positive @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.findById(jwt.getSubject(), id);
    }

    /**
     * Отменяет личное бронирование по идентификатору.
     *
     * @param id идентификатор бронирования
     * @param jwt JWT текущего пользователя
     */
    @Operation(
            summary = "Отменить свою бронь",
            description = "Требуется роль USER и владение бронированием. Повторная отмена CANCELLED возвращает 204."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Бронирование отменено", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Бронирование не найдено",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Не удалось освободить номер в Hotel Service",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/booking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @Parameter(description = "Идентификатор бронирования", example = "25")
            @Positive @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        bookingService.cancel(jwt.getSubject(), id);
    }
}
