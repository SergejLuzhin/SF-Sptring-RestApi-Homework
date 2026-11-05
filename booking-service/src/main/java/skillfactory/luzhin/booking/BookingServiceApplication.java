package skillfactory.luzhin.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа сервиса бронирования.
 */
@SpringBootApplication
public class BookingServiceApplication {

    /**
     * Запускает сервис бронирования.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
