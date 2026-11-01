package skillfactory.luzhin.booking.dto;

/**
 * Ответ с выпущенным токеном доступа.
 *
 * @param accessToken JWT доступа
 * @param tokenType тип токена
 * @param expiresIn срок действия в секундах
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
}
