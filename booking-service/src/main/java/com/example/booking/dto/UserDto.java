package com.example.booking.dto;

import com.example.booking.entity.Role;
import lombok.Data;

@Data
public class UserDto {

    private Long id;
    private String username;
    private Role role;
}
