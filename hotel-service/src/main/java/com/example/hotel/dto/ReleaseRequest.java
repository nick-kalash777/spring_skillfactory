package com.example.hotel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReleaseRequest {

    private String requestId;

    @NotNull
    private Long bookingId;
}
