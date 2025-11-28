package org.example.authenticationservice.dto;

import lombok.Data;
import org.example.authenticationservice.model.Role;

@Data
public class RegisterRequest {

    private String fullName;
    private String email;
    private String password;


    private Role role; // si null → ROLE_CLIENT
}
