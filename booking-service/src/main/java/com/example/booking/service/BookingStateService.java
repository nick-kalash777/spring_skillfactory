package com.example.booking.service;

import com.example.booking.entity.Booking;
import com.example.booking.entity.BookingStatus;
import com.example.booking.exception.NotFoundException;
import com.example.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStateService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking createPending(Long userId, Long roomId, LocalDate startDate, LocalDate endDate, String requestId) {
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setRoomId(roomId);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setRequestId(requestId);
        Booking saved = bookingRepository.saveAndFlush(booking);
        log.info("bookingId={} created with status PENDING, requestId={}", saved.getId(), requestId);
        return saved;
    }

    @Transactional
    public Booking setRoom(Long bookingId, Long roomId) {
        Booking booking = findBooking(bookingId);
        booking.setRoomId(roomId);
        log.info("bookingId={} room {} selected automatically", bookingId, roomId);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking changeStatus(Long bookingId, BookingStatus newStatus) {
        Booking booking = findBooking(bookingId);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(newStatus);
        log.info("bookingId={} {} -> {}", bookingId, oldStatus, newStatus);
        return bookingRepository.save(booking);
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
    }
}
