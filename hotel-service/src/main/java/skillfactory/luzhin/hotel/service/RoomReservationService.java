package skillfactory.luzhin.hotel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.hotel.dto.ConfirmAvailabilityRequest;
import skillfactory.luzhin.hotel.dto.ReleaseReason;
import skillfactory.luzhin.hotel.dto.ReleaseReservationRequest;
import skillfactory.luzhin.hotel.dto.RoomReservationResponse;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.entity.RoomReservation;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;
import skillfactory.luzhin.hotel.exception.BusinessConflictException;
import skillfactory.luzhin.hotel.exception.InvalidRequestException;
import skillfactory.luzhin.hotel.exception.ResourceNotFoundException;
import skillfactory.luzhin.hotel.repository.RoomRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализует подтверждение доступности и компенсацию занятости номеров.
 */
@Service
@RequiredArgsConstructor
public class RoomReservationService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository roomReservationRepository;

    /**
     * Атомарно создаёт активную блокировку номера или возвращает исходную операцию.
     *
     * @param roomId идентификатор номера
     * @param request параметры блокировки
     * @return текущее состояние занятости для бронирования
     */
    @Transactional
    public RoomReservationResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        validateDates(request.startDate(), request.endDate());
        Optional<RoomReservation> existing = findOperation(request.bookingId(), request.requestId());
        if (existing.isPresent()) {
            RoomReservation reservation = existing.get();
            validateConfirmationMatch(reservation, roomId, request);
            return toResponse(reservation);
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
        Optional<RoomReservation> existingAfterLock = findOperation(request.bookingId(), request.requestId());
        if (existingAfterLock.isPresent()) {
            RoomReservation reservation = existingAfterLock.get();
            validateConfirmationMatch(reservation, roomId, request);
            return toResponse(reservation);
        }
        if (!room.isAvailable() || roomReservationRepository.existsOverlappingReservation(
                roomId,
                request.startDate(),
                request.endDate()
        )) {
            throw new BusinessConflictException("Room is not available for the requested dates");
        }

        RoomReservation reservation = new RoomReservation();
        reservation.setBookingId(request.bookingId());
        reservation.setRequestId(request.requestId());
        reservation.setRoom(room);
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStatus(RoomReservationStatus.HELD);
        reservation.setCreatedAt(Instant.now());
        roomReservationRepository.save(reservation);

        room.setTimesBooked(room.getTimesBooked() + 1);
        return toResponse(reservation);
    }

    /**
     * Освобождает блокировку идемпотентно и применяет правило исторической статистики.
     *
     * @param roomId идентификатор номера
     * @param request параметры освобождения
     * @return состояние RELEASED без создания записи при отсутствии блокировки
     */
    @Transactional
    public RoomReservationResponse release(Long roomId, ReleaseReservationRequest request) {
        roomRepository.findByIdForUpdate(roomId);
        Optional<RoomReservation> existing = findOperation(request.bookingId(), request.requestId());
        if (existing.isEmpty()) {
            return new RoomReservationResponse(request.bookingId(), roomId, RoomReservationStatus.RELEASED);
        }

        RoomReservation reservation = existing.get();
        validateReleaseMatch(reservation, roomId, request);
        if (reservation.getStatus() == RoomReservationStatus.HELD) {
            reservation.setStatus(RoomReservationStatus.RELEASED);
            if (request.reason() == ReleaseReason.COMPENSATION) {
                Room room = reservation.getRoom();
                room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
            }
        }
        return toResponse(reservation);
    }

    /**
     * Находит операцию по любому из двух уникальных идентификаторов.
     *
     * @param bookingId идентификатор бронирования
     * @param requestId идентификатор запроса
     * @return существующая запись о занятости
     */
    private Optional<RoomReservation> findOperation(Long bookingId, UUID requestId) {
        return roomReservationRepository.findByBookingId(bookingId)
                .or(() -> roomReservationRepository.findByRequestId(requestId));
    }

    /**
     * Проверяет соответствие повторного confirm исходной операции.
     *
     * @param reservation существующая запись
     * @param roomId идентификатор номера
     * @param request повторный запрос
     */
    private void validateConfirmationMatch(
            RoomReservation reservation,
            Long roomId,
            ConfirmAvailabilityRequest request
    ) {
        if (!reservation.getBookingId().equals(request.bookingId())
                || !reservation.getRequestId().equals(request.requestId())
                || !reservation.getRoom().getId().equals(roomId)
                || !reservation.getStartDate().equals(request.startDate())
                || !reservation.getEndDate().equals(request.endDate())) {
            throw new BusinessConflictException("Reservation identifiers or parameters do not match the original operation");
        }
    }

    /**
     * Проверяет соответствие release исходной блокировке.
     *
     * @param reservation существующая запись
     * @param roomId идентификатор номера
     * @param request запрос на освобождение
     */
    private void validateReleaseMatch(RoomReservation reservation, Long roomId, ReleaseReservationRequest request) {
        if (!reservation.getBookingId().equals(request.bookingId())
                || !reservation.getRequestId().equals(request.requestId())
                || !reservation.getRoom().getId().equals(roomId)) {
            throw new BusinessConflictException("Reservation identifiers or room do not match the original operation");
        }
    }

    /**
     * Проверяет корректность полуоткрытого интервала бронирования.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new InvalidRequestException("startDate must be earlier than endDate");
        }
    }

    /**
     * Преобразует запись о занятости в ответ внутреннего API.
     *
     * @param reservation запись о занятости
     * @return DTO результата операции
     */
    private RoomReservationResponse toResponse(RoomReservation reservation) {
        return new RoomReservationResponse(
                reservation.getBookingId(),
                reservation.getRoom().getId(),
                reservation.getStatus()
        );
    }
}
