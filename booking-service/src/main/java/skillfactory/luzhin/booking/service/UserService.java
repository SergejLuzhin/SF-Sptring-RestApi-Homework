package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.dto.UserCreateRequest;
import skillfactory.luzhin.booking.dto.UserResponse;
import skillfactory.luzhin.booking.dto.UserUpdateRequest;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.ResourceNotFoundException;
import skillfactory.luzhin.booking.mapper.UserMapper;
import skillfactory.luzhin.booking.repository.UserRepository;

/**
 * Реализует административные операции с пользователями.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Создаёт пользователя с указанной администратором ролью.
     *
     * @param request данные пользователя
     * @return созданный пользователь без пароля
     */
    public UserResponse create(UserCreateRequest request) {
        ensureUsernameIsUnique(request.username());
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Обновляет только переданные поля пользователя.
     *
     * @param request данные обновления
     * @return обновлённый пользователь без пароля
     */
    public UserResponse update(UserUpdateRequest request) {
        User user = findById(request.id());
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            ensureUsernameIsUnique(request.username());
            user.setUsername(request.username());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        return userMapper.toResponse(user);
    }

    /**
     * Удаляет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     */
    public void delete(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    /**
     * Находит пользователя или сообщает об его отсутствии.
     *
     * @param id идентификатор пользователя
     * @return сущность пользователя
     */
    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /**
     * Проверяет уникальность имени пользователя.
     *
     * @param username проверяемое имя
     */
    private void ensureUsernameIsUnique(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessConflictException("Username already exists");
        }
    }
}
