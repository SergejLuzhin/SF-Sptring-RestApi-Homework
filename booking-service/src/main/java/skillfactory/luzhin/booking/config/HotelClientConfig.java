package skillfactory.luzhin.booking.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация HTTP-клиента Hotel Service с балансировкой через Service Discovery.
 */
@Configuration
public class HotelClientConfig {

    /**
     * Создаёт построитель, который Spring Cloud оснащает балансировкой через Service Discovery.
     *
     * @return load-balanced построитель HTTP-клиента
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder hotelServiceRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Создаёт клиент для обращения к экземплярам hotel-service из Eureka.
     *
     * @param builder клиентский построитель с поддержкой балансировки
     * @return настроенный HTTP-клиент
     */
    @Bean
    public RestClient hotelServiceRestClient(
            @Qualifier("hotelServiceRestClientBuilder") RestClient.Builder builder
    ) {
        return builder.baseUrl("http://hotel-service").build();
    }
}
