package skillfactory.luzhin.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI и bearer JWT для Booking Service.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Создаёт описание API, серверов и схемы аутентификации.
     *
     * @return конфигурация OpenAPI Booking Service
     */
    @Bean
    public OpenAPI bookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Service API")
                        .version("1.0")
                        .description("Регистрация, аутентификация, администрирование пользователей и личные бронирования."))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway — рекомендуемый адрес публичных операций"))
                .addServersItem(new Server()
                        .url("http://localhost:8082")
                        .description("Booking Service — прямой доступ"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT из /user/register или /user/auth; роль USER или ADMIN зависит от операции.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
