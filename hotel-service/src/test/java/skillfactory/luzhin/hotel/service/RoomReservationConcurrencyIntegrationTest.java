package skillfactory.luzhin.hotel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import skillfactory.luzhin.hotel.dto.ConfirmAvailabilityRequest;
import skillfactory.luzhin.hotel.dto.ReleaseReason;
import skillfactory.luzhin.hotel.dto.ReleaseReservationRequest;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;
import skillfactory.luzhin.hotel.exception.BusinessConflictException;
import skillfactory.luzhin.hotel.repository.HotelRepository;
import skillfactory.luzhin.hotel.repository.RoomRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

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

/**
 * Проверяет идемпотентность и блокировки номера на реальных конкурентных транзакциях H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomReservationConcurrencyIntegrationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 15);

    @Autowired
    private RoomReservationService roomReservationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository roomReservationRepository;

    private ExecutorService executorService;
    private Room room;

    /**
     * Очищает базу и создаёт общий номер и пул потоков для конкурентных вызовов.
     */
    @BeforeEach
    void setUp() {
        roomReservationRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        Hotel hotel = new Hotel();
        hotel.setName("Grand Hotel");
        hotel.setAddress("Test address");
        hotelRepository.saveAndFlush(hotel);

        Room newRoom = new Room();
        newRoom.setHotel(hotel);
        newRoom.setNumber("101");
        newRoom.setAvailable(true);
        newRoom.setTimesBooked(0);
        room = roomRepository.saveAndFlush(newRoom);
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
     * Проверяет единственную активную блокировку при параллельных пересекающихся датах.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldAllowOnlyOneOverlappingConfirmation() throws Exception {
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Callable<RoomReservationStatus>> tasks = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            long bookingId = index + 1L;
            ConfirmAvailabilityRequest request = new ConfirmAvailabilityRequest(
                    bookingId,
                    UUID.randomUUID(),
                    START_DATE,
                    END_DATE
            );
            tasks.add(confirmationTask(barrier, request));
        }

        List<RoomReservationStatus> results = execute(tasks);

        assertThat(results).containsOnlyOnce(RoomReservationStatus.HELD);
        assertThat(results.stream().filter(result -> result == null)).hasSize(threadCount - 1);
        assertThat(roomReservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    /**
     * Проверяет параллельное подтверждение соседних непересекающихся интервалов.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldAllowNonOverlappingConfirmations() throws Exception {
        List<LocalDate[]> intervals = List.of(
                new LocalDate[]{LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)},
                new LocalDate[]{LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 15)},
                new LocalDate[]{LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20)},
                new LocalDate[]{LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25)}
        );
        CyclicBarrier barrier = new CyclicBarrier(intervals.size());
        List<Callable<RoomReservationStatus>> tasks = new ArrayList<>();
        for (int index = 0; index < intervals.size(); index++) {
            LocalDate[] interval = intervals.get(index);
            tasks.add(confirmationTask(barrier, new ConfirmAvailabilityRequest(
                    index + 1L,
                    UUID.randomUUID(),
                    interval[0],
                    interval[1]
            )));
        }

        List<RoomReservationStatus> results = execute(tasks);

        assertThat(results).containsOnly(RoomReservationStatus.HELD);
        assertThat(roomReservationRepository.count()).isEqualTo(intervals.size());
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                .isEqualTo(intervals.size());
    }

    /**
     * Проверяет возврат одной операции при параллельных одинаковых confirm.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldConfirmSameOperationOnlyOnce() throws Exception {
        int threadCount = 8;
        UUID requestId = UUID.randomUUID();
        ConfirmAvailabilityRequest request = new ConfirmAvailabilityRequest(
                25L,
                requestId,
                START_DATE,
                END_DATE
        );
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Callable<RoomReservationStatus>> tasks = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            tasks.add(confirmationTask(barrier, request));
        }

        List<RoomReservationStatus> results = execute(tasks);

        assertThat(results).containsOnly(RoomReservationStatus.HELD);
        assertThat(roomReservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    /**
     * Проверяет однократную компенсацию при параллельных release и запрет воскрешения операции.
     *
     * @throws Exception ошибка синхронизации или выполнения потоков
     */
    @Test
    void shouldReleaseSameOperationOnlyOnceAndNotResurrectIt() throws Exception {
        UUID requestId = UUID.randomUUID();
        ConfirmAvailabilityRequest confirmation = new ConfirmAvailabilityRequest(
                25L,
                requestId,
                START_DATE,
                END_DATE
        );
        roomReservationService.confirmAvailability(room.getId(), confirmation);
        ReleaseReservationRequest release = new ReleaseReservationRequest(
                25L,
                requestId,
                ReleaseReason.COMPENSATION
        );
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Callable<RoomReservationStatus>> tasks = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return roomReservationService.release(room.getId(), release).status();
            });
        }

        List<RoomReservationStatus> results = execute(tasks);
        RoomReservationStatus repeatedConfirmation = roomReservationService
                .confirmAvailability(room.getId(), confirmation)
                .status();

        assertThat(results).containsOnly(RoomReservationStatus.RELEASED);
        assertThat(repeatedConfirmation).isEqualTo(RoomReservationStatus.RELEASED);
        assertThat(roomReservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isZero();
    }

    /**
     * Создаёт задание confirm, преобразующее бизнес-конфликт в отсутствие результата.
     *
     * @param barrier синхронизатор одновременного старта
     * @param request параметры подтверждения
     * @return задание для пула потоков
     */
    private Callable<RoomReservationStatus> confirmationTask(
            CyclicBarrier barrier,
            ConfirmAvailabilityRequest request
    ) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                return roomReservationService.confirmAvailability(room.getId(), request).status();
            } catch (BusinessConflictException exception) {
                return null;
            }
        };
    }

    /**
     * Выполняет задания одновременно и собирает результаты всех потоков.
     *
     * @param tasks задания подтверждения или освобождения
     * @return результаты операций
     * @throws Exception ошибка выполнения потоков
     */
    private List<RoomReservationStatus> execute(List<Callable<RoomReservationStatus>> tasks) throws Exception {
        List<Future<RoomReservationStatus>> futures = executorService.invokeAll(tasks);
        List<RoomReservationStatus> results = new ArrayList<>();
        for (Future<RoomReservationStatus> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        return results;
    }
}
