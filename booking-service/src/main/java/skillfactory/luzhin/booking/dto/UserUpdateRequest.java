package skillfactory.luzhin.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import skillfactory.luzhin.booking.entity.UserRole;

/**
 * Данные для административного частичного обновления пользователя.
 *
 * @param id идентификатор пользователя
 * @param username новое имя пользователя
 * @param password новый пароль
 * @param role новая роль пользователя
 */
public record UserUpdateRequest(
        @NotNull @Positive Long id,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String username,
        @Size(min = 8, max = 72) String password,
        UserRole role
) {
}
