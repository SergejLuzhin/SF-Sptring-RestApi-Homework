package skillfactory.luzhin.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Точка входа сервера регистрации и обнаружения сервисов Eureka.
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    /**
     * Запускает приложение Eureka Server.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
