package skillfactory.luzhin.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки подписи JWT.
 *
 * @param secret секретный ключ HS256
 * @param issuer издатель токена
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String issuer) {
}
