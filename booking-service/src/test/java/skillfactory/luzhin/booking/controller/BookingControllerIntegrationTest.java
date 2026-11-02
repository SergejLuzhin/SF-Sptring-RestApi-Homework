package skillfactory.luzhin.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import skillfactory.luzhin.booking.dto.BookingCreateRequest;
import skillfactory.luzhin.booking.dto.BookingResponse;
import skillfactory.luzhin.booking.dto.ReleaseReason;
import skillfactory.luzhin.booking.entity.Booking;
import skillfactory.luzhin.booking.entity.BookingStatus;
import skillfactory.luzhin.booking.entity.User;
import skillfactory.luzhin.booking.entity.UserRole;
import skillfactory.luzhin.booking.exception.BusinessConflictException;
import skillfactory.luzhin.booking.exception.ServiceUnavailableException;
import skillfactory.luzhin.booking.repository.BookingRepository;
import skillfactory.luzhin.booking.repository.UserRepository;
import skillfactory.luzhin.booking.service.HotelServiceClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет локальную модель бронирований, идемпотентность и владение записями.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingControllerIntegrationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private HotelServiceClient hotelServiceClient;

    /**
     * Настраивает успешный удалённый автоподбор для тестов создания брони.
     */
    @BeforeEach
    void setUpHotelClient() {
        when(hotelServiceClient.findRecommendedRoomId(anyString(), any(), any(), any()))
                .thenReturn(Optional.of(20L));
    }

    /**
     * Проверяет создание CONFIRMED-брони с явно выбранным номером.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCreateConfirmedBooking() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.roomId").value(10))
                .andExpect(jsonPath("$.startDate").value("2026-11-10"))
                .andExpect(jsonPath("$.endDate").value("2026-11-15"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").isString());

        Booking saved = bookingRepository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getAutoSelect()).isFalse();
        verify(hotelServiceClient).confirmAvailability(eq(10L), any());
    }

    /**
     * Проверяет игнорирование roomId при автоматическом подборе.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldIgnoreRoomIdWhenAutoSelectIsEnabled() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        assertThat(readResponse(result).roomId()).isEqualTo(20L);
        Booking saved = bookingRepository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getRoomId()).isEqualTo(20L);
        assertThat(saved.getAutoSelect()).isTrue();
        verify(hotelServiceClient).confirmAvailability(eq(20L), any());
    }

    /**
     * Проверяет возврат исходной брони при повторе того же запроса.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnOriginalBookingOnIdempotentRetry() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        BookingCreateRequest request = request(UUID.randomUUID(), 10L, START_DATE, END_DATE, false);

        MvcResult created = mockMvc.perform(createBookingRequest(user, request))
                .andExpect(status().isCreated())
                .andReturn();
        Long bookingId = readResponse(created).id();

        mockMvc.perform(createBookingRequest(user, request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(bookingRepository.count()).isEqualTo(1);
        verify(hotelServiceClient).confirmAvailability(eq(10L), any());
    }

    /**
     * Проверяет идемпотентность автоподбора независимо от переданного roomId.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldIgnoreChangedRoomIdOnAutoSelectRetry() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(createBookingRequest(
                        user,
                        request(requestId, 10L, START_DATE, END_DATE, true)
                ))
                .andExpect(status().isCreated())
                .andReturn();
        Long bookingId = readResponse(created).id();

        mockMvc.perform(createBookingRequest(user, request(requestId, 999L, START_DATE, END_DATE, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет конфликт requestId с другими бизнес-параметрами.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectSameRequestIdWithDifferentParameters() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, false)))
                .andExpect(status().isCreated());

        mockMvc.perform(createBookingRequest(user, request(requestId, 11L, START_DATE, END_DATE, false)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("requestId already exists with different parameters"));

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет, что другой пользователь не может переиспользовать чужой requestId.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectSameRequestIdFromAnotherUser() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        User other = createUser("other@example.com", UserRole.USER);
        BookingCreateRequest request = request(UUID.randomUUID(), 10L, START_DATE, END_DATE, false);

        mockMvc.perform(createBookingRequest(owner, request))
                .andExpect(status().isCreated());

        mockMvc.perform(createBookingRequest(other, request))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    /**
     * Проверяет обязательность roomId при ручном выборе.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRequireRoomIdForManualSelection() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);

        mockMvc.perform(createBookingRequest(user, request(UUID.randomUUID(), null, START_DATE, END_DATE, false)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("roomId is required when autoSelect is false"));

        assertThat(bookingRepository.count()).isZero();
    }

    /**
     * Проверяет отклонение равных или обратных дат бронирования.
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
    void shouldRejectInvalidDateInterval(LocalDate startDate, LocalDate endDate) throws Exception {
        User user = createUser("user@example.com", UserRole.USER);

        mockMvc.perform(createBookingRequest(user, request(UUID.randomUUID(), 10L, startDate, endDate, false)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("startDate must be earlier than endDate"));

        assertThat(bookingRepository.count()).isZero();
    }

    /**
     * Проверяет отказ без создания PENDING, если рекомендации не содержат номеров.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectAutoSelectWhenNoRoomsAreAvailable() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        when(hotelServiceClient.findRecommendedRoomId(anyString(), any(), any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(createBookingRequest(
                        user,
                        request(UUID.randomUUID(), null, START_DATE, END_DATE, true)
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No rooms are available for the requested dates"));

        assertThat(bookingRepository.count()).isZero();
    }

    /**
     * Проверяет компенсацию и отмену PENDING при бизнес-конфликте confirm.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCompensatePendingBookingAfterConfirmConflict() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();
        doThrow(new BusinessConflictException("Room is not available for the requested dates"))
                .when(hotelServiceClient).confirmAvailability(eq(10L), any());

        mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Room is not available for the requested dates"));

        Booking saved = bookingRepository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(hotelServiceClient).release(eq(10L), org.mockito.ArgumentMatchers.argThat(
                request -> request.reason() == ReleaseReason.COMPENSATION
        ));
    }

    /**
     * Проверяет отмену PENDING после недоступности confirm и успешного release.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCancelPendingBookingAfterUnavailableConfirmAndSuccessfulRelease() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();
        doThrow(new ServiceUnavailableException("Hotel Service is unavailable"))
                .when(hotelServiceClient).confirmAvailability(eq(10L), any());

        mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, false)))
                .andExpect(status().isServiceUnavailable());

        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет явную ошибку и сохранение PENDING при неуспешной компенсации.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReportCompensationFailure() throws Exception {
        User user = createUser("user@example.com", UserRole.USER);
        UUID requestId = UUID.randomUUID();
        doThrow(new ServiceUnavailableException("Hotel Service is unavailable"))
                .when(hotelServiceClient).confirmAvailability(eq(10L), any());
        doThrow(new ServiceUnavailableException("Hotel Service is unavailable"))
                .when(hotelServiceClient).release(eq(10L), any());

        mockMvc.perform(createBookingRequest(user, request(requestId, 10L, START_DATE, END_DATE, false)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("compensation failed")));

        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    /**
     * Проверяет получение истории только текущего пользователя в обратном порядке.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnOnlyCurrentUsersHistory() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        User other = createUser("other@example.com", UserRole.USER);
        Booking older = createBooking(owner, BookingStatus.PENDING);
        Booking newer = createBooking(owner, BookingStatus.CANCELLED);
        createBooking(other, BookingStatus.PENDING);

        mockMvc.perform(get("/bookings").with(userJwt(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()))
                .andExpect(jsonPath("$[0].userId").value(owner.getId()))
                .andExpect(jsonPath("$[1].userId").value(owner.getId()));
    }

    /**
     * Проверяет получение собственной брони по идентификатору.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldReturnOwnBookingById() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.PENDING);

        mockMvc.perform(get("/booking/{id}", booking.getId()).with(userJwt(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.userId").value(owner.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /**
     * Проверяет сокрытие чужой брони при получении по идентификатору.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldHideOtherUsersBookingOnGet() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        User other = createUser("other@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.CONFIRMED);

        mockMvc.perform(get("/booking/{id}", booking.getId()).with(userJwt(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("Booking")));
    }

    /**
     * Проверяет локальную отмену собственной PENDING-брони.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCancelOwnPendingBooking() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.PENDING);

        mockMvc.perform(delete("/booking/{id}", booking.getId()).with(userJwt(owner)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет идемпотентность повторной отмены.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldMakeCancellationIdempotent() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.CANCELLED);

        mockMvc.perform(delete("/booking/{id}", booking.getId()).with(userJwt(owner)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * Проверяет сокрытие чужой брони при попытке отмены.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldHideOtherUsersBookingOnCancel() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        User other = createUser("other@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.PENDING);

        mockMvc.perform(delete("/booking/{id}", booking.getId()).with(userJwt(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    /**
     * Проверяет отмену CONFIRMED-брони после успешного release в Hotel Service.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldCancelConfirmedBookingAfterHotelRelease() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.CONFIRMED);

        mockMvc.perform(delete("/booking/{id}", booking.getId()).with(userJwt(owner)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        verify(hotelServiceClient).release(eq(10L), org.mockito.ArgumentMatchers.argThat(
                request -> request.reason() == ReleaseReason.USER_CANCELLATION
        ));
    }

    /**
     * Проверяет сохранение CONFIRMED при неуспешном release в Hotel Service.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldNotCancelConfirmedBookingWhenHotelReleaseFails() throws Exception {
        User owner = createUser("owner@example.com", UserRole.USER);
        Booking booking = createBooking(owner, BookingStatus.CONFIRMED);
        doThrow(new ServiceUnavailableException("Hotel Service is unavailable"))
                .when(hotelServiceClient).release(eq(10L), any());

        mockMvc.perform(delete("/booking/{id}", booking.getId()).with(userJwt(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("Hotel Service")));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    /**
     * Проверяет запрет удаления пользователя вместе с историей бронирований.
     *
     * @throws Exception ошибка выполнения MockMvc-запроса
     */
    @Test
    void shouldRejectDeletingUserWithBookingHistory() throws Exception {
        User admin = createUser("admin@example.com", UserRole.ADMIN);
        User owner = createUser("owner@example.com", UserRole.USER);
        createBooking(owner, BookingStatus.CANCELLED);

        mockMvc.perform(delete("/user")
                        .param("id", owner.getId().toString())
                        .with(jwt().jwt(token -> token.subject(admin.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User with booking history cannot be deleted"));

        assertThat(userRepository.existsById(owner.getId())).isTrue();
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    /**
     * Создаёт пользователя с BCrypt-хешем.
     *
     * @param username имя пользователя
     * @param role роль пользователя
     * @return сохранённый пользователь
     */
    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    /**
     * Создаёт бронирование с уникальным requestId.
     *
     * @param owner владелец бронирования
     * @param status начальный статус
     * @return сохранённое бронирование
     */
    private Booking createBooking(User owner, BookingStatus status) {
        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setUser(owner);
        booking.setRoomId(10L);
        booking.setStartDate(START_DATE);
        booking.setEndDate(END_DATE);
        booking.setStatus(status);
        booking.setRequestId(UUID.randomUUID());
        booking.setAutoSelect(false);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        return bookingRepository.saveAndFlush(booking);
    }

    /**
     * Создаёт DTO запроса на бронирование.
     *
     * @param requestId идентификатор запроса
     * @param roomId идентификатор номера
     * @param startDate дата заезда
     * @param endDate дата выезда
     * @param autoSelect признак автоподбора
     * @return DTO запроса
     */
    private BookingCreateRequest request(
            UUID requestId,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoSelect
    ) {
        return new BookingCreateRequest(requestId, roomId, startDate, endDate, autoSelect);
    }

    /**
     * Создаёт аутентифицированный MockMvc-запрос на создание брони.
     *
     * @param user текущий пользователь
     * @param request тело запроса
     * @return построитель HTTP-запроса
     * @throws Exception ошибка сериализации JSON
     */
    private MockHttpServletRequestBuilder createBookingRequest(
            User user,
            BookingCreateRequest request
    ) throws Exception {
        return post("/booking")
                .with(userJwt(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    /**
     * Создаёт JWT postprocessor с subject и ролью USER.
     *
     * @param user текущий пользователь
     * @return postprocessor аутентификации
     */
    private RequestPostProcessor userJwt(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * Читает DTO бронирования из HTTP-ответа.
     *
     * @param result результат MockMvc
     * @return DTO бронирования
     * @throws Exception ошибка разбора JSON
     */
    private BookingResponse readResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), BookingResponse.class);
    }
}
