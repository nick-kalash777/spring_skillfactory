package com.example.booking;

import com.example.booking.client.HotelClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelClient hotelClient;

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("userId", 1L)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.claim("userId", 2L)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void registerAndAuthReturnToken() throws Exception {
        String body = "{\"username\":\"ivan\",\"password\":\"12345\"}";

        mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registerExistingUserReturns409() throws Exception {
        mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"12345\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists: admin"));
    }

    @Test
    void registerWithoutPasswordReturns400() throws Exception {
        mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"oleg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void authWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void adminCanLogin() throws Exception {
        mockMvc.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void userCannotCreateUser() throws Exception {
        mockMvc.perform(post("/user").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"petr\",\"password\":\"12345\",\"role\":\"USER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void userCannotListUsers() throws Exception {
        mockMvc.perform(get("/user").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUpdateAndDeleteUser() throws Exception {
        String response = mockMvc.perform(post("/user").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"maria\",\"password\":\"12345\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("maria"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(response, "$.id");

        mockMvc.perform(patch("/user").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/user").with(admin()).param("id", id.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/user").with(admin()).param("id", id.toString()))
                .andExpect(status().isNotFound());
    }
}
