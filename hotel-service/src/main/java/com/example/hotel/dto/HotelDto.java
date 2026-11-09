package com.example.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HotelDto {

    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String address;
}
