package skillfactory.luzhin.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skillfactory.luzhin.booking.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Находит пользователя по уникальному имени.
     *
     * @param username имя пользователя
     * @return пользователь, если он существует
     */
    Optional<User> findByUsername(String username);

    /**
     * Проверяет существование имени пользователя.
     *
     * @param username имя пользователя
     * @return признак существования пользователя
     */
    boolean existsByUsername(String username);
}
