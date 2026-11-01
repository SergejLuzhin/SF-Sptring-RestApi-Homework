package skillfactory.luzhin.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skillfactory.luzhin.booking.entity.Booking;

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
     * Проверяет наличие истории бронирований пользователя.
     *
     * @param userId идентификатор пользователя
     * @return признак наличия хотя бы одной брони
     */
    boolean existsByUserId(Long userId);
}
