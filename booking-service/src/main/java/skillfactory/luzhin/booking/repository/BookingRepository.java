package skillfactory.luzhin.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skillfactory.luzhin.booking.entity.Booking;
import skillfactory.luzhin.booking.entity.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий локальных бронирований.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Находит бронирование по идентификатору идемпотентности.
     *
     * @param requestId идентификатор запроса
     * @return существующее бронирование, если запрос уже обрабатывался
     */
    Optional<Booking> findByRequestId(UUID requestId);

    /**
     * Возвращает историю пользователя от новых записей к старым.
     *
     * @param userId идентификатор пользователя
     * @return бронирования пользователя
     */
    List<Booking> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * Находит бронирование только у указанного владельца.
     *
     * @param id идентификатор бронирования
     * @param userId идентификатор владельца
     * @return бронирование, если оно принадлежит пользователю
     */
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    /**
     * Изменяет статус только если бронь всё ещё находится в ожидаемом состоянии.
     *
     * @param bookingId идентификатор бронирования
     * @param expectedStatus ожидаемый исходный статус
     * @param targetStatus новый статус
     * @param updatedAt время изменения
     * @return число изменённых записей
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking booking
            set booking.status = :targetStatus, booking.updatedAt = :updatedAt
            where booking.id = :bookingId and booking.status = :expectedStatus
            """)
    int updateStatus(
            @Param("bookingId") Long bookingId,
            @Param("expectedStatus") BookingStatus expectedStatus,
            @Param("targetStatus") BookingStatus targetStatus,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Изменяет статус собственной брони только из ожидаемого состояния.
     *
     * @param bookingId идентификатор бронирования
     * @param userId идентификатор владельца
     * @param expectedStatus ожидаемый исходный статус
     * @param targetStatus новый статус
     * @param updatedAt время изменения
     * @return число изменённых записей
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking booking
            set booking.status = :targetStatus, booking.updatedAt = :updatedAt
            where booking.id = :bookingId
              and booking.user.id = :userId
              and booking.status = :expectedStatus
            """)
    int updateOwnedStatus(
            @Param("bookingId") Long bookingId,
            @Param("userId") Long userId,
            @Param("expectedStatus") BookingStatus expectedStatus,
            @Param("targetStatus") BookingStatus targetStatus,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Проверяет наличие истории бронирований пользователя.
     *
     * @param userId идентификатор пользователя
     * @return признак наличия хотя бы одной брони
     */
    boolean existsByUserId(Long userId);
}
