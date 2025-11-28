package org.example.authenticationservice.dto;


import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
}