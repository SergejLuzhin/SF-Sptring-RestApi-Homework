package skillfactory.luzhin.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skillfactory.luzhin.booking.dto.AuthResponse;
import skillfactory.luzhin.booking.dto.CredentialsRequest;
import skillfactory.luzhin.booking.dto.UserCreateRequest;
import skillfactory.luzhin.booking.dto.UserResponse;
import skillfactory.luzhin.booking.dto.UserUpdateRequest;
import skillfactory.luzhin.booking.service.AuthService;
import skillfactory.luzhin.booking.service.UserService;

/**
 * REST-контроллер регистрации, входа и администрирования пользователей.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
@Tag(name = "Пользователи", description = "Публичная аутентификация и администрирование пользователей")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Регистрирует пользователя с ролью USER.
     *
     * @param request учётные данные нового пользователя
     * @return JWT на один час
     */
    @Operation(summary = "Зарегистрировать пользователя", description = "Открытая операция; всегда создаёт роль USER.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан, JWT выпущен на один час"),
            @ApiResponse(responseCode = "400", description = "Некорректные учётные данные",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Имя пользователя уже занято",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody CredentialsRequest request) {
        return authService.register(request);
    }

    /**
     * Аутентифицирует пользователя по имени и паролю.
     *
     * @param request учётные данные пользователя
     * @return JWT на один час
     */
    @Operation(summary = "Войти и получить JWT", description = "Открытая операция; JWT действителен один час.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Учётные данные приняты, JWT выпущен"),
            @ApiResponse(responseCode = "400", description = "Некорректное тело запроса",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Неверные имя пользователя или пароль",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/auth")
    public AuthResponse authenticate(@Valid @RequestBody CredentialsRequest request) {
        return authService.authenticate(request);
    }

    /**
     * Создаёт пользователя с ролью, указанной администратором.
     *
     * @param request данные нового пользователя
     * @return созданный пользователь без пароля
     */
    @Operation(summary = "Создать пользователя", description = "Требуется роль ADMIN; допустимые роли USER и ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные пользователя",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Имя пользователя уже занято",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    /**
     * Обновляет переданные поля пользователя.
     *
     * @param request данные обновления
     * @return обновлённый пользователь без пароля
     */
    @Operation(summary = "Обновить пользователя", description = "Требуется роль ADMIN; обязателен только id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь обновлён"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные пользователя",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Имя пользователя уже занято",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping
    public UserResponse update(@Valid @RequestBody UserUpdateRequest request) {
        return userService.update(request);
    }

    /**
     * Удаляет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     */
    @Operation(
            summary = "Удалить пользователя",
            description = "Требуется роль ADMIN. Пользователь с историей бронирований не удаляется."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь удалён", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный идентификатор",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Удаление нарушит целостность истории бронирований",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Идентификатор пользователя", example = "2")
            @RequestParam @Positive Long id
    ) {
        userService.delete(id);
    }
}
