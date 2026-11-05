package skillfactory.luzhin.hotel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Конфигурация JWT Resource Server и ролевых правил Hotel Service.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class HotelSecurityConfig {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    /**
     * Настраивает stateless JWT Resource Server и правила доступа к API.
     *
     * @param http конфигуратор цепочки фильтров безопасности
     * @param objectMapper сериализатор ответов об ошибках
     * @param jwtAuthenticationConverter преобразователь ролей JWT
     * @return цепочка фильтров безопасности
     * @throws Exception ошибка построения конфигурации
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/rooms/*/confirm-availability",
                                "/api/rooms/*/release"
                        ).hasRole("INTERNAL")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/hotels",
                                "/api/rooms",
                                "/api/rooms/recommend"
                        ).hasRole("USER")
                        .requestMatchers("/api/hotels/**", "/api/rooms/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "JWT is missing or invalid"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Access is denied"
                        ))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "JWT is missing or invalid"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Access is denied"
                        )))
                .build();
    }

    /**
     * Создаёт декодер JWT с проверкой подписи, срока действия и издателя.
     *
     * @param properties настройки JWT
     * @return декодер JWT
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * Преобразует claim role в authority Spring Security с префиксом ROLE_.
     *
     * @return преобразователь аутентификации JWT
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    /**
     * Создаёт ключ HS256 и проверяет его минимальную длину.
     *
     * @param properties настройки JWT
     * @return симметричный ключ HmacSHA256
     */
    private SecretKeySpec secretKey(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    /**
     * Записывает ошибку безопасности в формате ProblemDetail.
     *
     * @param objectMapper сериализатор JSON
     * @param request HTTP-запрос
     * @param response HTTP-ответ
     * @param status HTTP-статус ошибки
     * @param detail описание ошибки
     * @throws IOException ошибка записи ответа
     */
    private void writeProblem(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
