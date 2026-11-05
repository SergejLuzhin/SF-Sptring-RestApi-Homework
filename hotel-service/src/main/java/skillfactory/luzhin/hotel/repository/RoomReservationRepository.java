package skillfactory.luzhin.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skillfactory.luzhin.hotel.entity.RoomReservation;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий записей о занятости номеров.
 */
public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    /**
     * Проверяет наличие активной или исторической занятости номера.
     *
     * @param roomId идентификатор номера
     * @return признак наличия записей о занятости
     */
    boolean existsByRoomId(Long roomId);

    /**
     * Проверяет наличие активной или исторической занятости хотя бы одного номера отеля.
     *
     * @param hotelId идентификатор отеля
     * @return признак наличия записей о занятости
     */
    boolean existsByRoomHotelId(Long hotelId);

    /**
     * Находит занятость по идентификатору бронирования.
     *
     * @param bookingId идентификатор бронирования
     * @return существующая запись операции
     */
    Optional<RoomReservation> findByBookingId(Long bookingId);

    /**
     * Находит занятость по идентификатору идемпотентности.
     *
     * @param requestId идентификатор запроса
     * @return существующая запись операции
     */
    Optional<RoomReservation> findByRequestId(UUID requestId);

    /**
     * Проверяет пересечение активной блокировки с запрошенным интервалом.
     *
     * @param roomId идентификатор номера
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return признак наличия пересекающейся активной блокировки
     */
    @Query("""
            select case when count(reservation) > 0 then true else false end
            from RoomReservation reservation
            where reservation.room.id = :roomId
              and reservation.status = skillfactory.luzhin.hotel.entity.RoomReservationStatus.HELD
              and :startDate < reservation.endDate
              and :endDate > reservation.startDate
            """)
    boolean existsOverlappingReservation(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
