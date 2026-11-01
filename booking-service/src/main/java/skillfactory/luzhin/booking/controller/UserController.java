package skillfactory.luzhin.booking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Регистрирует пользователя с ролью USER.
     *
     * @param request учётные данные нового пользователя
     * @return JWT на один час
     */
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
    @PatchMapping
    public UserResponse update(@Valid @RequestBody UserUpdateRequest request) {
        return userService.update(request);
    }

    /**
     * Удаляет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam @Positive Long id) {
        userService.delete(id);
    }
}
