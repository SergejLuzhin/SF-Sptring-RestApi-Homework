package skillfactory.luzhin.hotel.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.hotel.entity.Hotel;
import skillfactory.luzhin.hotel.entity.Room;
import skillfactory.luzhin.hotel.entity.RoomReservationStatus;
import skillfactory.luzhin.hotel.repository.HotelRepository;
import skillfactory.luzhin.hotel.repository.RoomRepository;
import skillfactory.luzhin.hotel.repository.RoomReservationRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет внутренние confirm/release, идемпотентность и статистику номеров.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "INTERNAL")
class RoomReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository roomReservationRepository;

    private Room room;

    /**
     * Создаёт эксплуатационно доступный номер перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
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
    }

    /**
     * Проверяет создание одной блокировки и однократное увеличение счётчика при повторе.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldConfirmAvailabilityIdempotently() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(confirm(room.getId(), 25L, requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(25))
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.status").value("HELD"));
        mockMvc.perform(confirm(room.getId(), 25L, requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));

        assertThat(roomReservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    /**
     * Проверяет конфликт пересекающихся активных блокировок.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectOverlappingConfirmation() throws Exception {
        mockMvc.perform(confirm(room.getId(), 25L, UUID.randomUUID()))
                .andExpect(status().isOk());

        mockMvc.perform(confirm(room.getId(), 26L, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Room is not available for the requested dates"));

        assertThat(roomReservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    /**
     * Проверяет компенсацию, повторный release и откат статистики.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReleaseCompensationIdempotently() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(confirm(room.getId(), 25L, requestId))
                .andExpect(status().isOk());

        mockMvc.perform(release(room.getId(), 25L, requestId, "COMPENSATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(release(room.getId(), 25L, requestId, "COMPENSATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        assertThat(roomReservationRepository.findByBookingId(25L).orElseThrow().getStatus())
                .isEqualTo(RoomReservationStatus.RELEASED);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isZero();
    }

    /**
     * Проверяет сохранение исторической статистики при отмене пользователем.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldPreserveTimesBookedAfterUserCancellation() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(confirm(room.getId(), 25L, requestId))
                .andExpect(status().isOk());

        mockMvc.perform(release(room.getId(), 25L, requestId, "USER_CANCELLATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    /**
     * Проверяет release после неуспешного confirm без создания истории занятости.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReleaseMissingReservationWithoutCreatingRecord() throws Exception {
        mockMvc.perform(release(room.getId(), 25L, UUID.randomUUID(), "COMPENSATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(25))
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.status").value("RELEASED"));

        assertThat(roomReservationRepository.count()).isZero();
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isZero();
    }

    /**
     * Проверяет запрет confirm для выведенного из эксплуатации номера.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectUnavailableRoom() throws Exception {
        room.setAvailable(false);
        roomRepository.saveAndFlush(room);

        mockMvc.perform(confirm(room.getId(), 25L, UUID.randomUUID()))
                .andExpect(status().isConflict());

        assertThat(roomReservationRepository.count()).isZero();
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isZero();
    }

    /**
     * Создаёт внутренний запрос confirm-availability.
     *
     * @param roomId идентификатор номера
     * @param bookingId идентификатор бронирования
     * @param requestId идентификатор запроса
     * @return построитель MockMvc-запроса
     */
    private MockHttpServletRequestBuilder confirm(Long roomId, Long bookingId, UUID requestId) {
        return post("/api/rooms/{id}/confirm-availability", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "bookingId": %d,
                          "requestId": "%s",
                          "startDate": "2026-11-10",
                          "endDate": "2026-11-15"
                        }
                        """.formatted(bookingId, requestId));
    }

    /**
     * Создаёт внутренний запрос release.
     *
     * @param roomId идентификатор номера
     * @param bookingId идентификатор бронирования
     * @param requestId идентификатор запроса
     * @param reason причина освобождения
     * @return построитель MockMvc-запроса
     */
    private MockHttpServletRequestBuilder release(Long roomId, Long bookingId, UUID requestId, String reason) {
        return post("/api/rooms/{id}/release", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "bookingId": %d,
                          "requestId": "%s",
                          "reason": "%s"
                        }
                        """.formatted(bookingId, requestId, reason));
    }
}
