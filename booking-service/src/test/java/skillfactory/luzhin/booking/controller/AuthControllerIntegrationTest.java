package skillfactory.luzhin.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.dto.AuthResponse;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет регистрацию, вход, BCrypt и выпуск JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Проверяет регистрацию только с ролью USER, хеширование и claims JWT.
     *
     * @throws Exception ошибка HTTP-запроса или разбора JWT
     */
    @Test
    void shouldRegisterUserWithUserRoleAndIssueJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user@example.com",
                                  "password": "password123",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        User saved = userRepository.findByUsername("user@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        assertToken(readAuthResponse(result), saved);
    }

    /**
     * Проверяет отказ повторной регистрации существующего username.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDuplicateRegistration() throws Exception {
        createUser("user@example.com", "password123", UserRole.USER);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user@example.com",
                                  "password": "anotherPassword123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Username already exists"))
                .andExpect(jsonPath("$.instance").value("/user/register"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет успешный вход и выпуск JWT с данными пользователя.
     *
     * @throws Exception ошибка HTTP-запроса или разбора JWT
     */
    @Test
    void shouldAuthenticateAndIssueJwt() throws Exception {
        User user = createUser("user@example.com", "password123", UserRole.USER);

        MvcResult result = mockMvc.perform(post("/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        assertToken(readAuthResponse(result), user);
    }

    /**
     * Проверяет одинаковый ответ 401 для неверного пароля и отсутствующего пользователя.
     *
     * @param username имя пользователя
     * @param password пароль
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @ParameterizedTest
    @CsvSource({
            "user@example.com, wrongPassword123",
            "unknown@example.com, password123"
    })
    void shouldRejectInvalidCredentials(String username, String password) throws Exception {
        createUser("user@example.com", "password123", UserRole.USER);

        mockMvc.perform(post("/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.instance").value("/user/auth"));
    }

    /**
     * Проверяет отказ регистрации с паролем короче восьми символов.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password")));

        assertThat(userRepository.count()).isZero();
    }

    /**
     * Создаёт пользователя с BCrypt-хешем.
     *
     * @param username имя пользователя
     * @param password исходный пароль
     * @param role роль пользователя
     * @return сохранённый пользователь
     */
    private User createUser(String username, String password, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    /**
     * Читает DTO токена из HTTP-ответа.
     *
     * @param result результат MockMvc
     * @return DTO токена
     * @throws Exception ошибка разбора JSON
     */
    private AuthResponse readAuthResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    /**
     * Проверяет алгоритм, срок действия и пользовательские claims JWT.
     *
     * @param response ответ с токеном
     * @param user пользователь
     * @throws Exception ошибка разбора JWT
     */
    private void assertToken(AuthResponse response, User user) throws Exception {
        SignedJWT jwt = SignedJWT.parse(response.accessToken());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(claims.getIssuer()).isEqualTo("booking-service-test");
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("username")).isEqualTo(user.getUsername());
        assertThat(claims.getStringClaim("role")).isEqualTo(user.getRole().name());
        assertThat(claims.getExpirationTime().toInstant().getEpochSecond()
                - claims.getIssueTime().toInstant().getEpochSecond()).isEqualTo(3600);
        assertThat(claims.getClaim("password")).isNull();
    }
}
