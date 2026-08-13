package com.sjp.recruitment.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    /** "admin" = trang quản trị; mặc định / khác = cổng người dùng. */
    private String portal;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPortal() { return portal; }
    public void setPortal(String portal) { this.portal = portal; }
}
