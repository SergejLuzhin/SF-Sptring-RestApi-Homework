package skillfactory.luzhin.hotel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки проверки JWT.
 *
 * @param secret секретный ключ HS256
 * @param issuer ожидаемый издатель токена
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String issuer) {
}
