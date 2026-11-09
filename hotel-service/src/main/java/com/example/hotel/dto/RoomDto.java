package com.example.hotel.dto;

import lombok.Data;

@Data
public class RoomDto {

    private Long id;
    private Long hotelId;
    private String number;
    private boolean available;
    private int timesBooked;
}
