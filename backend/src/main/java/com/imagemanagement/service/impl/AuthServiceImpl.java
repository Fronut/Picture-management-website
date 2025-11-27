package com.imagemanagement.service.impl;

import com.imagemanagement.dto.request.LoginRequest;
import com.imagemanagement.dto.request.RegisterRequest;
import com.imagemanagement.dto.response.AuthResponse;
import com.imagemanagement.dto.response.UserResponse;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.security.jwt.JwtTokenProvider;
import com.imagemanagement.service.AuthService;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        return new AuthResponse(token, jwtTokenProvider.getExpirationMillis(), toUserResponse(saved));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var principal = (CustomUserDetails) authentication.getPrincipal();
        Long principalId = Objects.requireNonNull(principal.getId(), "User id must not be null");
        User user = userRepository.findById(principalId).orElseThrow();
        String token = jwtTokenProvider.generateToken(authentication);

        return new AuthResponse(token, jwtTokenProvider.getExpirationMillis(), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(), user.getRole());
    }
}
