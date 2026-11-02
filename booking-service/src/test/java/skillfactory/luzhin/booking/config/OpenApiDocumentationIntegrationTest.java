package skillfactory.luzhin.booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет доступность Swagger UI и соответствие OpenAPI контракту Booking Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Проверяет публичный доступ к схеме, операции, коды ответов и bearer JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldExposeBookingOpenApiContractWithoutJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Booking Service API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/user/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/user/register'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/user/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/user/auth'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/user'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/user'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['503'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/bookings'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].delete.responses['204']").exists());
    }

    /**
     * Проверяет описание DTO, ограничения полей и отсутствие пароля в ответе пользователя.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldDescribeBookingDtoSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CredentialsRequest.required",
                        hasItems("username", "password")))
                .andExpect(jsonPath("$.components.schemas.CredentialsRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.BookingCreateRequest.required",
                        hasItems("requestId", "startDate", "endDate", "autoSelect")))
                .andExpect(jsonPath("$.components.schemas.BookingCreateRequest.properties.requestId.format")
                        .value("uuid"))
                .andExpect(jsonPath("$.components.schemas.BookingCreateRequest.properties.startDate.format")
                        .value("date"))
                .andExpect(jsonPath("$.components.schemas.BookingResponse.properties.status.enum",
                        hasItems("PENDING", "CONFIRMED", "CANCELLED")))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist());
    }

    /**
     * Проверяет перенаправление на Swagger UI и загрузку HTML-страницы без JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldExposeSwaggerUiWithoutJwt() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Swagger UI")));
    }
}
