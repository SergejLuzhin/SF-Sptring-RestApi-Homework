package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import skillfactory.luzhin.booking.config.MdcScope;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.dto.ConfirmAvailabilityRequest;
import skillfactory.luzhin.booking.dto.ReleaseReason;
import skillfactory.luzhin.booking.dto.ReleaseReservationRequest;
import skillfactory.luzhin.booking.entity.BookingStatus;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.InvalidRequestException;
import skillfactory.luzhin.booking.exception.ServiceUnavailableException;

import java.util.List;
import java.util.UUID;

/**
 * Координирует распределённое подтверждение, компенсацию и отмену бронирований.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingPersistenceService bookingPersistenceService;
    private final HotelServiceClient hotelServiceClient;

    /**
     * Создаёт PENDING-бронь, подтверждает номер и фиксирует CONFIRMED либо компенсацию.
     *
     * @param subject идентификатор пользователя из JWT
     * @param userToken JWT пользователя для запроса рекомендаций
     * @param request параметры бронирования
     * @return подтверждённая или уже существующая бронь
     */
    public BookingCreationResult create(String subject, String userToken, BookingCreateRequest request) {
        try (MdcScope ignored = MdcScope.open(request.requestId(), null)) {
            validateRequest(request);
            Long userId = parseUserId(subject);
            var existing = bookingPersistenceService.findExistingBooking(userId, request);
            if (existing.isPresent()) {
                BookingResponse booking = existing.get();
                try (MdcScope bookingScope = MdcScope.open(request.requestId(), booking.id())) {
                    log.info("Idempotent booking request returned existing result; status={}", booking.status());
                }
                return new BookingCreationResult(booking, false);
            }

            Long roomId = selectRoom(userToken, request);
            BookingResponse pending;
            try {
                pending = bookingPersistenceService.createPending(userId, request, roomId);
            } catch (DataIntegrityViolationException exception) {
                return bookingPersistenceService.findExistingBooking(userId, request)
                        .map(booking -> new BookingCreationResult(booking, false))
                        .orElseThrow(() -> exception);
            }
            try (MdcScope bookingScope = MdcScope.open(request.requestId(), pending.id())) {
                log.info("Booking PENDING created; roomId={}", roomId);
                try {
                    hotelServiceClient.confirmAvailability(roomId, new ConfirmAvailabilityRequest(
                            pending.id(),
                            request.requestId(),
                            request.startDate(),
                            request.endDate()
                    ));
                    BookingResponse confirmed = bookingPersistenceService.confirm(pending.id());
                    log.info("Booking CONFIRMED; roomId={}", roomId);
                    return new BookingCreationResult(confirmed, true);
                } catch (RuntimeException confirmationFailure) {
                    log.warn("Booking confirmation failed; reason={}", confirmationFailure.getMessage());
                    compensate(pending, request.requestId());
                    throw confirmationFailure;
                }
            }
        }
    }

    /**
     * Возвращает историю бронирований текущего пользователя.
     *
     * @param subject идентификатор пользователя из JWT
     * @return бронирования пользователя от новых к старым
     */
    public List<BookingResponse> findHistory(String subject) {
        return bookingPersistenceService.findHistory(parseUserId(subject));
    }

    /**
     * Возвращает бронирование, если оно принадлежит текущему пользователю.
     *
     * @param subject идентификатор пользователя из JWT
     * @param id идентификатор бронирования
     * @return запрошенная бронь
     */
    public BookingResponse findById(String subject, Long id) {
        return bookingPersistenceService.findById(parseUserId(subject), id);
    }

    /**
     * Отменяет собственную бронь, предварительно освобождая подтверждённый номер.
     *
     * @param subject идентификатор пользователя из JWT
     * @param id идентификатор бронирования
     */
    public void cancel(String subject, Long id) {
        Long userId = parseUserId(subject);
        BookingCancellation booking = bookingPersistenceService.findForCancellation(userId, id);
        try (MdcScope ignored = MdcScope.open(booking.requestId(), booking.id())) {
            if (booking.status() == BookingStatus.CANCELLED) {
                log.info("Booking cancellation skipped; booking is already CANCELLED");
                return;
            }
            if (booking.status() == BookingStatus.PENDING) {
                bookingPersistenceService.cancelPending(userId, id);
                log.info("Pending booking CANCELLED without remote release");
                return;
            }

            hotelServiceClient.release(booking.roomId(), new ReleaseReservationRequest(
                    booking.id(),
                    booking.requestId(),
                    ReleaseReason.USER_CANCELLATION
            ));
            try {
                bookingPersistenceService.cancelConfirmed(userId, id);
                log.info("Confirmed booking CANCELLED after remote release");
            } catch (RuntimeException exception) {
                log.error("Local cancellation failed after remote release", exception);
                throw new ServiceUnavailableException("Hotel room was released but the local booking cancellation failed");
            }
        }
    }

    /**
     * Выбирает переданный вручную номер либо первую рекомендацию Hotel Service.
     *
     * @param userToken JWT пользователя
     * @param request параметры бронирования
     * @return фактический идентификатор номера
     */
    private Long selectRoom(String userToken, BookingCreateRequest request) {
        if (!request.autoSelect()) {
            return request.roomId();
        }
        return hotelServiceClient.findRecommendedRoomId(
                        userToken,
                        request.requestId(),
                        request.startDate(),
                        request.endDate()
                )
                .orElseThrow(() -> new BusinessConflictException("No rooms are available for the requested dates"));
    }

    /**
     * Освобождает возможную удалённую блокировку и отменяет локальную PENDING-бронь.
     *
     * @param pending локальная бронь до подтверждения
     * @param requestId идентификатор идемпотентности исходного запроса
     */
    private void compensate(BookingResponse pending, UUID requestId) {
        try {
            hotelServiceClient.release(pending.roomId(), new ReleaseReservationRequest(
                    pending.id(),
                    requestId,
                    ReleaseReason.COMPENSATION
            ));
            bookingPersistenceService.cancelAfterCompensation(pending.id());
            log.info("Booking compensation completed; status=CANCELLED");
        } catch (RuntimeException compensationFailure) {
            log.error("Booking compensation failed; booking requires reconciliation", compensationFailure);
            throw new ServiceUnavailableException("Hotel Service compensation failed; booking requires reconciliation");
        }
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
