package skillfactory.luzhin.booking.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import skillfactory.luzhin.booking.config.HotelClientProperties;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.entity.BookingStatus;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.ServiceUnavailableException;
import skillfactory.luzhin.booking.repository.BookingRepository;
import skillfactory.luzhin.booking.repository.UserRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяет реальные HTTP-тайм-ауты, повторы и компенсацию через локальную заглушку Hotel Service.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
class BookingHttpFailureIntegrationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 15);
    private static final String CONFIRM_PATH = "/api/rooms/10/confirm-availability";
    private static final String RELEASE_PATH = "/api/rooms/10/release";

    @Autowired
    private BookingPersistenceService bookingPersistenceService;

    @Autowired
    private InternalJwtService internalJwtService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private BookingService bookingService;
    private User user;
    private AtomicInteger confirmCalls;
    private AtomicInteger releaseCalls;

    /**
     * Создаёт пользователя, HTTP-заглушку и счётчики вызовов на чистой базе.
     *
     * @throws IOException ошибка запуска локального HTTP-сервера
     */
    @BeforeEach
    void setUp() throws IOException {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        User newUser = new User();
        newUser.setUsername("user@example.com");
        newUser.setPassword(passwordEncoder.encode("password123"));
        newUser.setRole(UserRole.USER);
        user = userRepository.saveAndFlush(newUser);

        confirmCalls = new AtomicInteger();
        releaseCalls = new AtomicInteger();
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpExecutor = Executors.newCachedThreadPool();
        httpServer.setExecutor(httpExecutor);
    }

    /**
     * Останавливает HTTP-заглушку и её потоки.
     *
     * @throws InterruptedException ошибка ожидания завершения потоков
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            assertThat(httpExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Проверяет успешный confirm после временных ответов 503 в пределах лимита.
     */
    @Test
    void shouldConfirmAfterTemporaryFailuresWithinRetryLimit(CapturedOutput output) {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> respond(exchange, confirmCalls.incrementAndGet() < 3 ? 503 : 200),
                exchange -> respond(exchange, 200),
                3,
                1,
                Duration.ofSeconds(1)
        );

        MDC.put("correlationId", "distributed-success");
        BookingCreationResult result;
        try {
            result = createBooking(requestId);
        } finally {
            MDC.clear();
        }

        assertThat(result.response().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmCalls).hasValue(3);
        assertThat(releaseCalls).hasValue(0);
        assertThat(output)
                .contains("correlationId=distributed-success")
                .contains("requestId=" + requestId)
                .contains("bookingId=" + result.response().id())
                .contains("Booking PENDING created")
                .contains("Hotel confirm will be retried")
                .contains("Booking CONFIRMED");
    }

    /**
     * Проверяет отсутствие повторов бизнес-конфликта и успешную компенсацию.
     */
    @Test
    void shouldNotRetryBusinessConflictAndShouldCompensate() {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> {
                    confirmCalls.incrementAndGet();
                    respond(exchange, 409);
                },
                exchange -> {
                    releaseCalls.incrementAndGet();
                    respond(exchange, 200);
                },
                3,
                1,
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> createBooking(requestId)).isInstanceOf(BusinessConflictException.class);

        assertThat(confirmCalls).hasValue(1);
        assertThat(releaseCalls).hasValue(1);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет отсутствие повторов ошибки авторизации и отмену PENDING после release.
     */
    @Test
    void shouldNotRetryAuthorizationFailure() {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> {
                    confirmCalls.incrementAndGet();
                    respond(exchange, 403);
                },
                exchange -> {
                    releaseCalls.incrementAndGet();
                    respond(exchange, 200);
                },
                3,
                1,
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> createBooking(requestId)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(confirmCalls).hasValue(1);
        assertThat(releaseCalls).hasValue(1);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет отдельные лимиты повторов confirm и release при исчерпании временных ошибок.
     */
    @Test
    void shouldExhaustConfirmRetriesAndRetryReleaseSeparately() {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> {
                    confirmCalls.incrementAndGet();
                    respond(exchange, 503);
                },
                exchange -> {
                    respond(exchange, releaseCalls.incrementAndGet() == 1 ? 503 : 200);
                },
                2,
                2,
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> createBooking(requestId)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(confirmCalls).hasValue(2);
        assertThat(releaseCalls).hasValue(2);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет release после read timeout, когда удалённая блокировка могла быть установлена.
     */
    @Test
    void shouldReleaseAfterConfirmReadTimeoutWithUnknownResult() {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> {
                    confirmCalls.incrementAndGet();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    respond(exchange, 200);
                },
                exchange -> {
                    releaseCalls.incrementAndGet();
                    respond(exchange, 200);
                },
                1,
                1,
                Duration.ofMillis(50)
        );

        assertThatThrownBy(() -> createBooking(requestId)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(confirmCalls).hasValue(1);
        assertThat(releaseCalls).hasValue(1);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет видимую ошибку и сохранение PENDING после исчерпания повторов release.
     */
    @Test
    void shouldExposeCompensationFailureAfterReleaseRetries(CapturedOutput output) {
        UUID requestId = UUID.randomUUID();
        startClient(
                exchange -> {
                    confirmCalls.incrementAndGet();
                    respond(exchange, 503);
                },
                exchange -> {
                    releaseCalls.incrementAndGet();
                    respond(exchange, 503);
                },
                1,
                3,
                Duration.ofSeconds(1)
        );

        MDC.put("correlationId", "distributed-compensation-failure");
        try {
            assertThatThrownBy(() -> createBooking(requestId))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("compensation failed");
        } finally {
            MDC.clear();
        }

        assertThat(confirmCalls).hasValue(1);
        assertThat(releaseCalls).hasValue(3);
        Long bookingId = bookingRepository.findByRequestId(requestId).orElseThrow().getId();
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        assertThat(output)
                .contains("correlationId=distributed-compensation-failure")
                .contains("requestId=" + requestId)
                .contains("bookingId=" + bookingId)
                .contains("Hotel release failed without further retries")
                .contains("Booking compensation failed");
    }

    /**
     * Запускает HTTP-заглушку и создаёт Booking Service с реальными RestClient и транзакциями.
     *
     * @param confirmHandler обработчик confirm-availability
     * @param releaseHandler обработчик release
     * @param confirmAttempts общее число попыток confirm
     * @param releaseAttempts общее число попыток release
     * @param readTimeout предельное время ожидания ответа
     */
    private void startClient(
            HttpHandler confirmHandler,
            HttpHandler releaseHandler,
            int confirmAttempts,
            int releaseAttempts,
            Duration readTimeout
    ) {
        httpServer.createContext(CONFIRM_PATH, confirmHandler);
        httpServer.createContext(RELEASE_PATH, releaseHandler);
        httpServer.start();
        String baseUrl = "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort();
        HotelClientProperties properties = new HotelClientProperties(
                baseUrl,
                Duration.ofMillis(200),
                readTimeout,
                new HotelClientProperties.Retry(confirmAttempts, Duration.ofMillis(1), Duration.ofMillis(2)),
                new HotelClientProperties.Retry(releaseAttempts, Duration.ofMillis(1), Duration.ofMillis(2))
        );
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        HotelServiceClient hotelServiceClient = new HotelServiceClient(restClient, internalJwtService, properties);
        bookingService = new BookingService(bookingPersistenceService, hotelServiceClient);
    }

    /**
     * Вызывает создание брони с ручным выбором номера через реальный координатор.
     *
     * @param requestId идентификатор идемпотентности
     * @return результат создания брони
     */
    private BookingCreationResult createBooking(UUID requestId) {
        return bookingService.create(
                user.getId().toString(),
                "user-token",
                new BookingCreateRequest(requestId, 10L, START_DATE, END_DATE, false)
        );
    }

    /**
     * Отправляет JSON-ответ заглушки с указанным HTTP-статусом.
     *
     * @param exchange текущий HTTP-обмен
     * @param status HTTP-статус ответа
     * @throws IOException ошибка отправки ответа
     */
    private void respond(HttpExchange exchange, int status) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        } finally {
            exchange.close();
        }
    }
}
