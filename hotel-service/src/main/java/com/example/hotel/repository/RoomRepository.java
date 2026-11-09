package com.example.hotel.repository;

import com.example.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHotelId(Long hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from Room r where r.available = true "
            + "and (:hotelId is null or r.hotel.id = :hotelId) "
            + "and not exists (select l from RoomLock l where l.roomId = r.id and l.active = true "
            + "and l.startDate < :endDate and l.endDate > :startDate)")
    List<Room> findFreeRooms(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                             @Param("hotelId") Long hotelId);
}
