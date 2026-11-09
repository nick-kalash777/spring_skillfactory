package com.example.hotel.dto;

import lombok.Data;

@Data
public class HotelStatsDto {

    private Long hotelId;
    private String name;
    private int roomsCount;
    private int totalBookings;
    private double averageBookingsPerRoom;
}
