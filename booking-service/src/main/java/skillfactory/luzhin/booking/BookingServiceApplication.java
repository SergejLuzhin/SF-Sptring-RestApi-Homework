package skillfactory.luzhin.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Booking Service.
 */
@SpringBootApplication
public class BookingServiceApplication {

    /**
     * Starts the Booking Service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
