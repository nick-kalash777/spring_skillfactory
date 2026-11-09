package com.example.hotel.service;

import com.example.hotel.dto.HotelDto;
import com.example.hotel.dto.HotelStatsDto;
import com.example.hotel.entity.Hotel;
import com.example.hotel.entity.Room;
import com.example.hotel.exception.NotFoundException;
import com.example.hotel.mapper.HotelMapper;
import com.example.hotel.repository.HotelRepository;
import com.example.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

    @Transactional
    public HotelDto create(HotelDto dto) {
        Hotel saved = hotelRepository.save(hotelMapper.toEntity(dto));
        log.info("hotel {} created", saved.getId());
        return hotelMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<HotelDto> findAll() {
        return hotelMapper.toDtoList(hotelRepository.findAll());
    }

    @Transactional(readOnly = true)
    public HotelDto findById(Long id) {
        return hotelMapper.toDto(getHotel(id));
    }

    @Transactional
    public HotelDto update(Long id, HotelDto dto) {
        Hotel hotel = getHotel(id);
        hotel.setName(dto.getName());
        hotel.setAddress(dto.getAddress());
        return hotelMapper.toDto(hotelRepository.save(hotel));
    }

    @Transactional
    public void delete(Long id) {
        Hotel hotel = getHotel(id);
        roomRepository.deleteAll(roomRepository.findByHotelId(id));
        hotelRepository.delete(hotel);
        log.info("hotel {} deleted", id);
    }

    @Transactional(readOnly = true)
    public List<HotelStatsDto> getStats() {
        List<HotelStatsDto> result = new ArrayList<>();
        for (Hotel hotel : hotelRepository.findAll()) {
            List<Room> rooms = roomRepository.findByHotelId(hotel.getId());
            int totalBookings = 0;
            for (Room room : rooms) {
                totalBookings += room.getTimesBooked();
            }
            HotelStatsDto stats = new HotelStatsDto();
            stats.setHotelId(hotel.getId());
            stats.setName(hotel.getName());
            stats.setRoomsCount(rooms.size());
            stats.setTotalBookings(totalBookings);
            stats.setAverageBookingsPerRoom(rooms.isEmpty() ? 0 : (double) totalBookings / rooms.size());
            result.add(stats);
        }
        return result;
    }

    private Hotel getHotel(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Hotel not found: " + id));
    }
}
