package skillfactory.luzhin.hotel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.hotel.dto.HotelCreateRequest;
import skillfactory.luzhin.hotel.dto.HotelResponse;
import skillfactory.luzhin.hotel.dto.HotelUpdateRequest;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.exception.BusinessConflictException;
import skillfactory.luzhin.hotel.exception.ResourceNotFoundException;
import skillfactory.luzhin.hotel.mapper.HotelMapper;
import skillfactory.luzhin.hotel.repository.HotelRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

import java.util.List;

/**
 * Реализует операции управления отелями.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final HotelMapper hotelMapper;

    /**
     * Создаёт новый отель.
     *
     * @param request данные отеля
     * @return созданный отель
     */
    @Transactional
    public HotelResponse create(HotelCreateRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        return hotelMapper.toResponse(hotelRepository.save(hotel));
    }

    /**
     * Возвращает список всех отелей.
     *
     * @return список отелей
     */
    public List<HotelResponse> findAll() {
        return hotelRepository.findAll().stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    /**
     * Обновляет переданные поля отеля.
     *
     * @param id идентификатор отеля
     * @param request данные обновления
     * @return обновлённый отель
     */
    @Transactional
    public HotelResponse update(Long id, HotelUpdateRequest request) {
        Hotel hotel = findById(id);
        hotelMapper.updateEntity(request, hotel);
        return hotelMapper.toResponse(hotel);
    }

    /**
     * Удаляет отель, если с его номерами не связана история занятости.
     *
     * @param id идентификатор отеля
     */
    @Transactional
    public void delete(Long id) {
        Hotel hotel = findById(id);
        if (roomReservationRepository.existsByRoomHotelId(id)) {
            throw new BusinessConflictException("Hotel cannot be deleted because reservation history exists");
        }
        hotelRepository.delete(hotel);
    }

    /**
     * Находит отель или сообщает об его отсутствии.
     *
     * @param id идентификатор отеля
     * @return сущность отеля
     */
    private Hotel findById(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
    }
}
