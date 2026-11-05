package skillfactory.luzhin.hotel.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skillfactory.luzhin.hotel.entity.Room;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий номеров и запросов их доступности.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Проверяет наличие номера с указанным названием в отеле.
     *
     * @param hotelId идентификатор отеля
     * @param number номер комнаты
     * @return признак существования номера
     */
    boolean existsByHotelIdAndNumber(Long hotelId, String number);

    /**
     * Находит номер с эксклюзивной блокировкой для атомарного подтверждения.
     *
     * @param id идентификатор номера
     * @return номер, если он существует
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    /**
     * Находит эксплуатационно доступные номера без активных пересечений по датам.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @param sort порядок сортировки результата
     * @return свободные на указанные даты номера
     */
    @Query("""
            select room
            from Room room
            where room.available = true
              and not exists (
                  select reservation.id
                  from RoomReservation reservation
                  where reservation.room = room
                    and reservation.status = skillfactory.luzhin.hotel.entity.RoomReservationStatus.HELD
                    and :startDate < reservation.endDate
                    and :endDate > reservation.startDate
              )
            """)
    List<Room> findAvailableRooms(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Sort sort
    );
}
