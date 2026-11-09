package com.example.booking;

import com.example.booking.client.HotelClient;
import com.example.booking.dto.RoomDto;
import com.example.booking.entity.Booking;
import com.example.booking.entity.BookingStatus;
import com.example.booking.repository.BookingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private HotelClient hotelClient;

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll();
    }

    private RequestPostProcessor user(long userId) {
        return jwt().jwt(j -> j.claim("userId", userId)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private String bookingJson(String requestId, long roomId) {
        return "{\"requestId\":\"" + requestId + "\",\"roomId\":" + roomId
                + ",\"startDate\":\"2030-12-01\",\"endDate\":\"2030-12-05\",\"autoSelect\":false}";
    }

    private String autoBookingJson(String requestId) {
        return "{\"requestId\":\"" + requestId
                + "\",\"startDate\":\"2030-12-01\",\"endDate\":\"2030-12-05\",\"autoSelect\":true}";
    }

    private RoomDto room(long id) {
        RoomDto room = new RoomDto();
        room.setId(id);
        return room;
    }

    @Test
    void successfulBookingIsConfirmed() throws Exception {
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-success", 1L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(1));

        verify(hotelClient, times(1)).confirmAvailability(eq(1L), any(), any());
        verify(hotelClient, never()).release(any(), any(), any());
    }

    @Test
    void remoteErrorCancelsBookingAndReleasesRoom() throws Exception {
        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .when(hotelClient).confirmAvailability(eq(2L), any(), any());

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-error", 2L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        Booking booking = bookingRepository.findByRequestId("req-error").orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(hotelClient, times(1)).release(eq(2L), any(), any());
    }

    @Test
    void timeoutCancelsBookingAndReleasesRoom() throws Exception {
        doThrow(new ResourceAccessException("Read timed out"))
                .when(hotelClient).confirmAvailability(eq(3L), any(), any());

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-timeout", 3L)))
                .andExpect(status().isServiceUnavailable());

        Booking booking = bookingRepository.findByRequestId("req-timeout").orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(hotelClient, times(1)).release(eq(3L), any(), any());
    }

    @Test
    void repeatedRequestDoesNotCreateDuplicate() throws Exception {
        String body = bookingJson("req-repeat", 4L);
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        Long id = bookingRepository.findByRequestId("req-repeat").orElseThrow().getId();

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(hotelClient, times(1)).confirmAvailability(eq(4L), any(), any());
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void sameRequestIdFromOtherUserReturns409() throws Exception {
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-shared", 4L)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/booking").with(user(101L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-shared", 4L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("requestId is already used"));
    }

    @Test
    void roomConflictReturns409() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(hotelClient).confirmAvailability(eq(5L), any(), any());

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-conflict", 5L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        Booking booking = bookingRepository.findByRequestId("req-conflict").orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(hotelClient, never()).release(any(), any(), any());
    }

    @Test
    void autoSelectUsesFirstRecommendedRoom() throws Exception {
        when(hotelClient.getRecommendedRooms(any(), any(), any())).thenReturn(List.of(room(7L), room(8L)));

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(autoBookingJson("req-auto")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(7));

        verify(hotelClient, times(1)).confirmAvailability(eq(7L), any(), any());
        verify(hotelClient, never()).confirmAvailability(eq(8L), any(), any());
    }

    @Test
    void autoSelectTriesNextRoomWhenFirstIsBusy() throws Exception {
        when(hotelClient.getRecommendedRooms(any(), any(), any())).thenReturn(List.of(room(10L), room(11L)));
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(hotelClient).confirmAvailability(eq(10L), any(), any());

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(autoBookingJson("req-next")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(11));

        verify(hotelClient, times(1)).confirmAvailability(eq(10L), any(), any());
        verify(hotelClient, times(1)).confirmAvailability(eq(11L), any(), any());
    }

    @Test
    void autoSelectWithoutFreeRoomsReturns409() throws Exception {
        when(hotelClient.getRecommendedRooms(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content(autoBookingJson("req-empty")))
                .andExpect(status().isConflict());

        Booking booking = bookingRepository.findByRequestId("req-empty").orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void pastDatesReturn400() throws Exception {
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"startDate\":\"2020-01-01\",\"endDate\":\"2020-01-05\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start date must not be in the past"));

        verify(hotelClient, never()).confirmAvailability(any(), any(), any());
        assertEquals(0, bookingRepository.count());
    }

    @Test
    void endDateBeforeStartDateReturns400() throws Exception {
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"startDate\":\"2030-01-05\",\"endDate\":\"2030-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start date must be before end date"));
    }

    @Test
    void bookingWithoutRoomIdReturns400() throws Exception {
        mockMvc.perform(post("/booking").with(user(100L)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2030-01-01\",\"endDate\":\"2030-01-05\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingsArePaginated() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/booking").with(user(400L)).contentType(MediaType.APPLICATION_JSON)
                            .content(bookingJson("req-page-" + i, i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/bookings").with(user(400L)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requestId").value("req-page-3"));

        mockMvc.perform(get("/bookings").with(user(400L)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value("req-page-1"));

        mockMvc.perform(get("/bookings").with(user(400L)).param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userSeesOnlyOwnBookingsAndCanCancel() throws Exception {
        mockMvc.perform(post("/booking").with(user(200L)).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("req-own", 9L)))
                .andExpect(status().isCreated());
        Long id = bookingRepository.findByRequestId("req-own").orElseThrow().getId();

        mockMvc.perform(get("/bookings").with(user(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/booking/" + id).with(user(300L)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/booking/" + id).with(user(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelClient, times(1)).release(eq(9L), any(), any());
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isUnauthorized());
    }
}
