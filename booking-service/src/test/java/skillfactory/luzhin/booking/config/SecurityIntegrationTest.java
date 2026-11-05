package skillfactory.luzhin.booking.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.service.InternalJwtService;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет JWT Resource Server, роли и служебные токены Booking Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private InternalJwtService internalJwtService;

    /**
     * Проверяет доступ к административной операции с подписанным ADMIN JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldAllowAdminEndpointWithValidAdminToken() throws Exception {
        String token = issueToken(
                jwtEncoder,
                jwtProperties.issuer(),
                "ADMIN",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "security-admin@example.com",
                                  "password": "adminPassword123",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /**
     * Проверяет ответ 401 ProblemDetail при отсутствии JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectMissingToken() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("JWT is missing or invalid"))
                .andExpect(jsonPath("$.instance").value("/user"));
    }

    /**
     * Проверяет ответ 403 при обращении USER к административной операции.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectUserRoleOnAdminEndpoint() throws Exception {
        String token = issueToken(
                jwtEncoder,
                jwtProperties.issuer(),
                "USER",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access is denied"));
    }

    /**
     * Проверяет отказ просроченного JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectExpiredToken() throws Exception {
        String token = issueToken(
                jwtEncoder,
                jwtProperties.issuer(),
                "ADMIN",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Проверяет отказ JWT, подписанного неизвестным ключом.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectTokenWithInvalidSignature() throws Exception {
        JwtEncoder forgedEncoder = encoderWithSecret("forged-booking-service-secret-with-at-least-32-bytes");
        String token = issueToken(
                forgedEncoder,
                jwtProperties.issuer(),
                "ADMIN",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Проверяет отказ JWT от неожиданного издателя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectUnexpectedIssuer() throws Exception {
        String token = issueToken(
                jwtEncoder,
                "unexpected-issuer",
                "ADMIN",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Проверяет claims и пятиминутный срок действия служебного INTERNAL JWT.
     */
    @Test
    void shouldIssueShortLivedInternalToken() {
        String token = internalJwtService.issueToken();

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("booking-service");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("INTERNAL");
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(300);
        assertThat(jwt.getClaimAsString("username")).isNull();
    }

    /**
     * Выпускает JWT с заданными claims для проверки цепочки безопасности.
     *
     * @param encoder кодировщик JWT
     * @param issuer издатель токена
     * @param role роль владельца токена
     * @param issuedAt время выпуска
     * @param expiresAt время истечения
     * @return подписанный JWT
     */
    private String issueToken(
            JwtEncoder encoder,
            String issuer,
            String role,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("1")
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Создаёт кодировщик JWT с указанным симметричным ключом.
     *
     * @param secret секретный ключ HS256
     * @return кодировщик JWT
     */
    private JwtEncoder encoderWithSecret(String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
