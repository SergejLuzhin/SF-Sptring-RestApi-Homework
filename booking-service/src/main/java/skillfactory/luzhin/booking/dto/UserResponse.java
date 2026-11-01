package skillfactory.luzhin.booking.dto;

import skillfactory.luzhin.booking.entity.UserRole;

/**
 * Безопасное представление пользователя без пароля и его хеша.
 *
 * @param id идентификатор пользователя
 * @param username имя пользователя
 * @param role роль пользователя
 */
public record UserResponse(Long id, String username, UserRole role) {
}
