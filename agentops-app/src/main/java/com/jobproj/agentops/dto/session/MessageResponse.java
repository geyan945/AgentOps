package com.jobproj.agentops.dto.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private String role;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
}