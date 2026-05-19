package com.sjp.recruitment.model.dto.response;

import com.sjp.recruitment.model.entity.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AuthResponse {
    private final User user;
    private final String token;
}