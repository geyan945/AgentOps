package com.jobproj.agentops.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserResponse {

    private Long id;
    private String username;
    private String role;
}