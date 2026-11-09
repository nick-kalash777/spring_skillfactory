package com.example.booking.service;

import com.example.booking.dto.AuthRequest;
import com.example.booking.dto.TokenResponse;
import com.example.booking.dto.UserCreateRequest;
import com.example.booking.dto.UserDto;
import com.example.booking.dto.UserUpdateRequest;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.exception.ConflictException;
import com.example.booking.exception.NotFoundException;
import com.example.booking.mapper.UserMapper;
import com.example.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    @Transactional
    public TokenResponse register(AuthRequest request) {
        User user = saveNewUser(request.getUsername(), request.getPassword(), Role.USER);
        return new TokenResponse(tokenService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse auth(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.info("failed login for user {}", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }
        return new TokenResponse(tokenService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userMapper.toDtoList(userRepository.findAll());
    }

    @Transactional
    public UserDto create(UserCreateRequest request) {
        Role role = request.getRole() == null ? Role.USER : request.getRole();
        User user = saveNewUser(request.getUsername(), request.getPassword(), role);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto update(UserUpdateRequest request) {
        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getId()));
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            checkUsernameFree(request.getUsername());
            user.setUsername(request.getUsername());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        log.info("user {} updated", user.getId());
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        log.info("user {} deleted", id);
    }

    private User saveNewUser(String username, String password, Role role) {
        checkUsernameFree(username);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        User saved = userRepository.save(user);
        log.info("user {} created with role {}", saved.getId(), role);
        return saved;
    }

    private void checkUsernameFree(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("User already exists: " + username);
        }
    }
}
