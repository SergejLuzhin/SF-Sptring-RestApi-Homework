package skillfactory.luzhin.hotel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI и bearer JWT для Hotel Service.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Создаёт описание API, серверов и схемы аутентификации.
     *
     * @return конфигурация OpenAPI Hotel Service
     */
    @Bean
    public OpenAPI hotelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hotel Service API")
                        .version("1.0")
                        .description("Управление отелями и номерами, поиск доступности и внутренние операции занятости."))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway — только публичные операции"))
                .addServersItem(new Server()
                        .url("http://localhost:8081")
                        .description("Hotel Service — прямой доступ, включая INTERNAL-операции"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT с ролью USER, ADMIN или INTERNAL согласно описанию операции.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
