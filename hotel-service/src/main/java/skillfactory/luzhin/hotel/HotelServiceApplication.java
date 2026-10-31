package skillfactory.luzhin.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Hotel Management Service.
 */
@SpringBootApplication
public class HotelServiceApplication {

    /**
     * Starts the Hotel Management Service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }
}
