package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import skillfactory.luzhin.booking.dto.ConfirmAvailabilityRequest;
import skillfactory.luzhin.booking.dto.HotelRoomResponse;
import skillfactory.luzhin.booking.dto.ReleaseReservationRequest;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.InvalidRequestException;
import skillfactory.luzhin.booking.exception.ResourceNotFoundException;
import skillfactory.luzhin.booking.exception.ServiceUnavailableException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * Выполняет вызовы Hotel Service для подбора, блокировки и освобождения номеров.
 */
@Component
@RequiredArgsConstructor
public class HotelServiceClient {

    private final RestClient hotelServiceRestClient;
    private final InternalJwtService internalJwtService;

    /**
     * Возвращает первый номер из отсортированных рекомендаций Hotel Service.
     *
     * @param userToken JWT пользователя для доступа к публичным рекомендациям
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return идентификатор рекомендованного номера или пустой результат
     */
    public Optional<Long> findRecommendedRoomId(String userToken, LocalDate startDate, LocalDate endDate) {
        try {
            HotelRoomResponse[] rooms = hotelServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/rooms/recommend")
                            .queryParam("startDate", startDate)
                            .queryParam("endDate", endDate)
                            .build())
                    .headers(headers -> headers.setBearerAuth(userToken))
                    .retrieve()
                    .body(HotelRoomResponse[].class);
            if (rooms == null) {
                return Optional.empty();
            }
            return Arrays.stream(rooms)
                    .findFirst()
                    .map(HotelRoomResponse::id);
        } catch (RestClientResponseException exception) {
            throw translateResponseError(exception, null);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    /**
     * Подтверждает доступность номера со служебным INTERNAL JWT.
     *
     * @param roomId идентификатор номера
     * @param request параметры блокировки
     */
    public void confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        try {
            hotelServiceRestClient.post()
                    .uri("/api/rooms/{roomId}/confirm-availability", roomId)
                    .headers(headers -> headers.setBearerAuth(internalJwtService.issueToken()))
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translateResponseError(exception, roomId);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    /**
     * Освобождает блокировку номера со служебным INTERNAL JWT.
     *
     * @param roomId идентификатор номера
     * @param request параметры освобождения
     */
    public void release(Long roomId, ReleaseReservationRequest request) {
        try {
            hotelServiceRestClient.post()
                    .uri("/api/rooms/{roomId}/release", roomId)
                    .headers(headers -> headers.setBearerAuth(internalJwtService.issueToken()))
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translateResponseError(exception, null);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    /**
     * Преобразует HTTP-ошибку Hotel Service в ошибку контракта Booking Service.
     *
     * @param exception исходная HTTP-ошибка
     * @param roomId идентификатор номера для ошибки 404
     * @return ошибка соответствующего бизнес-слоя
     */
    private RuntimeException translateResponseError(RestClientResponseException exception, Long roomId) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 409) {
            return new BusinessConflictException("Room is not available for the requested dates");
        }
        if (status.value() == 404 && roomId != null) {
            return new ResourceNotFoundException("Room", roomId);
        }
        if (status.value() == 400) {
            return new InvalidRequestException("Hotel Service rejected the booking parameters");
        }
        return unavailable();
    }

    /**
     * Создаёт ошибку недоступности распределённой операции.
     *
     * @return ошибка с кодом 503 на уровне REST API
     */
    private ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("Hotel Service is unavailable");
    }
}
