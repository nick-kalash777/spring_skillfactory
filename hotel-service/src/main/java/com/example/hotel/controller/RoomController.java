package com.example.hotel.controller;

import com.example.hotel.dto.ConfirmAvailabilityRequest;
import com.example.hotel.dto.ReleaseRequest;
import com.example.hotel.dto.RoomDto;
import com.example.hotel.dto.RoomRequest;
import com.example.hotel.dto.RoomStatsDto;
import com.example.hotel.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RoomDto findById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoomDto> findFree(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                  @RequestParam(required = false) Long hotelId) {
        return roomService.findFree(startDate, endDate, hotelId);
    }

    @GetMapping("/recommend")
    @PreAuthorize("isAuthenticated()")
    public List<RoomDto> recommend(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                   @RequestParam(required = false) Long hotelId) {
        return roomService.recommend(startDate, endDate, hotelId);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoomStatsDto> stats(@RequestParam(required = false) Long hotelId) {
        return roomService.getStats(hotelId);
    }

    @PostMapping("/{id}/confirm-availability")
    @PreAuthorize("isAuthenticated()")
    public RoomDto confirmAvailability(@PathVariable Long id, @Valid @RequestBody ConfirmAvailabilityRequest request) {
        return roomService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("isAuthenticated()")
    public void release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest request) {
        roomService.release(id, request);
    }
}
