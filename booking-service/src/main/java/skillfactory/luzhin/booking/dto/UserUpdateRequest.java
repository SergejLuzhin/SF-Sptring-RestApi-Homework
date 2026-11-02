package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Частичное обновление пользователя; обязателен только id")
public record UserUpdateRequest(
        @Schema(description = "Идентификатор пользователя", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long id,
        @Schema(description = "Новое уникальное имя пользователя", example = "new-name@example.com")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String username,
        @Schema(description = "Новый пароль длиной от 8 до 72 символов", example = "newPassword123",
                minLength = 8, maxLength = 72)
        @Size(min = 8, max = 72) String password,
        @Schema(description = "Новая роль пользователя", example = "USER", allowableValues = {"USER", "ADMIN"})
        UserRole role
) {
}
