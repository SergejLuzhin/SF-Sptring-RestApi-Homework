package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Данные для административного создания пользователя")
public record UserCreateRequest(
        @Schema(description = "Уникальное имя пользователя", example = "admin@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String username,
        @Schema(description = "Пароль длиной от 8 до 72 символов", example = "password123",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(description = "Роль пользователя", example = "ADMIN", allowableValues = {"USER", "ADMIN"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UserRole role
) {
}
