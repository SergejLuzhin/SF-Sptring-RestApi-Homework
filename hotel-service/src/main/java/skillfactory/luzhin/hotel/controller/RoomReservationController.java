package skillfactory.luzhin.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
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
@Tag(
        name = "INTERNAL: занятость номеров",
        description = "Только для Booking Service с ролью INTERNAL. Операции не маршрутизируются через Gateway."
)
public class RoomReservationController {

    private final RoomReservationService roomReservationService;

    /**
     * Подтверждает доступность номера и создаёт активную блокировку.
     *
     * @param id идентификатор номера
     * @param request параметры бронирования
     * @return результат подтверждения доступности
     */
    @Operation(
            summary = "INTERNAL: подтвердить доступность",
            description = "Атомарно создаёт блокировку. Повтор bookingId/requestId возвращает исходный результат. "
                    + "Доступно только напрямую в Hotel Service с ролью INTERNAL.",
            servers = @Server(url = "http://localhost:8081", description = "Прямой вызов Hotel Service")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Номер заблокирован или возвращён исходный результат"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры или интервал дат",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль INTERNAL",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Номер не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Номер недоступен или даты пересекаются",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/confirm-availability")
    public RoomReservationResponse confirmAvailability(
            @Parameter(description = "Идентификатор номера", example = "10")
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
    @Operation(
            summary = "INTERNAL: освободить номер",
            description = "Идемпотентно снимает блокировку после компенсации или отмены пользователем. "
                    + "Доступно только напрямую в Hotel Service с ролью INTERNAL.",
            servers = @Server(url = "http://localhost:8081", description = "Прямой вызов Hotel Service")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Блокировка снята или уже отсутствовала"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль INTERNAL",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Номер не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Параметры не соответствуют исходной операции",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/release")
    public RoomReservationResponse release(
            @Parameter(description = "Идентификатор номера", example = "10")
            @Positive @PathVariable Long id,
            @Valid @RequestBody ReleaseReservationRequest request
    ) {
        return roomReservationService.release(id, request);
    }
}
