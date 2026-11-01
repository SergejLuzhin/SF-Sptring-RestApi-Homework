package skillfactory.luzhin.hotel.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class HotelController {

    private final HotelService hotelService;

    /**
     * Создаёт отель.
     *
     * @param request данные нового отеля
     * @return созданный отель
     */
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
    @PatchMapping("/{id}")
    public HotelResponse update(
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
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Positive @PathVariable Long id) {
        hotelService.delete(id);
    }
}
