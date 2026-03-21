package com.jobproj.agentops.runtime;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.runtime.RuntimeCommandResponse;
import com.jobproj.agentops.dto.runtime.RuntimeResumeRunRequest;
import com.jobproj.agentops.dto.runtime.RuntimeStartRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RuntimeClient {

    private final InternalAccessService internalAccessService;

    @Value("${agent.runtime.base-url:http://localhost:18085}")
    private String baseUrl;

    private RestClient buildClient() {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    public RuntimeCommandResponse startRun(RuntimeStartRunRequest request) {
        ApiResponse<RuntimeCommandResponse> response = buildClient().post()
                .uri("/runtime/graphs/enterprise-copilot/runs")
                .header("X-AgentOps-Internal-Key", internalAccessService.getInternalApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<RuntimeCommandResponse>>() {});
        return response == null ? null : response.getData();
    }

    public RuntimeCommandResponse resumeRun(RuntimeResumeRunRequest request) {
        ApiResponse<RuntimeCommandResponse> response = buildClient().post()
                .uri("/runtime/graphs/enterprise-copilot/runs/{runId}/resume", request.getRunId())
                .header("X-AgentOps-Internal-Key", internalAccessService.getInternalApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<RuntimeCommandResponse>>() {});
        return response == null ? null : response.getData();
    }
}
