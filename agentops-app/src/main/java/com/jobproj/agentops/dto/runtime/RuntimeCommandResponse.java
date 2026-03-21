package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeCommandResponse {

    private boolean accepted;
    private String status;
    private String currentNode;
}
