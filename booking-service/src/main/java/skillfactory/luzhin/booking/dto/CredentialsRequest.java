package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Учётные данные для регистрации или входа.
 *
 * @param username имя пользователя
 * @param password пароль
 */
@Schema(description = "Учётные данные для регистрации или входа")
public record CredentialsRequest(
        @Schema(description = "Уникальное имя пользователя", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String username,
        @Schema(description = "Пароль длиной от 8 до 72 символов", example = "password123",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
