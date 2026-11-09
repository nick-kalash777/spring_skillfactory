package com.example.hotel.service;

import com.example.hotel.dto.ConfirmAvailabilityRequest;
import com.example.hotel.dto.ReleaseRequest;
import com.example.hotel.dto.RoomDto;
import com.example.hotel.dto.RoomRequest;
import com.example.hotel.dto.RoomStatsDto;
import com.example.hotel.entity.Hotel;
import com.example.hotel.entity.Room;
import com.example.hotel.entity.RoomLock;
import com.example.hotel.exception.ConflictException;
import com.example.hotel.exception.NotFoundException;
import com.example.hotel.mapper.RoomMapper;
import com.example.hotel.repository.HotelRepository;
import com.example.hotel.repository.RoomLockRepository;
import com.example.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomLockRepository roomLockRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomDto create(RoomRequest request) {
        Room room = new Room();
        room.setTimesBooked(0);
        fillRoom(room, request);
        Room saved = roomRepository.save(room);
        log.info("room {} created in hotel {}", saved.getId(), request.getHotelId());
        return roomMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public RoomDto findById(Long id) {
        return roomMapper.toDto(getRoom(id));
    }

    @Transactional
    public RoomDto update(Long id, RoomRequest request) {
        Room room = getRoom(id);
        fillRoom(room, request);
        return roomMapper.toDto(roomRepository.save(room));
    }

    @Transactional
    public void delete(Long id) {
        roomRepository.delete(getRoom(id));
        log.info("room {} deleted", id);
    }

    @Transactional(readOnly = true)
    public List<RoomDto> findFree(LocalDate startDate, LocalDate endDate, Long hotelId) {
        checkDates(startDate, endDate);
        return roomMapper.toDtoList(roomRepository.findFreeRooms(startDate, endDate, hotelId));
    }

    @Transactional(readOnly = true)
    public List<RoomDto> recommend(LocalDate startDate, LocalDate endDate, Long hotelId) {
        checkDates(startDate, endDate);
        List<Room> rooms = roomRepository.findFreeRooms(startDate, endDate, hotelId);
        rooms.sort(Comparator.comparing(Room::getTimesBooked).thenComparing(Room::getId));
        return roomMapper.toDtoList(rooms);
    }

    @Transactional(readOnly = true)
    public List<RoomStatsDto> getStats(Long hotelId) {
        List<Room> rooms = hotelId == null ? roomRepository.findAll() : roomRepository.findByHotelId(hotelId);
        rooms.sort(Comparator.comparing(Room::getTimesBooked).reversed().thenComparing(Room::getId));
        List<RoomStatsDto> result = new ArrayList<>();
        for (Room room : rooms) {
            RoomStatsDto stats = roomMapper.toStatsDto(room);
            stats.setActiveBookings(roomLockRepository.countByRoomIdAndActiveTrue(room.getId()));
            result.add(stats);
        }
        return result;
    }

    @Transactional
    public RoomDto confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        log.info("bookingId={} requestId={} confirm availability of room {}", request.getBookingId(), request.getRequestId(), roomId);
        checkDates(request.getStartDate(), request.getEndDate());

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));
        if (!room.isAvailable()) {
            log.info("bookingId={} room {} is not available", request.getBookingId(), roomId);
            throw new ConflictException("Room is not available: " + roomId);
        }

        if (roomLockRepository.findByRequestId(request.getRequestId()).isPresent()) {
            log.info("bookingId={} requestId={} request already processed", request.getBookingId(), request.getRequestId());
            return roomMapper.toDto(room);
        }

        if (roomLockRepository.isRoomBusy(roomId, request.getStartDate(), request.getEndDate())) {
            log.info("bookingId={} room {} is already booked for these dates", request.getBookingId(), roomId);
            throw new ConflictException("Room is already booked for these dates");
        }

        RoomLock lock = new RoomLock();
        lock.setRoomId(roomId);
        lock.setBookingId(request.getBookingId());
        lock.setRequestId(request.getRequestId());
        lock.setStartDate(request.getStartDate());
        lock.setEndDate(request.getEndDate());
        lock.setActive(true);
        roomLockRepository.save(lock);

        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);

        log.info("bookingId={} room {} locked, timesBooked={}", request.getBookingId(), roomId, room.getTimesBooked());
        return roomMapper.toDto(room);
    }

    @Transactional
    public void release(Long roomId, ReleaseRequest request) {
        log.info("bookingId={} requestId={} release lock of room {}", request.getBookingId(), request.getRequestId(), roomId);
        Optional<RoomLock> found = roomLockRepository.findByRoomIdAndBookingIdAndActiveTrue(roomId, request.getBookingId());
        if (found.isEmpty()) {
            log.info("bookingId={} active lock not found, nothing to release", request.getBookingId());
            return;
        }

        RoomLock lock = found.get();
        lock.setActive(false);
        roomLockRepository.save(lock);

        Room room = roomRepository.findByIdForUpdate(roomId).orElse(null);
        if (room != null && room.getTimesBooked() > 0) {
            room.setTimesBooked(room.getTimesBooked() - 1);
            roomRepository.save(room);
        }

        log.info("bookingId={} lock of room {} released", request.getBookingId(), roomId);
    }

    private void fillRoom(Room room, RoomRequest request) {
        Hotel hotel = hotelRepository.findById(request.getHotelId())
                .orElseThrow(() -> new NotFoundException("Hotel not found: " + request.getHotelId()));
        room.setHotel(hotel);
        room.setNumber(request.getNumber());
        room.setAvailable(request.isAvailable());
    }

    private Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room not found: " + id));
    }

    private void checkDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
    }
}
