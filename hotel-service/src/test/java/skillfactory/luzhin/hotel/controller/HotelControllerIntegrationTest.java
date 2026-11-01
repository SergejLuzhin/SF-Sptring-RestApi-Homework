package skillfactory.luzhin.hotel.controller;

import org.junit.jupiter.api.Test;
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
 * Проверяет REST API отелей вместе с сервисным слоем и H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles = {"USER", "ADMIN"})
class HotelControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository roomReservationRepository;

    /**
     * Проверяет создание отеля и возврат DTO с кодом 201.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Grand Hotel",
                                  "address": "Moscow, Tverskaya 1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Grand Hotel"))
                .andExpect(jsonPath("$.address").value("Moscow, Tverskaya 1"));

        assertThat(hotelRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет получение списка отелей.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnHotelList() throws Exception {
        Hotel hotel = createHotel("Grand Hotel");

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(hotel.getId()))
                .andExpect(jsonPath("$[0].name").value("Grand Hotel"))
                .andExpect(jsonPath("$[0].address").value("Test address"));
    }

    /**
     * Проверяет частичное обновление отеля без изменения адреса.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldUpdateOnlyProvidedHotelFields() throws Exception {
        Hotel hotel = createHotel("Grand Hotel");

        mockMvc.perform(patch("/api/hotels/{id}", hotel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Hotel"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hotel.getId()))
                .andExpect(jsonPath("$.name").value("Updated Hotel"))
                .andExpect(jsonPath("$.address").value("Test address"));
    }

    /**
     * Проверяет удаление отеля без истории занятости.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldDeleteHotelWithoutReservationHistory() throws Exception {
        Hotel hotel = createHotel("Grand Hotel");

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(hotelRepository.existsById(hotel.getId())).isFalse();
    }

    /**
     * Проверяет запрет удаления отеля с исторической записью занятости.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDeletionOfHotelWithReservationHistory() throws Exception {
        Hotel hotel = createHotel("Grand Hotel");
        Room room = createRoom(hotel);
        createReservation(room, RoomReservationStatus.RELEASED);

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("reservation history")))
                .andExpect(jsonPath("$.instance").value("/api/hotels/" + hotel.getId()));
    }

    /**
     * Проверяет ответ 404 при обновлении отсутствующего отеля.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnNotFoundForMissingHotel() throws Exception {
        mockMvc.perform(patch("/api/hotels/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Missing Hotel"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("was not found")))
                .andExpect(jsonPath("$.instance").value("/api/hotels/999999"));
    }

    /**
     * Проверяет ответ 400 и описание полей при ошибке Bean Validation.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectBlankHotelFields() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "address": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(containsString("name")))
                .andExpect(jsonPath("$.detail").value(containsString("address")));
    }

    /**
     * Проверяет ответ 400 на нечитаемый JSON.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Проверяет ответ 400 на неположительный идентификатор в пути.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectNonPositiveHotelId() throws Exception {
        mockMvc.perform(patch("/api/hotels/{id}", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Hotel"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Создаёт и сохраняет тестовый отель.
     *
     * @param name название отеля
     * @return сохранённый отель
     */
    private Hotel createHotel(String name) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setAddress("Test address");
        return hotelRepository.saveAndFlush(hotel);
    }

    /**
     * Создаёт и сохраняет тестовый номер.
     *
     * @param hotel отель номера
     * @return сохранённый номер
     */
    private Room createRoom(Hotel hotel) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber("101");
        room.setAvailable(true);
        return roomRepository.saveAndFlush(room);
    }

    /**
     * Создаёт и сохраняет запись занятости номера.
     *
     * @param room номер
     * @param status состояние записи
     */
    private void createReservation(Room room, RoomReservationStatus status) {
        RoomReservation reservation = new RoomReservation();
        reservation.setBookingId(10L);
        reservation.setRequestId(UUID.randomUUID());
        reservation.setRoom(room);
        reservation.setStartDate(LocalDate.of(2026, 11, 10));
        reservation.setEndDate(LocalDate.of(2026, 11, 15));
        reservation.setStatus(status);
        reservation.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        roomReservationRepository.saveAndFlush(reservation);
    }
}
