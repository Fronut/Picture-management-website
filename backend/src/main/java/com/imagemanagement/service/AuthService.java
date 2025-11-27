package com.imagemanagement.service;

import com.imagemanagement.dto.request.LoginRequest;
import com.imagemanagement.dto.request.RegisterRequest;
import com.imagemanagement.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
