package skillfactory.luzhin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Gateway application.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Starts the API Gateway application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
