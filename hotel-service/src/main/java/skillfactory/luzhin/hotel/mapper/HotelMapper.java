package skillfactory.luzhin.hotel.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import skillfactory.luzhin.hotel.dto.HotelCreateRequest;
import skillfactory.luzhin.hotel.dto.HotelResponse;
import skillfactory.luzhin.hotel.dto.HotelUpdateRequest;
import skillfactory.luzhin.hotel.entity.Hotel;

/**
 * Преобразует данные отеля между DTO и JPA-сущностью.
 */
@Mapper(componentModel = "spring")
public interface HotelMapper {

    /**
     * Создаёт сущность отеля из запроса.
     *
     * @param request данные нового отеля
     * @return сущность отеля
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    Hotel toEntity(HotelCreateRequest request);

    /**
     * Создаёт ответ API из сущности отеля.
     *
     * @param hotel сущность отеля
     * @return DTO отеля
     */
    HotelResponse toResponse(Hotel hotel);

    /**
     * Обновляет только переданные поля сущности отеля.
     *
     * @param request данные обновления
     * @param hotel изменяемая сущность отеля
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    void updateEntity(HotelUpdateRequest request, @MappingTarget Hotel hotel);
}
