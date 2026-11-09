package com.example.hotel.dto;

import lombok.Data;

@Data
public class RoomStatsDto {

    private Long roomId;
    private Long hotelId;
    private String number;
    private int timesBooked;
    private long activeBookings;
}
