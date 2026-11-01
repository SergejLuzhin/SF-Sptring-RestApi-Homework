package skillfactory.luzhin.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Учётные данные для регистрации или входа.
 *
 * @param username имя пользователя
 * @param password пароль
 */
public record CredentialsRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
