package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeMemoryFactResponse {

    private Long id;
    private String factType;
    private String factKey;
    private String factValue;
}
