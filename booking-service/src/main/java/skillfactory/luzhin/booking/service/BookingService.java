package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.entity.Booking;
import skillfactory.luzhin.booking.entity.BookingStatus;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.InvalidRequestException;
import skillfactory.luzhin.booking.exception.ResourceNotFoundException;
import skillfactory.luzhin.booking.exception.ServiceUnavailableException;
import skillfactory.luzhin.booking.mapper.BookingMapper;
import skillfactory.luzhin.booking.repository.BookingRepository;
import skillfactory.luzhin.booking.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Реализует локальную модель бронирований и правила владения записями.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final Clock clock;

    /**
     * Создаёт PENDING-бронь или возвращает результат идемпотентного повтора.
     *
     * @param subject идентификатор пользователя из JWT
     * @param request параметры бронирования
     * @return созданная или уже существующая бронь
     */
    @Transactional
    public BookingCreationResult create(String subject, BookingCreateRequest request) {
        validateRequest(request);
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return bookingRepository.findByRequestId(request.requestId())
                .map(existing -> new BookingCreationResult(repeat(existing, userId, request), false))
                .orElseGet(() -> new BookingCreationResult(createPending(user, request), true));
    }

    /**
     * Возвращает историю бронирований текущего пользователя.
     *
     * @param subject идентификатор пользователя из JWT
     * @return бронирования пользователя от новых к старым
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> findHistory(String subject) {
        Long userId = parseUserId(subject);
        return bookingRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    /**
     * Возвращает бронирование, если оно принадлежит текущему пользователю.
     *
     * @param subject идентификатор пользователя из JWT
     * @param id идентификатор бронирования
     * @return запрошенная бронь
     */
    @Transactional(readOnly = true)
    public BookingResponse findById(String subject, Long id) {
        return bookingMapper.toResponse(findOwnedBooking(subject, id));
    }

    /**
     * Отменяет локальную PENDING-бронь текущего пользователя.
     *
     * @param subject идентификатор пользователя из JWT
     * @param id идентификатор бронирования
     */
    @Transactional
    public void cancel(String subject, Long id) {
        Booking booking = findOwnedBooking(subject, id);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ServiceUnavailableException("Hotel Service release is required to cancel a confirmed booking");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(clock.instant());
    }

    /**
     * Создаёт и сохраняет новую локальную бронь.
     *
     * @param user владелец бронирования
     * @param request параметры запроса
     * @return DTO созданной брони
     */
    private BookingResponse createPending(User user, BookingCreateRequest request) {
        Instant now = clock.instant();
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(request.autoSelect() ? null : request.roomId());
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setStatus(BookingStatus.PENDING);
        booking.setRequestId(request.requestId());
        booking.setAutoSelect(request.autoSelect());
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        return bookingMapper.toResponse(bookingRepository.saveAndFlush(booking));
    }

    /**
     * Проверяет совпадение параметров повторного запроса.
     *
     * @param booking существующая бронь
     * @param userId идентификатор текущего пользователя
     * @param request повторный запрос
     * @return DTO исходной брони
     */
    private BookingResponse repeat(Booking booking, Long userId, BookingCreateRequest request) {
        boolean sameParameters = booking.getUser().getId().equals(userId)
                && booking.getAutoSelect().equals(request.autoSelect())
                && booking.getStartDate().equals(request.startDate())
                && booking.getEndDate().equals(request.endDate())
                && (request.autoSelect() || Objects.equals(booking.getRoomId(), request.roomId()));
        if (!sameParameters) {
            throw new BusinessConflictException("requestId already exists with different parameters");
        }
        return bookingMapper.toResponse(booking);
    }

    /**
     * Проверяет обязательный roomId и корректность интервала дат.
     *
     * @param request параметры бронирования
     */
    private void validateRequest(BookingCreateRequest request) {
        if (!request.autoSelect() && request.roomId() == null) {
            throw new InvalidRequestException("roomId is required when autoSelect is false");
        }
        if (!request.startDate().isBefore(request.endDate())) {
            throw new InvalidRequestException("startDate must be earlier than endDate");
        }
    }

    /**
     * Находит запись без раскрытия существования чужой брони.
     *
     * @param subject идентификатор пользователя из JWT
     * @param id идентификатор бронирования
     * @return бронь текущего пользователя
     */
    private Booking findOwnedBooking(String subject, Long id) {
        Long userId = parseUserId(subject);
        return bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    /**
     * Извлекает положительный идентификатор пользователя из subject JWT.
     *
     * @param subject subject JWT
     * @return идентификатор пользователя
     */
    private Long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new InvalidRequestException("JWT subject must contain a valid user id");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException("JWT subject must contain a valid user id");
        }
    }
}
