package skillfactory.luzhin.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skillfactory.luzhin.hotel.entity.Hotel;

/**
 * Репозиторий отелей.
 */
public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
