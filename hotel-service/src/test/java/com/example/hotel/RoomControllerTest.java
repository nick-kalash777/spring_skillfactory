package com.example.hotel;

import com.example.hotel.entity.Hotel;
import com.example.hotel.entity.Room;
import com.example.hotel.repository.HotelRepository;
import com.example.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotel = createHotel("Test Hotel");
    }

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Hotel createHotel(String name) {
        Hotel newHotel = new Hotel();
        newHotel.setName(name);
        newHotel.setAddress("Test street");
        return hotelRepository.save(newHotel);
    }

    private Room createRoom(Hotel roomHotel, String number, int timesBooked, boolean available) {
        Room room = new Room();
        room.setHotel(roomHotel);
        room.setNumber(number);
        room.setAvailable(available);
        room.setTimesBooked(timesBooked);
        return roomRepository.save(room);
    }

    private Room createRoom(String number, int timesBooked) {
        return createRoom(hotel, number, timesBooked, true);
    }

    private String confirmBody(String requestId, long bookingId, String startDate, String endDate) {
        return "{\"requestId\":\"" + requestId + "\",\"bookingId\":" + bookingId
                + ",\"startDate\":\"" + startDate + "\",\"endDate\":\"" + endDate + "\"}";
    }

    @Test
    void adminCanCreateRoom() throws Exception {
        mockMvc.perform(post("/api/rooms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelId\":" + hotel.getId() + ",\"number\":\"777\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));
    }

    @Test
    void roomForUnknownHotelReturns404() throws Exception {
        mockMvc.perform(post("/api/rooms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelId\":999,\"number\":\"777\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hotel not found: 999"));
    }

    @Test
    void recommendSortsByTimesBookedThenId() throws Exception {
        Room first = createRoom("101", 5);
        Room second = createRoom("102", 1);
        Room third = createRoom("103", 1);

        mockMvc.perform(get("/api/rooms/recommend").with(user())
                        .param("startDate", "2030-01-01")
                        .param("endDate", "2030-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(third.getId()))
                .andExpect(jsonPath("$[2].id").value(first.getId()));
    }

    @Test
    void freeRoomsCanBeFilteredByHotel() throws Exception {
        Hotel otherHotel = createHotel("Other Hotel");
        createRoom("101", 0);
        Room otherRoom = createRoom(otherHotel, "201", 0, true);
        createRoom(otherHotel, "202", 0, false);

        mockMvc.perform(get("/api/rooms").with(user())
                        .param("startDate", "2030-01-01")
                        .param("endDate", "2030-01-05")
                        .param("hotelId", otherHotel.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherRoom.getId()));
    }

    @Test
    void wrongDatesReturn400() throws Exception {
        mockMvc.perform(get("/api/rooms").with(user())
                        .param("startDate", "2030-01-05")
                        .param("endDate", "2030-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void confirmAvailabilitySuccess() throws Exception {
        Room room = createRoom("201", 0);

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-1", 1, "2030-02-01", "2030-02-05")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timesBooked").value(1));

        mockMvc.perform(get("/api/rooms").with(user())
                        .param("startDate", "2030-02-03")
                        .param("endDate", "2030-02-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void repeatedRequestIdDoesNotIncreaseTimesBooked() throws Exception {
        Room room = createRoom("301", 0);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody("req-same", 2, "2030-03-01", "2030-03-05")))
                    .andExpect(status().isOk());
        }

        assertEquals(1, roomRepository.findById(room.getId()).get().getTimesBooked());
    }

    @Test
    void overlappingDatesReturnConflict() throws Exception {
        Room room = createRoom("401", 0);
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-a", 3, "2030-04-01", "2030-04-10")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-b", 4, "2030-04-05", "2030-04-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unavailableRoomReturnsConflict() throws Exception {
        Room room = createRoom(hotel, "402", 0, false);

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-c", 7, "2030-04-01", "2030-04-10")))
                .andExpect(status().isConflict());
    }

    @Test
    void releaseRemovesLock() throws Exception {
        Room room = createRoom("501", 0);
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-r1", 5, "2030-05-01", "2030-05-05")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/release").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"rel-1\",\"bookingId\":5}"))
                .andExpect(status().isOk());

        assertEquals(0, roomRepository.findById(room.getId()).get().getTimesBooked());
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("req-r2", 6, "2030-05-02", "2030-05-04")))
                .andExpect(status().isOk());
    }

    @Test
    void adminSeesRoomStats() throws Exception {
        createRoom("601", 2);
        Room popular = createRoom("602", 9);

        mockMvc.perform(get("/api/rooms/stats").with(admin())
                        .param("hotelId", hotel.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].roomId").value(popular.getId()))
                .andExpect(jsonPath("$[0].timesBooked").value(9))
                .andExpect(jsonPath("$[0].activeBookings").value(0));
    }

    @Test
    void userCannotSeeRoomStats() throws Exception {
        mockMvc.perform(get("/api/rooms/stats").with(user()))
                .andExpect(status().isForbidden());
    }
}
