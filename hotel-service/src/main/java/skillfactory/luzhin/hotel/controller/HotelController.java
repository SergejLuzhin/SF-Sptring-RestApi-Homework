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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skillfactory.luzhin.hotel.dto.HotelCreateRequest;
import skillfactory.luzhin.hotel.dto.HotelResponse;
import skillfactory.luzhin.hotel.dto.HotelUpdateRequest;
import skillfactory.luzhin.hotel.service.HotelService;

import java.util.List;

/**
 * REST-контроллер управления отелями.
 */
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Validated
@Tag(name = "Отели", description = "Просмотр для USER и администрирование для ADMIN")
public class HotelController {

    private final HotelService hotelService;

    /**
     * Создаёт отель.
     *
     * @param request данные нового отеля
     * @return созданный отель
     */
    @Operation(summary = "Создать отель", description = "Требуется роль ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отель создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные отеля",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponse create(@Valid @RequestBody HotelCreateRequest request) {
        return hotelService.create(request);
    }

    /**
     * Возвращает все отели.
     *
     * @return список отелей
     */
    @Operation(summary = "Получить список отелей", description = "Требуется роль USER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список отелей получен"),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public List<HotelResponse> findAll() {
        return hotelService.findAll();
    }

    /**
     * Обновляет переданные поля отеля.
     *
     * @param id идентификатор отеля
     * @param request данные обновления
     * @return обновлённый отель
     */
    @Operation(summary = "Обновить отель", description = "Требуется роль ADMIN; изменяются только переданные поля.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отель обновлён"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные отеля",
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
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    public HotelResponse update(
            @Parameter(description = "Идентификатор отеля", example = "1")
            @Positive @PathVariable Long id,
            @Valid @RequestBody HotelUpdateRequest request
    ) {
        return hotelService.update(id, request);
    }

    /**
     * Удаляет отель.
     *
     * @param id идентификатор отеля
     */
    @Operation(
            summary = "Удалить отель",
            description = "Требуется роль ADMIN. Отель со связанными номерами или историей занятости не удаляется."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отель удалён", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
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
            @ApiResponse(responseCode = "409", description = "Удаление нарушит целостность связанных данных",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Идентификатор отеля", example = "1")
            @Positive @PathVariable Long id
    ) {
        hotelService.delete(id);
    }
}
