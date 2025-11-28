package org.example.authenticationservice.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String code;       // code reçu par email OU master code
    private String newPassword;
}