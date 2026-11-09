package com.example.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ConfirmAvailabilityRequest {

    @NotBlank
    private String requestId;

    @NotNull
    private Long bookingId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}
