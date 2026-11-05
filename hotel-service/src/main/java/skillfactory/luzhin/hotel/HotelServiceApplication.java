package skillfactory.luzhin.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа сервиса управления отелями.
 */
@SpringBootApplication
public class HotelServiceApplication {

    /**
     * Запускает сервис управления отелями.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }
}
