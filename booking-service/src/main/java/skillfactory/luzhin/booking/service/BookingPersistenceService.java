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
import skillfactory.luzhin.booking.exception.ResourceNotFoundException;
import skillfactory.luzhin.booking.mapper.BookingMapper;
import skillfactory.luzhin.booking.repository.BookingRepository;
import skillfactory.luzhin.booking.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Выполняет короткие локальные транзакции над бронированиями без HTTP-вызовов.
 */
@Service
@RequiredArgsConstructor
public class BookingPersistenceService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final Clock clock;

    /**
     * Проверяет пользователя и находит исходную бронь для идемпотентного повтора.
     *
     * @param userId идентификатор пользователя
     * @param request параметры запроса
     * @return исходная бронь, если requestId уже обрабатывался
     */
    @Transactional(readOnly = true)
    public Optional<BookingResponse> findExistingBooking(Long userId, BookingCreateRequest request) {
        findUser(userId);
        return bookingRepository.findByRequestId(request.requestId())
                .map(booking -> repeat(booking, userId, request));
    }

    /**
     * Создаёт и фиксирует новую PENDING-бронь с выбранным номером.
     *
     * @param userId идентификатор владельца
     * @param request параметры бронирования
     * @param roomId фактически выбранный номер
     * @return созданная локальная бронь
     */
    @Transactional
    public BookingResponse createPending(Long userId, BookingCreateRequest request, Long roomId) {
        User user = findUser(userId);
        Instant now = clock.instant();
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(roomId);
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
     * Переводит PENDING-бронь в CONFIRMED после успешного удалённого confirm.
     *
     * @param bookingId идентификатор бронирования
     * @return подтверждённая бронь
     */
    @Transactional
    public BookingResponse confirm(Long bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessConflictException("Cancelled booking cannot be confirmed");
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setUpdatedAt(clock.instant());
        }
        return bookingMapper.toResponse(booking);
    }

    /**
     * Переводит PENDING-бронь в CANCELLED после успешной компенсации.
     *
     * @param bookingId идентификатор бронирования
     */
    @Transactional
    public void cancelAfterCompensation(Long bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BusinessConflictException("Confirmed booking cannot be cancelled as compensation");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(clock.instant());
    }

    /**
     * Возвращает историю бронирований пользователя.
     *
     * @param userId идентификатор пользователя
     * @return бронирования от новых к старым
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> findHistory(Long userId) {
        return bookingRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    /**
     * Возвращает собственную бронь пользователя.
     *
     * @param userId идентификатор пользователя
     * @param bookingId идентификатор бронирования
     * @return запрошенная бронь
     */
    @Transactional(readOnly = true)
    public BookingResponse findById(Long userId, Long bookingId) {
        return bookingMapper.toResponse(findOwnedBooking(userId, bookingId));
    }

    /**
     * Возвращает данные собственной брони для сценария отмены.
     *
     * @param userId идентификатор пользователя
     * @param bookingId идентификатор бронирования
     * @return данные для локальной отмены или release
     */
    @Transactional(readOnly = true)
    public BookingCancellation findForCancellation(Long userId, Long bookingId) {
        Booking booking = findOwnedBooking(userId, bookingId);
        return new BookingCancellation(
                booking.getId(),
                booking.getRoomId(),
                booking.getRequestId(),
                booking.getStatus()
        );
    }

    /**
     * Локально отменяет собственную PENDING-бронь.
     *
     * @param userId идентификатор пользователя
     * @param bookingId идентификатор бронирования
     */
    @Transactional
    public void cancelPending(Long userId, Long bookingId) {
        Booking booking = findOwnedBooking(userId, bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BusinessConflictException("Confirmed booking requires Hotel Service release");
        }
        cancel(booking);
    }

    /**
     * Завершает отмену CONFIRMED-брони после успешного удалённого release.
     *
     * @param userId идентификатор пользователя
     * @param bookingId идентификатор бронирования
     */
    @Transactional
    public void cancelConfirmed(Long userId, Long bookingId) {
        Booking booking = findOwnedBooking(userId, bookingId);
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new BusinessConflictException("Pending booking has no confirmed Hotel Service reservation");
        }
        cancel(booking);
    }

    /**
     * Проверяет совпадение параметров идемпотентного повтора.
     *
     * @param booking исходная бронь
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
     * Обновляет терминальное состояние и время изменения брони.
     *
     * @param booking отменяемая бронь
     */
    private void cancel(Booking booking) {
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setUpdatedAt(clock.instant());
        }
    }

    /**
     * Находит пользователя или сообщает о его отсутствии.
     *
     * @param userId идентификатор пользователя
     * @return сущность пользователя
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Находит бронирование по идентификатору.
     *
     * @param bookingId идентификатор бронирования
     * @return сущность бронирования
     */
    private Booking findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    /**
     * Находит бронь без раскрытия существования чужой записи.
     *
     * @param userId идентификатор пользователя
     * @param bookingId идентификатор бронирования
     * @return собственная бронь пользователя
     */
    private Booking findOwnedBooking(Long userId, Long bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }
}
