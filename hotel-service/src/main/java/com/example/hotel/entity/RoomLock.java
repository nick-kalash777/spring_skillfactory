package com.example.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "room_locks", indexes = @Index(name = "idx_room_locks_room_id", columnList = "roomId"))
@Getter
@Setter
@NoArgsConstructor
public class RoomLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roomId;

    private Long bookingId;

    @Column(unique = true)
    private String requestId;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean active;
}
