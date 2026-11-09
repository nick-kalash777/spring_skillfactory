package com.example.booking.dto;

import com.example.booking.entity.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private Role role;
}
