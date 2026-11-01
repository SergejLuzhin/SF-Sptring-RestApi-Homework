package skillfactory.luzhin.hotel.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет JWT Resource Server и ролевые правила Hotel Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    private static final String TEST_SECRET = "booking-service-test-jwt-secret-with-at-least-32-bytes";
    private static final String TEST_ISSUER = "booking-service-test";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Проверяет доступ USER к просмотру отелей с реальным JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldAllowUserToReadHotels() throws Exception {
        String token = issueToken(TEST_SECRET, TEST_ISSUER, "USER", Instant.now(), Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Проверяет доступ ADMIN к созданию отеля с реальным JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldAllowAdminToCreateHotel() throws Exception {
        String token = issueToken(TEST_SECRET, TEST_ISSUER, "ADMIN", Instant.now(), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Security Hotel",
                                  "address": "Test address"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Security Hotel"));
    }

    /**
     * Проверяет ответ 401 ProblemDetail при отсутствии JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectMissingToken() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("JWT is missing or invalid"))
                .andExpect(jsonPath("$.instance").value("/api/hotels"));
    }

    /**
     * Проверяет ответ 403 при обращении USER к административной операции.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectUserRoleOnAdminEndpoint() throws Exception {
        String token = issueToken(TEST_SECRET, TEST_ISSUER, "USER", Instant.now(), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/hotels")
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
                TEST_SECRET,
                TEST_ISSUER,
                "USER",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );

        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token))
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
        String token = issueToken(
                "forged-hotel-service-secret-with-at-least-32-bytes",
                TEST_ISSUER,
                "USER",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token))
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
        String token = issueToken(TEST_SECRET, "unexpected-issuer", "USER", Instant.now(), Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Проверяет запрет пользовательского JWT на внутреннем эндпойнте.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectUserRoleOnInternalEndpoint() throws Exception {
        String token = issueToken(TEST_SECRET, TEST_ISSUER, "USER", Instant.now(), Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/rooms/1/release")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * Проверяет, что INTERNAL JWT проходит авторизацию внутреннего эндпойнта.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldAllowInternalRoleOnInternalEndpoint() throws Exception {
        String token = issueToken(TEST_SECRET, TEST_ISSUER, "INTERNAL", Instant.now(), Instant.now().plusSeconds(300));

        mockMvc.perform(post("/api/rooms/1/release")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * Выпускает JWT с заданными claims для проверки цепочки безопасности.
     *
     * @param secret секретный ключ HS256
     * @param issuer издатель токена
     * @param role роль владельца токена
     * @param issuedAt время выпуска
     * @param expiresAt время истечения
     * @return подписанный JWT
     */
    private String issueToken(String secret, String issuer, String role, Instant issuedAt, Instant expiresAt) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
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
}
