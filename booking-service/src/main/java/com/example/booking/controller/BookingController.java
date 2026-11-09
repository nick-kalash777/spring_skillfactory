package com.example.booking.controller;

import com.example.booking.dto.BookingDto;
import com.example.booking.dto.BookingRequest;
import com.example.booking.dto.RoomDto;
import com.example.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/booking")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public BookingDto createBooking(@Valid @RequestBody BookingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.createBooking(request, getUserId(jwt), getToken(jwt));
    }

    @GetMapping("/bookings")
    @PreAuthorize("isAuthenticated()")
    public List<BookingDto> getBookings(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getUserBookings(getUserId(jwt), page, size);
    }

    @GetMapping("/booking/{id}")
    @PreAuthorize("isAuthenticated()")
    public BookingDto getBooking(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getBooking(id, getUserId(jwt));
    }

    @DeleteMapping("/booking/{id}")
    @PreAuthorize("isAuthenticated()")
    public BookingDto cancelBooking(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.cancelBooking(id, getUserId(jwt), getToken(jwt));
    }

    @GetMapping("/booking/rooms/recommend")
    @PreAuthorize("isAuthenticated()")
    public List<RoomDto> getRecommendedRooms(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getRecommendedRooms(startDate, endDate, getToken(jwt));
    }

    private Long getUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new IllegalArgumentException("Token does not contain userId");
        }
        return userId.longValue();
    }

    private String getToken(Jwt jwt) {
        return "Bearer " + jwt.getTokenValue();
    }
}
