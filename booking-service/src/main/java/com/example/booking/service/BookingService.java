package com.example.booking.service;

import com.example.booking.client.HotelClient;
import com.example.booking.dto.BookingDto;
import com.example.booking.dto.BookingRequest;
import com.example.booking.dto.ConfirmAvailabilityRequest;
import com.example.booking.dto.ReleaseRequest;
import com.example.booking.dto.RoomDto;
import com.example.booking.entity.Booking;
import com.example.booking.entity.BookingStatus;
import com.example.booking.exception.ConflictException;
import com.example.booking.exception.NotFoundException;
import com.example.booking.exception.ServiceUnavailableException;
import com.example.booking.mapper.BookingMapper;
import com.example.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int MAX_ROOMS_TO_TRY = 3;

    private final BookingRepository bookingRepository;
    private final BookingStateService bookingStateService;
    private final BookingMapper bookingMapper;
    private final HotelClient hotelClient;

    public BookingDto createBooking(BookingRequest request, Long userId, String token) {
        String requestId = request.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        Optional<Booking> existing = bookingRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return returnExisting(existing.get(), userId);
        }

        checkDates(request.getStartDate(), request.getEndDate());
        if (!request.isAutoSelect() && request.getRoomId() == null) {
            throw new IllegalArgumentException("roomId is required when autoSelect is false");
        }

        Long roomId = request.isAutoSelect() ? null : request.getRoomId();
        Booking booking;
        try {
            booking = bookingStateService.createPending(userId, roomId, request.getStartDate(), request.getEndDate(), requestId);
        } catch (DataIntegrityViolationException e) {
            Booking saved = bookingRepository.findByRequestId(requestId)
                    .orElseThrow(() -> new ConflictException("Booking could not be created"));
            return returnExisting(saved, userId);
        }
        Long bookingId = booking.getId();

        List<Long> roomIds = new ArrayList<>();
        if (request.isAutoSelect()) {
            roomIds = selectRooms(bookingId, request.getStartDate(), request.getEndDate(), token);
        } else {
            roomIds.add(roomId);
        }

        ConfirmAvailabilityRequest confirmRequest = new ConfirmAvailabilityRequest(
                requestId, bookingId, request.getStartDate(), request.getEndDate());
        for (Long candidateId : roomIds) {
            if (request.isAutoSelect()) {
                bookingStateService.setRoom(bookingId, candidateId);
            }
            try {
                hotelClient.confirmAvailability(candidateId, confirmRequest, token);
                Booking confirmed = bookingStateService.changeStatus(bookingId, BookingStatus.CONFIRMED);
                return bookingMapper.toDto(confirmed);
            } catch (HttpClientErrorException e) {
                log.warn("bookingId={} room {} rejected by hotel service: {}", bookingId, candidateId, e.getStatusCode());
            } catch (RestClientException e) {
                log.error("bookingId={} hotel service error: {}", bookingId, e.getMessage());
                compensate(candidateId, requestId, bookingId, token);
                bookingStateService.changeStatus(bookingId, BookingStatus.CANCELLED);
                throw new ServiceUnavailableException("Hotel service is unavailable, booking cancelled, bookingId=" + bookingId);
            }
        }

        bookingStateService.changeStatus(bookingId, BookingStatus.CANCELLED);
        throw new ConflictException("Room is not available for these dates, bookingId=" + bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUserBookings(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be from 1 to 100");
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").descending());
        return bookingMapper.toDtoList(bookingRepository.findByUserId(userId, pageRequest));
    }

    @Transactional(readOnly = true)
    public BookingDto getBooking(Long id, Long userId) {
        return bookingMapper.toDto(findUserBooking(id, userId));
    }

    public BookingDto cancelBooking(Long id, Long userId, String token) {
        Booking booking = findUserBooking(id, userId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return bookingMapper.toDto(booking);
        }
        Booking cancelled = bookingStateService.changeStatus(id, BookingStatus.CANCELLED);
        if (booking.getRoomId() != null) {
            compensate(booking.getRoomId(), booking.getRequestId(), id, token);
        }
        return bookingMapper.toDto(cancelled);
    }

    public List<RoomDto> getRecommendedRooms(LocalDate startDate, LocalDate endDate, String token) {
        checkDates(startDate, endDate);
        try {
            return hotelClient.getRecommendedRooms(startDate, endDate, token);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Hotel service is unavailable");
        }
    }

    private List<Long> selectRooms(Long bookingId, LocalDate startDate, LocalDate endDate, String token) {
        List<RoomDto> rooms;
        try {
            rooms = hotelClient.getRecommendedRooms(startDate, endDate, token);
        } catch (RestClientException e) {
            log.error("bookingId={} failed to get recommended rooms: {}", bookingId, e.getMessage());
            bookingStateService.changeStatus(bookingId, BookingStatus.CANCELLED);
            throw new ServiceUnavailableException("Hotel service is unavailable, booking cancelled, bookingId=" + bookingId);
        }

        List<Long> roomIds = new ArrayList<>();
        for (RoomDto room : rooms) {
            if (roomIds.size() < MAX_ROOMS_TO_TRY) {
                roomIds.add(room.getId());
            }
        }
        log.info("bookingId={} rooms to try: {}", bookingId, roomIds);
        return roomIds;
    }

    private BookingDto returnExisting(Booking booking, Long userId) {
        if (!booking.getUserId().equals(userId)) {
            throw new ConflictException("requestId is already used");
        }
        log.info("bookingId={} request {} already processed, returning existing booking", booking.getId(), booking.getRequestId());
        return bookingMapper.toDto(booking);
    }

    private void compensate(Long roomId, String requestId, Long bookingId, String token) {
        try {
            hotelClient.release(roomId, new ReleaseRequest(requestId, bookingId), token);
            log.info("bookingId={} compensation done: room {} released", bookingId, roomId);
        } catch (RestClientException e) {
            log.error("bookingId={} compensation failed: {}", bookingId, e.getMessage());
        }
    }

    private void checkDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Start date must not be in the past");
        }
    }

    private Booking findUserBooking(Long id, Long userId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + id));
        if (!booking.getUserId().equals(userId)) {
            throw new NotFoundException("Booking not found: " + id);
        }
        return booking;
    }
}
