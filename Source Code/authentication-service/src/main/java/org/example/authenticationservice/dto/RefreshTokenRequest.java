package org.example.authenticationservice.dto;


import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}