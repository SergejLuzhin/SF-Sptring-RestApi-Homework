package skillfactory.luzhin.booking.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.entity.Booking;

/**
 * Преобразует сущности бронирований в публичные DTO.
 */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    /**
     * Создаёт ответ API с идентификатором владельца.
     *
     * @param booking сущность бронирования
     * @return DTO бронирования
     */
    @Mapping(target = "userId", source = "user.id")
    BookingResponse toResponse(Booking booking);
}
