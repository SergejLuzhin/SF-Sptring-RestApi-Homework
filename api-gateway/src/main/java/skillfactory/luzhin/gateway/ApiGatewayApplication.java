package skillfactory.luzhin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа API Gateway.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Запускает приложение API Gateway.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
