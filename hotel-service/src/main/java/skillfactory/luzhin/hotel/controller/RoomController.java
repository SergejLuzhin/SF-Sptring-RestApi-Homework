package skillfactory.luzhin.hotel.controller;

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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skillfactory.luzhin.hotel.dto.RoomCreateRequest;
import skillfactory.luzhin.hotel.dto.RoomResponse;
import skillfactory.luzhin.hotel.dto.RoomUpdateRequest;
import skillfactory.luzhin.hotel.service.RoomService;

import java.time.LocalDate;
import java.util.List;

/**
 * REST-контроллер управления номерами и поиска доступных вариантов.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Validated
@Tag(name = "Номера", description = "Поиск доступности для USER и администрирование для ADMIN")
public class RoomController {

    private final RoomService roomService;

    /**
     * Создаёт номер в отеле.
     *
     * @param request данные нового номера
     * @return созданный номер
     */
    @Operation(summary = "Создать номер", description = "Требуется роль ADMIN; number уникален в пределах отеля.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Номер создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные номера",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Отель не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Номер комнаты уже существует в отеле",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody RoomCreateRequest request) {
        return roomService.create(request);
    }

    /**
     * Возвращает свободные на указанные даты номера.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return список свободных номеров
     */
    @Operation(
            summary = "Найти свободные номера",
            description = "Требуется роль USER. Интервал [startDate, endDate), startDate должен быть раньше endDate."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список свободных номеров получен"),
            @ApiResponse(responseCode = "400", description = "Некорректные или отсутствующие даты",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public List<RoomResponse> findAvailable(
            @Parameter(description = "Дата заезда в формате yyyy-MM-dd", example = "2026-11-10")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Дата выезда в формате yyyy-MM-dd", example = "2026-11-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return roomService.findAvailable(startDate, endDate);
    }

    /**
     * Возвращает свободные номера в порядке рекомендации.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return отсортированный список свободных номеров
     */
    @Operation(
            summary = "Получить рекомендованные номера",
            description = "Требуется роль USER. Сортировка строго timesBooked ASC, id ASC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отсортированный список свободных номеров получен"),
            @ApiResponse(responseCode = "400", description = "Некорректные или отсутствующие даты",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/recommend")
    public List<RoomResponse> findRecommended(
            @Parameter(description = "Дата заезда в формате yyyy-MM-dd", example = "2026-11-10")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Дата выезда в формате yyyy-MM-dd", example = "2026-11-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return roomService.findRecommended(startDate, endDate);
    }

    /**
     * Обновляет переданные поля номера.
     *
     * @param id идентификатор номера
     * @param request данные обновления
     * @return обновлённый номер
     */
    @Operation(
            summary = "Обновить номер",
            description = "Требуется роль ADMIN. Поле available управляет эксплуатацией, а не занятостью на даты."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Номер обновлён"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные номера",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Номер или отель не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Номер комнаты уже существует в отеле",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    public RoomResponse update(
            @Parameter(description = "Идентификатор номера", example = "10")
            @Positive @PathVariable Long id,
            @Valid @RequestBody RoomUpdateRequest request
    ) {
        return roomService.update(id, request);
    }

    /**
     * Удаляет номер.
     *
     * @param id идентификатор номера
     */
    @Operation(
            summary = "Удалить номер",
            description = "Требуется роль ADMIN. Номер с активной или исторической занятостью не удаляется."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Номер удалён", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Номер не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Удаление нарушит целостность истории занятости",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Идентификатор номера", example = "10")
            @Positive @PathVariable Long id
    ) {
        roomService.delete(id);
    }
}
