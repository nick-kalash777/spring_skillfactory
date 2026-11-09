package com.example.booking.dto;

import com.example.booking.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @NotNull
    private Long id;

    private String username;

    private String password;

    private Role role;
}
