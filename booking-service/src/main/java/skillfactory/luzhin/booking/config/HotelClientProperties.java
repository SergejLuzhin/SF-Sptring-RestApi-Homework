package skillfactory.luzhin.booking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки соединения и повторов вызовов Hotel Service.
 *
 * @param baseUrl базовый адрес Hotel Service
 * @param connectTimeout предельное время установления соединения
 * @param readTimeout предельное время ожидания ответа
 * @param confirm настройки повторов подтверждения доступности
 * @param release настройки повторов освобождения номера
 */
@Validated
@ConfigurationProperties(prefix = "hotel.client")
public record HotelClientProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull @Valid Retry confirm,
        @NotNull @Valid Retry release
) {

    /**
     * Настройки ограниченных повторов одной идемпотентной операции.
     *
     * @param maxAttempts общее число попыток, включая первоначальный вызов
     * @param initialBackoff задержка перед первым повтором
     * @param maxBackoff максимальная задержка между попытками
     */
    public record Retry(
            @Min(1) int maxAttempts,
            @NotNull Duration initialBackoff,
            @NotNull Duration maxBackoff
    ) {
    }
}
