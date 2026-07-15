package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
        @Size(max = 255, message = "Tên đăng nhập hoặc email không được vượt quá 255 ký tự")
        String usernameOrEmail,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 255, message = "Mật khẩu không được vượt quá 255 ký tự")
        String password
) {
}
