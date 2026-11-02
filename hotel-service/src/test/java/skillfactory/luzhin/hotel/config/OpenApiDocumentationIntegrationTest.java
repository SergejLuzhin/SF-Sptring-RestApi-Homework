package skillfactory.luzhin.hotel.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет доступность Swagger UI и соответствие OpenAPI контракту Hotel Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Проверяет публичные и административные операции, коды ответов и bearer JWT.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldExposeHotelOpenApiContractWithoutJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Hotel Service API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/hotels'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/hotels'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/hotels/{id}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/hotels/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/rooms'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/rooms'].get.parameters[0].required").value(true))
                .andExpect(jsonPath("$.paths['/api/rooms/recommend'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}'].delete.responses['204']").exists());
    }

    /**
     * Проверяет отдельную маркировку внутренних операций и их прямой сервер без Gateway.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldMarkInternalOperationsAndRequireInternalRole() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/confirm-availability'].post.tags",
                        hasItem("INTERNAL: занятость номеров")))
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/confirm-availability'].post.servers[0].url")
                        .value("http://localhost:8081"))
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/confirm-availability'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/release'].post.tags",
                        hasItem("INTERNAL: занятость номеров")))
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/release'].post.servers[0].url")
                        .value("http://localhost:8081"))
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/release'].post.responses['200']").exists())
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    /**
     * Проверяет описание DTO, форматы дат и допустимые значения перечислений.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldDescribeHotelDtoSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.HotelCreateRequest.required", hasItems("name", "address")))
                .andExpect(jsonPath("$.components.schemas.RoomCreateRequest.required", hasItems("hotelId", "number")))
                .andExpect(jsonPath("$.components.schemas.RoomCreateRequest.properties.available.default").value(true))
                .andExpect(jsonPath("$.components.schemas.ConfirmAvailabilityRequest.properties.startDate.format")
                        .value("date"))
                .andExpect(jsonPath("$.components.schemas.ReleaseReservationRequest.properties.reason.enum",
                        hasItems("COMPENSATION", "USER_CANCELLATION")))
                .andExpect(jsonPath("$.components.schemas.RoomReservationResponse.properties.status.enum",
                        hasItems("HELD", "RELEASED")));
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
