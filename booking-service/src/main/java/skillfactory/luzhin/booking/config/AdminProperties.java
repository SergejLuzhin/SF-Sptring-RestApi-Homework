package skillfactory.luzhin.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки начальной учётной записи администратора.
 *
 * @param username имя администратора
 * @param password начальный пароль администратора
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String username, String password) {
}
