package skillfactory.luzhin.hotel.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.entity.RoomReservation;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;
import skillfactory.luzhin.hotel.repository.HotelRepository;
import skillfactory.luzhin.hotel.repository.RoomRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет REST API номеров, доступность и рекомендации вместе с H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomControllerIntegrationTest {

    private static final String DATE_PARAMETERS = "startDate=2026-11-10&endDate=2026-11-15";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository roomReservationRepository;

    private Hotel hotel;

    /**
     * Создаёт отель перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        Hotel newHotel = new Hotel();
        newHotel.setName("Grand Hotel");
        newHotel.setAddress("Test address");
        hotel = hotelRepository.saveAndFlush(newHotel);
    }

    /**
     * Проверяет создание номера и значения по умолчанию.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCreateRoomWithDefaultAvailability() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "101"
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.number").value("101"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));

        assertThat(roomRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет ответ 404 при создании номера в отсутствующем отеле.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnNotFoundWhenHotelDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": 999999,
                                  "number": "101"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Hotel")));
    }

    /**
     * Проверяет ответ 409 при дублировании номера комнаты в отеле.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDuplicateRoomNumber() throws Exception {
        createRoom("101", true, 0);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "101"
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Room number already exists in this hotel"));
    }

    /**
     * Проверяет Bean Validation обязательных полей номера.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectInvalidRoomFields() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": -1,
                                  "number": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("hotelId")))
                .andExpect(jsonPath("$.detail").value(containsString("number")));
    }

    /**
     * Проверяет исключение номера из поиска после отключения доступности через PATCH.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldFilterOperationallyUnavailableRoomAfterPatch() throws Exception {
        Room available = createRoom("101", true, 0);
        Room unavailable = createRoom("102", true, 0);

        mockMvc.perform(patch("/api/rooms/{id}", unavailable.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "available": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.number").value("102"));

        mockMvc.perform(get("/api/rooms?" + DATE_PARAMETERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(available.getId()));
    }

    /**
     * Проверяет порядок рекомендаций по загруженности и идентификатору.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnSortedRecommendations() throws Exception {
        Room first = createRoom("101", true, 0);
        Room frequentlyBooked = createRoom("102", true, 3);
        Room leastBookedWithLaterId = createRoom("103", true, 0);

        mockMvc.perform(get("/api/rooms/recommend?" + DATE_PARAMETERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(leastBookedWithLaterId.getId()))
                .andExpect(jsonPath("$[2].id").value(frequentlyBooked.getId()));
    }

    /**
     * Проверяет, что активная запись скрывает номер, а освобождённая — нет.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldExcludeHeldAndIncludeReleasedRoom() throws Exception {
        Room held = createRoom("101", true, 0);
        Room released = createRoom("102", true, 0);
        createReservation(held, 10L, RoomReservationStatus.HELD);
        createReservation(released, 11L, RoomReservationStatus.RELEASED);

        mockMvc.perform(get("/api/rooms?" + DATE_PARAMETERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(released.getId()));
    }

    /**
     * Проверяет частичное обновление номера без изменения остальных полей.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldUpdateOnlyProvidedRoomFields() throws Exception {
        Room room = createRoom("101", false, 3);

        mockMvc.perform(patch("/api/rooms/{id}", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": "201"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("201"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.timesBooked").value(3))
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()));
    }

    /**
     * Проверяет удаление номера без записей занятости.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldDeleteRoomWithoutReservationHistory() throws Exception {
        Room room = createRoom("101", true, 0);

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(roomRepository.existsById(room.getId())).isFalse();
    }

    /**
     * Проверяет запрет удаления номера с активной или исторической занятостью.
     *
     * @param reservationStatus состояние записи занятости
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @ParameterizedTest
    @EnumSource(RoomReservationStatus.class)
    void shouldRejectDeletionOfRoomWithReservationHistory(RoomReservationStatus reservationStatus) throws Exception {
        Room room = createRoom("101", true, 0);
        createReservation(room, 10L, reservationStatus);

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("reservation history")));
    }

    /**
     * Проверяет ответ 400 на равные или обратные даты.
     *
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @ParameterizedTest
    @CsvSource({
            "2026-11-10, 2026-11-10",
            "2026-11-15, 2026-11-10"
    })
    void shouldRejectInvalidDateInterval(String startDate, String endDate) throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("startDate must be earlier than endDate"));
    }

    /**
     * Проверяет ответ 400 на дату в неподдерживаемом формате.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectMalformedDate() throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .param("startDate", "2026/11/10")
                        .param("endDate", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Проверяет ответ 400 при отсутствии обязательной даты выезда.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectMissingDateParameter() throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .param("startDate", "2026-11-10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Проверяет ответ 404 при обновлении отсутствующего номера.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnNotFoundForMissingRoom() throws Exception {
        mockMvc.perform(patch("/api/rooms/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "available": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Room")));
    }

    /**
     * Создаёт и сохраняет тестовый номер.
     *
     * @param number номер комнаты
     * @param available эксплуатационная доступность
     * @param timesBooked количество бронирований
     * @return сохранённый номер
     */
    private Room createRoom(String number, boolean available, long timesBooked) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setAvailable(available);
        room.setTimesBooked(timesBooked);
        return roomRepository.saveAndFlush(room);
    }

    /**
     * Создаёт и сохраняет запись занятости на проверяемые даты.
     *
     * @param room номер
     * @param bookingId идентификатор бронирования
     * @param status состояние записи
     */
    private void createReservation(Room room, Long bookingId, RoomReservationStatus status) {
        RoomReservation reservation = new RoomReservation();
        reservation.setBookingId(bookingId);
        reservation.setRequestId(UUID.randomUUID());
        reservation.setRoom(room);
        reservation.setStartDate(LocalDate.of(2026, 11, 10));
        reservation.setEndDate(LocalDate.of(2026, 11, 15));
        reservation.setStatus(status);
        reservation.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        roomReservationRepository.saveAndFlush(reservation);
    }
}
