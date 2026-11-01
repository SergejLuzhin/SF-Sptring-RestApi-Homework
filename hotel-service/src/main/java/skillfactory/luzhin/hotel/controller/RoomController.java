package skillfactory.luzhin.hotel.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
public class RoomController {

    private final RoomService roomService;

    /**
     * Создаёт номер в отеле.
     *
     * @param request данные нового номера
     * @return созданный номер
     */
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
    @GetMapping
    public List<RoomResponse> findAvailable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
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
    @GetMapping("/recommend")
    public List<RoomResponse> findRecommended(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
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
    @PatchMapping("/{id}")
    public RoomResponse update(
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
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Positive @PathVariable Long id) {
        roomService.delete(id);
    }
}
