package skillfactory.luzhin.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skillfactory.luzhin.hotel.entity.RoomReservation;

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
}
