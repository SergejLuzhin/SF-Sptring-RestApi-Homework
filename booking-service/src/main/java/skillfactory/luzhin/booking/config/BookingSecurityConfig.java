package skillfactory.luzhin.booking.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Конфигурация хеширования паролей и выпуска JWT.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class})
public class BookingSecurityConfig {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    /**
     * Создаёт BCrypt-кодировщик паролей.
     *
     * @return кодировщик паролей
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Создаёт кодировщик JWT с симметричным ключом HS256.
     *
     * @param properties настройки JWT
     * @return кодировщик JWT
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * Предоставляет источник времени UTC для claims JWT.
     *
     * @return системные часы UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
