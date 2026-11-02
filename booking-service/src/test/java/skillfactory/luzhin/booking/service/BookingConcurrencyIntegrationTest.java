package skillfactory.luzhin.booking.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.entity.BookingStatus;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.repository.BookingRepository;
import skillfactory.luzhin.booking.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Проверяет конкурентную идемпотентность и условные переходы бронирований на H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 15);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingPersistenceService bookingPersistenceService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private HotelServiceClient hotelServiceClient;

    private ExecutorService executorService;
    private User user;

    /**
     * Создаёт владельца бронирований и пул потоков на чистой базе.
     */
    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        User newUser = new User();
        newUser.setUsername("user@example.com");
        newUser.setPassword(passwordEncoder.encode("password123"));
        newUser.setRole(UserRole.USER);
        user = userRepository.saveAndFlush(newUser);
        executorService = Executors.newFixedThreadPool(8);
    }

    /**
     * Останавливает пул потоков после каждого теста.
     *
     * @throws InterruptedException ошибка ожидания завершения потоков
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        executorService.shutdownNow();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Проверяет создание одной брони и один удалённый confirm при параллельных requestId.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldCreateOnlyOneBookingForParallelSameRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        BookingCreateRequest request = request(requestId, 10L);
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Callable<BookingCreationResult>> tasks = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return bookingService.create(user.getId().toString(), "user-token", request);
            });
        }

        List<Future<BookingCreationResult>> futures = executorService.invokeAll(tasks);
        List<BookingCreationResult> results = new ArrayList<>();
        for (Future<BookingCreationResult> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(result -> result.response().id()).containsOnly(results.get(0).response().id());
        assertThat(results).filteredOn(BookingCreationResult::created).hasSize(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        verify(hotelServiceClient, times(1)).confirmAvailability(eq(10L), any());
    }

    /**
     * Проверяет конфликт параллельного requestId с другими параметрами без второй брони.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldRejectParallelSameRequestIdWithDifferentParameters() throws Exception {
        UUID requestId = UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<BookingCreationResult>> tasks = List.of(
                conflictingCreationTask(barrier, request(requestId, 10L)),
                conflictingCreationTask(barrier, request(requestId, 11L))
        );

        List<Future<BookingCreationResult>> futures = executorService.invokeAll(tasks);
        List<BookingCreationResult> results = new ArrayList<>();
        for (Future<BookingCreationResult> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }

        assertThat(results).containsExactlyInAnyOrder(null, results.stream().filter(result -> result != null).findFirst().orElse(null));
        assertThat(results.stream().filter(result -> result == null)).hasSize(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        verify(hotelServiceClient, times(1)).confirmAvailability(any(), any());
    }

    /**
     * Проверяет, что гонка подтверждения и компенсации оставляет один терминальный статус.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldNotRegressTerminalStatusDuringConcurrentTransitions() throws Exception {
        BookingCreateRequest request = request(UUID.randomUUID(), 10L);
        BookingResponse pending = bookingPersistenceService.createPending(user.getId(), request, 10L);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<BookingStatus>> tasks = List.of(
                () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        return bookingPersistenceService.confirm(pending.id()).status();
                    } catch (BusinessConflictException exception) {
                        return null;
                    }
                },
                () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        bookingPersistenceService.cancelAfterCompensation(pending.id());
                        return BookingStatus.CANCELLED;
                    } catch (BusinessConflictException exception) {
                        return null;
                    }
                }
        );

        List<Future<BookingStatus>> futures = executorService.invokeAll(tasks);
        List<BookingStatus> results = new ArrayList<>();
        for (Future<BookingStatus> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }

        BookingStatus finalStatus = bookingRepository.findById(pending.id()).orElseThrow().getStatus();
        assertThat(finalStatus).isIn(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
        assertThat(results).contains(finalStatus);
        assertThat(results.stream().filter(result -> result == null)).hasSize(1);
        if (finalStatus == BookingStatus.CONFIRMED) {
            assertThatThrownBy(() -> bookingPersistenceService.cancelAfterCompensation(pending.id()))
                    .isInstanceOf(BusinessConflictException.class);
            assertThat(bookingPersistenceService.confirm(pending.id()).status()).isEqualTo(BookingStatus.CONFIRMED);
        } else {
            assertThatThrownBy(() -> bookingPersistenceService.confirm(pending.id()))
                    .isInstanceOf(BusinessConflictException.class);
            bookingPersistenceService.cancelAfterCompensation(pending.id());
        }
        assertThat(bookingRepository.findById(pending.id()).orElseThrow().getStatus()).isEqualTo(finalStatus);
    }

    /**
     * Создаёт задание, преобразующее конфликт requestId в отсутствие результата.
     *
     * @param barrier синхронизатор одновременного старта
     * @param request параметры бронирования
     * @return задание создания брони
     */
    private Callable<BookingCreationResult> conflictingCreationTask(
            CyclicBarrier barrier,
            BookingCreateRequest request
    ) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                return bookingService.create(user.getId().toString(), "user-token", request);
            } catch (BusinessConflictException exception) {
                return null;
            }
        };
    }

    /**
     * Создаёт запрос с ручным выбором номера.
     *
     * @param requestId идентификатор идемпотентности
     * @param roomId идентификатор номера
     * @return DTO запроса
     */
    private BookingCreateRequest request(UUID requestId, Long roomId) {
        return new BookingCreateRequest(requestId, roomId, START_DATE, END_DATE, false);
    }
}
