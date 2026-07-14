package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải có từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Tên đăng nhập chỉ được chứa chữ, số, dấu chấm, gạch dưới hoặc gạch ngang")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        String password,

        @Pattern(regexp = "^$|^\\+?[0-9]{9,15}$", message = "Số điện thoại không đúng định dạng")
        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        String phone,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
        String fullName,

        @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
        LocalDate dateOfBirth,

        @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
        String address
) {
}
