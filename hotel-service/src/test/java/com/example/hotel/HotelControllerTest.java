package com.example.hotel;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Integer createHotel(String name) throws Exception {
        String response = mockMvc.perform(post("/api/hotels").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"address\":\"Main street 1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void adminCanCreateUpdateAndDeleteHotel() throws Exception {
        Integer id = createHotel("Hotel One");

        mockMvc.perform(get("/api/hotels").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/hotels/" + id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotel Two\",\"address\":\"Main street 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hotel Two"));

        mockMvc.perform(delete("/api/hotels/" + id).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hotels").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void userCanGetHotelById() throws Exception {
        Integer id = createHotel("Hotel Three");

        mockMvc.perform(get("/api/hotels/" + id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hotel Three"));
    }

    @Test
    void unknownHotelReturns404() throws Exception {
        mockMvc.perform(get("/api/hotels/999").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Hotel not found: 999"));
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotel One\",\"address\":\"Main street 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hotelWithoutNameReturns400() throws Exception {
        mockMvc.perform(post("/api/hotels").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Main street 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request data"));
    }

    @Test
    void adminSeesHotelStats() throws Exception {
        Integer id = createHotel("Stats Hotel");
        mockMvc.perform(post("/api/rooms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelId\":" + id + ",\"number\":\"1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/hotels/stats").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Stats Hotel"))
                .andExpect(jsonPath("$[0].roomsCount").value(1))
                .andExpect(jsonPath("$[0].totalBookings").value(0));

        mockMvc.perform(get("/api/hotels/stats").with(user()))
                .andExpect(status().isForbidden());
    }
}
