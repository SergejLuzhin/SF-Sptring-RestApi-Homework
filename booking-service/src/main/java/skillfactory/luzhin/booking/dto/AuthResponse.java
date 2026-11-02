package skillfactory.luzhin.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ с выпущенным токеном доступа.
 *
 * @param accessToken JWT доступа
 * @param tokenType тип токена
 * @param expiresIn срок действия в секундах
 */
@Schema(description = "Выпущенный пользовательский JWT")
public record AuthResponse(
        @Schema(description = "Токен доступа; передаётся в Authorization: Bearer", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Тип токена", example = "Bearer") String tokenType,
        @Schema(description = "Срок действия в секундах", example = "3600") long expiresIn
) {
}
