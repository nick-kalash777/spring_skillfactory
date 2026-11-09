package com.example.hotel.repository;

import com.example.hotel.entity.RoomLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

    Optional<RoomLock> findByRequestId(String requestId);

    Optional<RoomLock> findByRoomIdAndBookingIdAndActiveTrue(Long roomId, Long bookingId);

    long countByRoomIdAndActiveTrue(Long roomId);

    @Query("select count(l) > 0 from RoomLock l where l.roomId = :roomId and l.active = true "
            + "and l.startDate < :endDate and l.endDate > :startDate")
    boolean isRoomBusy(@Param("roomId") Long roomId, @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate);
}
