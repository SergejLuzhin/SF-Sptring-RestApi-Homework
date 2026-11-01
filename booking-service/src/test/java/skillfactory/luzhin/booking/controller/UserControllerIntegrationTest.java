package skillfactory.luzhin.booking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет административные операции с пользователями и безопасные DTO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Проверяет создание ADMIN, отсутствие пароля в ответе и BCrypt в БД.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCreateAdminUser() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin@example.com",
                                  "password": "adminPassword123",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByUsername("admin@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getPassword()).isNotEqualTo("adminPassword123");
        assertThat(passwordEncoder.matches("adminPassword123", saved.getPassword())).isTrue();
    }

    /**
     * Проверяет конфликт при административном создании дубликата username.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDuplicateUserCreation() throws Exception {
        createUser("admin@example.com", "password123", UserRole.ADMIN);

        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin@example.com",
                                  "password": "anotherPassword123",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Username already exists"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет частичное обновление username и роли с сохранением пароля.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldUpdateProvidedFieldsAndPreservePassword() throws Exception {
        User user = createUser("user@example.com", "password123", UserRole.USER);
        String originalHash = user.getPassword();

        mockMvc.perform(patch("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "username": "updated@example.com",
                                  "role": "ADMIN"
                                }
                                """.formatted(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getPassword()).isEqualTo(originalHash);
        assertThat(passwordEncoder.matches("password123", updated.getPassword())).isTrue();
    }

    /**
     * Проверяет повторное BCrypt-хеширование при смене пароля.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldHashUpdatedPassword() throws Exception {
        User user = createUser("user@example.com", "password123", UserRole.USER);
        String originalHash = user.getPassword();

        mockMvc.perform(patch("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "password": "newPassword123"
                                }
                                """.formatted(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getPassword()).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches("newPassword123", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("password123", updated.getPassword())).isFalse();
    }

    /**
     * Проверяет конфликт при смене username на уже занятое имя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDuplicateUsernameOnUpdate() throws Exception {
        User user = createUser("first@example.com", "password123", UserRole.USER);
        createUser("second@example.com", "password123", UserRole.USER);

        mockMvc.perform(patch("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "username": "second@example.com"
                                }
                                """.formatted(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Username already exists"));
    }

    /**
     * Проверяет удаление пользователя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldDeleteUser() throws Exception {
        User user = createUser("user@example.com", "password123", UserRole.USER);

        mockMvc.perform(delete("/user").param("id", user.getId().toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(userRepository.existsById(user.getId())).isFalse();
    }

    /**
     * Проверяет ответ 404 при обновлении отсутствующего пользователя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnNotFoundOnUpdateOfMissingUser() throws Exception {
        mockMvc.perform(patch("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 999999,
                                  "username": "missing@example.com"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User with id 999999 was not found"));
    }

    /**
     * Проверяет ответ 404 при удалении отсутствующего пользователя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnNotFoundOnDeleteOfMissingUser() throws Exception {
        mockMvc.perform(delete("/user").param("id", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User with id 999999 was not found"));
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
}
