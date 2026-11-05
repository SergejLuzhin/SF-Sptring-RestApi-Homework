package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import skillfactory.luzhin.booking.entity.UserRole;

/**
 * Безопасное представление пользователя без пароля и его хеша.
 *
 * @param id идентификатор пользователя
 * @param username имя пользователя
 * @param role роль пользователя
 */
@Schema(description = "Пользователь без пароля и его хеша")
public record UserResponse(
        @Schema(description = "Идентификатор пользователя", example = "2") Long id,
        @Schema(description = "Имя пользователя", example = "user@example.com") String username,
        @Schema(description = "Роль пользователя", example = "USER", allowableValues = {"USER", "ADMIN"}) UserRole role
) {
}
