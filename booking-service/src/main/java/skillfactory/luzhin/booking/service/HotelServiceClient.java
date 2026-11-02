package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import skillfactory.luzhin.booking.config.HotelClientProperties;
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
import java.util.Set;
import java.util.UUID;

/**
 * Выполняет вызовы Hotel Service для подбора, блокировки и освобождения номеров.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HotelServiceClient {

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(500, 502, 503, 504);

    private final RestClient hotelServiceRestClient;
    private final InternalJwtService internalJwtService;
    private final HotelClientProperties properties;

    /**
     * Возвращает первый номер из отсортированных рекомендаций Hotel Service.
     *
     * @param userToken JWT пользователя для доступа к публичным рекомендациям
     * @param requestId идентификатор исходной операции бронирования
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @return идентификатор рекомендованного номера или пустой результат
     */
    public Optional<Long> findRecommendedRoomId(
            String userToken,
            UUID requestId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        try {
            HotelRoomResponse[] rooms = hotelServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/rooms/recommend")
                            .queryParam("startDate", startDate)
                            .queryParam("endDate", endDate)
                            .build())
                    .headers(headers -> {
                        headers.setBearerAuth(userToken);
                        addObservabilityHeaders(headers, requestId, null);
                    })
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
     * Подтверждает доступность номера со служебным INTERNAL JWT и ограниченными повторами.
     *
     * @param roomId идентификатор номера
     * @param request параметры блокировки
     */
    public void confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        executeWithRetries("confirm", properties.confirm(), roomId, () -> hotelServiceRestClient.post()
                .uri("/api/rooms/{roomId}/confirm-availability", roomId)
                .headers(headers -> {
                    headers.setBearerAuth(internalJwtService.issueToken());
                    addObservabilityHeaders(headers, request.requestId(), request.bookingId());
                })
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Освобождает блокировку номера со служебным INTERNAL JWT и отдельными повторами.
     *
     * @param roomId идентификатор номера
     * @param request параметры освобождения
     */
    public void release(Long roomId, ReleaseReservationRequest request) {
        executeWithRetries("release", properties.release(), null, () -> hotelServiceRestClient.post()
                .uri("/api/rooms/{roomId}/release", roomId)
                .headers(headers -> {
                    headers.setBearerAuth(internalJwtService.issueToken());
                    addObservabilityHeaders(headers, request.requestId(), request.bookingId());
                })
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Передаёт correlation ID и идентификаторы операции во внутренний HTTP-запрос.
     *
     * @param headers заголовки исходящего запроса
     * @param requestId идентификатор идемпотентности
     * @param bookingId идентификатор бронирования
     */
    private void addObservabilityHeaders(HttpHeaders headers, UUID requestId, Long bookingId) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            headers.set("X-Correlation-ID", correlationId);
        }
        headers.set("X-Request-ID", requestId.toString());
        if (bookingId != null) {
            headers.set("X-Booking-ID", bookingId.toString());
        }
    }

    /**
     * Повторяет идемпотентный вызов только после временных сбоев.
     *
     * @param operation название распределённой операции
     * @param retry настройки числа попыток и задержек
     * @param roomId идентификатор номера для преобразования ошибки 404
     * @param call идемпотентный HTTP-вызов
     */
    private void executeWithRetries(
            String operation,
            HotelClientProperties.Retry retry,
            Long roomId,
            Runnable call
    ) {
        for (int attempt = 1; ; attempt++) {
            log.info("Hotel {} attempt started; attempt={}/{}", operation, attempt, retry.maxAttempts());
            try {
                call.run();
                log.info("Hotel {} attempt succeeded; attempt={}", operation, attempt);
                return;
            } catch (RestClientResponseException exception) {
                boolean retryable = RETRYABLE_STATUSES.contains(exception.getStatusCode().value());
                if (!retryable || attempt == retry.maxAttempts()) {
                    log.warn(
                            "Hotel {} failed without further retries; attempt={}, status={}",
                            operation,
                            attempt,
                            exception.getStatusCode().value()
                    );
                    throw translateResponseError(exception, roomId);
                }
                log.warn(
                        "Hotel {} will be retried; attempt={}, status={}",
                        operation,
                        attempt,
                        exception.getStatusCode().value()
                );
            } catch (ResourceAccessException exception) {
                if (attempt == retry.maxAttempts()) {
                    log.warn("Hotel {} retries exhausted; attempt={}, reason=network_or_timeout", operation, attempt);
                    throw unavailable();
                }
                log.warn("Hotel {} will be retried; attempt={}, reason=network_or_timeout", operation, attempt);
            } catch (RestClientException exception) {
                log.warn("Hotel {} failed without retry; reason={}", operation, exception.getClass().getSimpleName());
                throw unavailable();
            }
            waitBeforeRetry(retry, attempt);
        }
    }

    /**
     * Выдерживает экспоненциально увеличивающуюся паузу перед следующей попыткой.
     *
     * @param retry настройки задержек
     * @param completedAttempts число уже завершённых попыток
     */
    private void waitBeforeRetry(HotelClientProperties.Retry retry, int completedAttempts) {
        long initialDelay = retry.initialBackoff().toMillis();
        long maximumDelay = retry.maxBackoff().toMillis();
        long delay = initialDelay;
        for (int index = 1; index < completedAttempts && delay < maximumDelay; index++) {
            delay = delay >= maximumDelay / 2 ? maximumDelay : delay * 2;
        }
        try {
            Thread.sleep(Math.min(delay, maximumDelay));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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
