package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeMessageResponse {

    private Long id;
    private String role;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
}
