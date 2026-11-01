package skillfactory.luzhin.hotel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.hotel.dto.RoomCreateRequest;
import skillfactory.luzhin.hotel.dto.RoomResponse;
import skillfactory.luzhin.hotel.dto.RoomUpdateRequest;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.exception.BusinessConflictException;
import skillfactory.luzhin.hotel.exception.InvalidRequestException;
import skillfactory.luzhin.hotel.exception.ResourceNotFoundException;
import skillfactory.luzhin.hotel.mapper.RoomMapper;
import skillfactory.luzhin.hotel.repository.HotelRepository;
import skillfactory.luzhin.hotel.repository.RoomRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Реализует управление номерами и расчёт их доступности.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private static final Sort RECOMMENDATION_SORT = Sort.by(
            Sort.Order.asc("timesBooked"),
            Sort.Order.asc("id")
    );

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final RoomMapper roomMapper;

    /**
     * Создаёт номер в существующем отеле.
     *
     * @param request данные номера
     * @return созданный номер
     */
    @Transactional
    public RoomResponse create(RoomCreateRequest request) {
        Hotel hotel = findHotelById(request.hotelId());
        ensureNumberIsUnique(hotel.getId(), request.number());
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        return roomMapper.toResponse(roomRepository.save(room));
    }

    /**
     * Возвращает эксплуатационно доступные и свободные на даты номера.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return список свободных номеров
     */
    public List<RoomResponse> findAvailable(LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        return roomRepository.findAvailableRooms(startDate, endDate, Sort.unsorted()).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    /**
     * Возвращает свободные номера по возрастанию загруженности и идентификатора.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return отсортированный список рекомендаций
     */
    public List<RoomResponse> findRecommended(LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        return roomRepository.findAvailableRooms(startDate, endDate, RECOMMENDATION_SORT).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    /**
     * Обновляет переданные поля номера.
     *
     * @param id идентификатор номера
     * @param request данные обновления
     * @return обновлённый номер
     */
    @Transactional
    public RoomResponse update(Long id, RoomUpdateRequest request) {
        Room room = findById(id);
        Hotel targetHotel = request.hotelId() == null ? room.getHotel() : findHotelById(request.hotelId());
        String targetNumber = request.number() == null ? room.getNumber() : request.number();
        boolean identityChanged = !targetHotel.getId().equals(room.getHotel().getId())
                || !targetNumber.equals(room.getNumber());
        if (identityChanged) {
            ensureNumberIsUnique(targetHotel.getId(), targetNumber);
        }
        roomMapper.updateEntity(request, room);
        room.setHotel(targetHotel);
        return roomMapper.toResponse(room);
    }

    /**
     * Удаляет номер, если с ним не связана история занятости.
     *
     * @param id идентификатор номера
     */
    @Transactional
    public void delete(Long id) {
        Room room = findById(id);
        if (roomReservationRepository.existsByRoomId(id)) {
            throw new BusinessConflictException("Room cannot be deleted because reservation history exists");
        }
        roomRepository.delete(room);
    }

    /**
     * Проверяет корректность полуоткрытого интервала бронирования.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
            throw new InvalidRequestException("startDate must be earlier than endDate");
        }
    }

    /**
     * Проверяет уникальность номера комнаты в пределах отеля.
     *
     * @param hotelId идентификатор отеля
     * @param number номер комнаты
     */
    private void ensureNumberIsUnique(Long hotelId, String number) {
        if (roomRepository.existsByHotelIdAndNumber(hotelId, number)) {
            throw new BusinessConflictException("Room number already exists in this hotel");
        }
    }

    /**
     * Находит номер или сообщает об его отсутствии.
     *
     * @param id идентификатор номера
     * @return сущность номера
     */
    private Room findById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));
    }

    /**
     * Находит отель или сообщает об его отсутствии.
     *
     * @param id идентификатор отеля
     * @return сущность отеля
     */
    private Hotel findHotelById(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
    }
}
