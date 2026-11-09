package com.example.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoomRequest {

    @NotNull
    private Long hotelId;

    @NotBlank
    private String number;

    private boolean available = true;
}
