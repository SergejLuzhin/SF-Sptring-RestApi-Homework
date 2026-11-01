package skillfactory.luzhin.booking.mapper;

import org.mapstruct.Mapper;
import skillfactory.luzhin.booking.dto.UserResponse;
import skillfactory.luzhin.booking.entity.User;

/**
 * Преобразует данные пользователя в безопасные DTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Создаёт ответ API без пароля и его хеша.
     *
     * @param user сущность пользователя
     * @return DTO пользователя
     */
    UserResponse toResponse(User user);
}
