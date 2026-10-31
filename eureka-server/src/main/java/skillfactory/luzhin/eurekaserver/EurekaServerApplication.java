package skillfactory.luzhin.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Entry point for the Eureka service registry.
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    /**
     * Starts the Eureka Server application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
