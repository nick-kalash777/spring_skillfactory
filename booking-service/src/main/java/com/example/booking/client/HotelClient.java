package com.example.booking.client;

import com.example.booking.dto.ConfirmAvailabilityRequest;
import com.example.booking.dto.ReleaseRequest;
import com.example.booking.dto.RoomDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
public class HotelClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final int FAILURES_TO_OPEN_CIRCUIT = 3;
    private static final long CIRCUIT_OPEN_TIME_MS = 10000;

    private final RestClient restClient;
    private int failuresInRow;
    private long circuitOpenUntil;

    public HotelClient(RestClient.Builder builder,
                       @Value("${hotel-service.url}") String hotelUrl,
                       @Value("${hotel-service.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder
                .baseUrl(hotelUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public List<RoomDto> getRecommendedRooms(LocalDate startDate, LocalDate endDate, String token) {
        checkCircuit();
        return callWithRetry("recommend rooms", null, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/rooms/recommend")
                        .queryParam("startDate", startDate)
                        .queryParam("endDate", endDate)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomDto>>() {
                }));
    }

    public void confirmAvailability(Long roomId, ConfirmAvailabilityRequest request, String token) {
        checkCircuit();
        callWithRetry("confirm availability", request.getBookingId(),
                () -> post("/api/rooms/{id}/confirm-availability", roomId, request, token));
    }

    public void release(Long roomId, ReleaseRequest request, String token) {
        callWithRetry("release", request.getBookingId(),
                () -> post("/api/rooms/{id}/release", roomId, request, token));
    }

    private Object post(String uri, Long roomId, Object body, String token) {
        return restClient.post()
                .uri(uri, roomId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private <T> T callWithRetry(String action, Long bookingId, Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                T result = call.get();
                onSuccess();
                return result;
            } catch (HttpClientErrorException e) {
                onSuccess();
                throw e;
            } catch (RestClientException e) {
                log.warn("bookingId={} {} attempt {} of {} failed: {}", bookingId, action, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    onFailure();
                    throw e;
                }
                sleep(attempt * RETRY_DELAY_MS);
            }
        }
    }

    private synchronized void checkCircuit() {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            log.warn("circuit breaker is open, hotel service is not called");
            throw new ResourceAccessException("Circuit breaker is open");
        }
    }

    private synchronized void onSuccess() {
        failuresInRow = 0;
    }

    private synchronized void onFailure() {
        failuresInRow++;
        if (failuresInRow >= FAILURES_TO_OPEN_CIRCUIT) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_OPEN_TIME_MS;
            failuresInRow = 0;
            log.warn("circuit breaker opened for {} ms", CIRCUIT_OPEN_TIME_MS);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
