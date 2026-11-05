package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.dto.AuthResponse;
import skillfactory.luzhin.booking.dto.CredentialsRequest;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.InvalidCredentialsException;
import skillfactory.luzhin.booking.repository.UserRepository;

/**
 * Реализует регистрацию, проверку учётных данных и вход.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Регистрирует пользователя с ролью USER и выпускает JWT.
     *
     * @param request учётные данные нового пользователя
     * @return выпущенный токен
     */
    @Transactional
    public AuthResponse register(CredentialsRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessConflictException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        User saved = userRepository.save(user);
        return jwtService.issueToken(saved);
    }

    /**
     * Проверяет учётные данные и выпускает JWT.
     *
     * @param request учётные данные пользователя
     * @return выпущенный токен
     */
    public AuthResponse authenticate(CredentialsRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return jwtService.issueToken(user);
    }
}
