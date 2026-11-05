package skillfactory.luzhin.booking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.repository.UserRepository;

/**
 * Создаёт начального администратора из внешней конфигурации.
 */
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Создаёт ADMIN, если заданы оба параметра и имя ещё не занято.
     *
     * @param args аргументы запуска приложения
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = adminProperties.username();
        String password = adminProperties.password();
        if ((username == null || username.isBlank()) && (password == null || password.isBlank())) {
            return;
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Both admin username and password must be configured");
        }
        if (password.length() < 8) {
            throw new IllegalStateException("Admin password must contain at least 8 characters");
        }
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }
}
