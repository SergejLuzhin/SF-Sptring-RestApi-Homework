package skillfactory.luzhin.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import skillfactory.luzhin.booking.entity.UserRole;

/**
 * Данные для административного создания пользователя.
 *
 * @param username имя пользователя
 * @param password пароль
 * @param role роль пользователя
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role
) {
}
