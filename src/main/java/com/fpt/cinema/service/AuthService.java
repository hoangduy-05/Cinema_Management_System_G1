package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.LoginRequest;
import com.fpt.cinema.dto.request.RegisterRequest;
import com.fpt.cinema.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
