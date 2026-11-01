package skillfactory.luzhin.hotel.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import skillfactory.luzhin.hotel.dto.RoomCreateRequest;
import skillfactory.luzhin.hotel.dto.RoomResponse;
import skillfactory.luzhin.hotel.dto.RoomUpdateRequest;
import skillfactory.luzhin.hotel.entity.Room;

/**
 * Преобразует данные номера между DTO и JPA-сущностью.
 */
@Mapper(componentModel = "spring")
public interface RoomMapper {

    /**
     * Создаёт сущность номера из запроса.
     *
     * @param request данные нового номера
     * @return сущность номера
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    @Mapping(target = "available", source = "available", defaultValue = "true")
    Room toEntity(RoomCreateRequest request);

    /**
     * Создаёт ответ API из сущности номера.
     *
     * @param room сущность номера
     * @return DTO номера
     */
    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);

    /**
     * Обновляет только переданные поля сущности номера.
     *
     * @param request данные обновления
     * @param room изменяемая сущность номера
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    void updateEntity(RoomUpdateRequest request, @MappingTarget Room room);
}
