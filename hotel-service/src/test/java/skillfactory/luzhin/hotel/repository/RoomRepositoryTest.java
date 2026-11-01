package skillfactory.luzhin.hotel.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.entity.RoomReservation;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяет запросы доступности и ограничения БД для номеров.
 */
@DataJpaTest
@ActiveProfiles("test")
class RoomRepositoryTest {

    private static final LocalDate EXISTING_START = LocalDate.of(2026, 11, 10);
    private static final LocalDate EXISTING_END = LocalDate.of(2026, 11, 15);
    private static final Sort RECOMMENDATION_SORT = Sort.by(
            Sort.Order.asc("timesBooked"),
            Sort.Order.asc("id")
    );

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository roomReservationRepository;

    private Hotel hotel;
    private Room room;

    /**
     * Создаёт базовые отель и номер перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        hotel = createHotel("Grand Hotel");
        room = createRoom(hotel, "101", true, 0);
        entityManager.flush();
    }

    /**
     * Проверяет запрет одинакового номера комнаты в одном отеле.
     */
    @Test
    void shouldRejectDuplicateRoomNumberWithinSameHotel() {
        Room duplicate = new Room();
        duplicate.setHotel(hotel);
        duplicate.setNumber(room.getNumber());
        duplicate.setAvailable(true);

        assertThatThrownBy(() -> roomRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Проверяет возможность использовать одинаковый номер комнаты в разных отелях.
     */
    @Test
    void shouldAllowSameRoomNumberInDifferentHotels() {
        Hotel anotherHotel = createHotel("Another Hotel");
        Room anotherRoom = new Room();
        anotherRoom.setHotel(anotherHotel);
        anotherRoom.setNumber(room.getNumber());
        anotherRoom.setAvailable(true);

        Room saved = roomRepository.saveAndFlush(anotherRoom);

        assertThat(saved.getId()).isNotNull();
    }

    /**
     * Проверяет уникальность идентификатора бронирования в записях занятости.
     */
    @Test
    void shouldRejectDuplicateBookingId() {
        createReservation(room, 10L, UUID.randomUUID(), EXISTING_START, EXISTING_END, RoomReservationStatus.HELD);
        RoomReservation duplicate = reservation(
                room,
                10L,
                UUID.randomUUID(),
                EXISTING_START.plusDays(1),
                EXISTING_END.plusDays(1),
                RoomReservationStatus.HELD
        );

        assertThatThrownBy(() -> roomReservationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Проверяет уникальность идентификатора запроса в записях занятости.
     */
    @Test
    void shouldRejectDuplicateRequestId() {
        UUID requestId = UUID.randomUUID();
        createReservation(room, 10L, requestId, EXISTING_START, EXISTING_END, RoomReservationStatus.HELD);
        RoomReservation duplicate = reservation(
                room,
                11L,
                requestId,
                EXISTING_START.plusDays(1),
                EXISTING_END.plusDays(1),
                RoomReservationStatus.HELD
        );

        assertThatThrownBy(() -> roomReservationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Проверяет границы полуоткрытого интервала и исключение пересекающихся слотов.
     *
     * @param requestedStart дата начала запрашиваемого интервала
     * @param requestedEnd дата окончания запрашиваемого интервала
     * @param expectedAvailable ожидаемая доступность номера
     */
    @ParameterizedTest
    @CsvSource({
            "2026-11-05, 2026-11-10, true",
            "2026-11-15, 2026-11-20, true",
            "2026-11-05, 2026-11-11, false",
            "2026-11-14, 2026-11-20, false",
            "2026-11-10, 2026-11-15, false",
            "2026-11-11, 2026-11-14, false"
    })
    void shouldApplyHalfOpenIntervalRules(
            String requestedStart,
            String requestedEnd,
            boolean expectedAvailable
    ) {
        createReservation(room, 10L, UUID.randomUUID(), EXISTING_START, EXISTING_END, RoomReservationStatus.HELD);
        entityManager.flush();

        List<Room> result = roomRepository.findAvailableRooms(
                LocalDate.parse(requestedStart),
                LocalDate.parse(requestedEnd),
                Sort.unsorted()
        );

        assertThat(result.contains(room)).isEqualTo(expectedAvailable);
    }

    /**
     * Проверяет исключение выведенного из эксплуатации номера.
     */
    @Test
    void shouldExcludeOperationallyUnavailableRoom() {
        room.setAvailable(false);
        entityManager.flush();

        List<Room> result = roomRepository.findAvailableRooms(
                EXISTING_START,
                EXISTING_END,
                Sort.unsorted()
        );

        assertThat(result).isEmpty();
    }

    /**
     * Проверяет, что освобождённая запись не блокирует номер.
     */
    @Test
    void shouldIgnoreReleasedReservation() {
        createReservation(room, 10L, UUID.randomUUID(), EXISTING_START, EXISTING_END, RoomReservationStatus.RELEASED);
        entityManager.flush();

        List<Room> result = roomRepository.findAvailableRooms(
                EXISTING_START,
                EXISTING_END,
                Sort.unsorted()
        );

        assertThat(result).containsExactly(room);
    }

    /**
     * Проверяет, что занятость одного номера не влияет на другой номер.
     */
    @Test
    void shouldIgnoreReservationOfAnotherRoom() {
        Room occupiedRoom = createRoom(hotel, "102", true, 0);
        createReservation(occupiedRoom, 10L, UUID.randomUUID(), EXISTING_START, EXISTING_END, RoomReservationStatus.HELD);
        entityManager.flush();

        List<Room> result = roomRepository.findAvailableRooms(
                EXISTING_START,
                EXISTING_END,
                Sort.unsorted()
        );

        assertThat(result).containsExactly(room);
    }

    /**
     * Проверяет сортировку рекомендаций по загруженности и идентификатору.
     */
    @Test
    void shouldSortRecommendationsByTimesBookedAndId() {
        Room frequentlyBooked = createRoom(hotel, "102", true, 3);
        Room leastBookedWithLaterId = createRoom(hotel, "103", true, 0);
        entityManager.flush();

        List<Room> result = roomRepository.findAvailableRooms(
                EXISTING_START,
                EXISTING_END,
                RECOMMENDATION_SORT
        );

        assertThat(result).containsExactly(room, leastBookedWithLaterId, frequentlyBooked);
    }

    /**
     * Создаёт и сохраняет отель.
     *
     * @param name название отеля
     * @return сохранённый отель
     */
    private Hotel createHotel(String name) {
        Hotel newHotel = new Hotel();
        newHotel.setName(name);
        newHotel.setAddress("Test address");
        entityManager.persist(newHotel);
        return newHotel;
    }

    /**
     * Создаёт и сохраняет номер.
     *
     * @param owner отель номера
     * @param number номер комнаты
     * @param available эксплуатационная доступность
     * @param timesBooked количество бронирований
     * @return сохранённый номер
     */
    private Room createRoom(Hotel owner, String number, boolean available, long timesBooked) {
        Room newRoom = new Room();
        newRoom.setHotel(owner);
        newRoom.setNumber(number);
        newRoom.setAvailable(available);
        newRoom.setTimesBooked(timesBooked);
        entityManager.persist(newRoom);
        return newRoom;
    }

    /**
     * Создаёт и сохраняет запись занятости.
     *
     * @param reservedRoom номер
     * @param bookingId идентификатор бронирования
     * @param requestId идентификатор запроса
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @param status состояние занятости
     */
    private void createReservation(
            Room reservedRoom,
            Long bookingId,
            UUID requestId,
            LocalDate startDate,
            LocalDate endDate,
            RoomReservationStatus status
    ) {
        entityManager.persist(reservation(reservedRoom, bookingId, requestId, startDate, endDate, status));
    }

    /**
     * Формирует запись занятости без сохранения.
     *
     * @param reservedRoom номер
     * @param bookingId идентификатор бронирования
     * @param requestId идентификатор запроса
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @param status состояние занятости
     * @return новая запись занятости
     */
    private RoomReservation reservation(
            Room reservedRoom,
            Long bookingId,
            UUID requestId,
            LocalDate startDate,
            LocalDate endDate,
            RoomReservationStatus status
    ) {
        RoomReservation reservation = new RoomReservation();
        reservation.setBookingId(bookingId);
        reservation.setRequestId(requestId);
        reservation.setRoom(reservedRoom);
        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setStatus(status);
        reservation.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        return reservation;
    }
}
