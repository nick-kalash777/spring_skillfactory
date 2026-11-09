package com.example.booking;

import com.example.booking.client.HotelClient;
import com.example.booking.dto.ConfirmAvailabilityRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotelClientTest {

    private static final String TOKEN = "Bearer test";

    private HttpServer server;
    private HotelClient hotelClient;
    private final AtomicInteger calls = new AtomicInteger();
    private int failuresBeforeSuccess;
    private int delayMs;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rooms", exchange -> {
            int call = calls.incrementAndGet();
            sleep(delayMs);
            int status = call <= failuresBeforeSuccess ? 500 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        hotelClient = new HotelClient(RestClient.builder(), url, 300);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ConfirmAvailabilityRequest confirmRequest() {
        return new ConfirmAvailabilityRequest("req-1", 1L, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 5));
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void retrySucceedsAfterServerErrors() {
        failuresBeforeSuccess = 2;

        hotelClient.confirmAvailability(1L, confirmRequest(), TOKEN);

        assertEquals(3, calls.get());
    }

    @Test
    void errorIsThrownWhenAllAttemptsFail() {
        failuresBeforeSuccess = 10;

        assertThrows(RestClientException.class,
                () -> hotelClient.confirmAvailability(1L, confirmRequest(), TOKEN));

        assertEquals(3, calls.get());
    }

    @Test
    void slowResponseFailsByTimeout() {
        delayMs = 1000;

        assertThrows(ResourceAccessException.class,
                () -> hotelClient.confirmAvailability(1L, confirmRequest(), TOKEN));

        assertEquals(3, calls.get());
    }

    @Test
    void circuitOpensAfterThreeFailedCalls() {
        failuresBeforeSuccess = 100;
        for (int i = 0; i < 3; i++) {
            assertThrows(RestClientException.class,
                    () -> hotelClient.confirmAvailability(1L, confirmRequest(), TOKEN));
        }
        int callsBefore = calls.get();

        ResourceAccessException e = assertThrows(ResourceAccessException.class,
                () -> hotelClient.confirmAvailability(1L, confirmRequest(), TOKEN));

        assertEquals("Circuit breaker is open", e.getMessage());
        assertEquals(callsBefore, calls.get());
    }
}
