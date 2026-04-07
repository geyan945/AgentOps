package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.runtime.InternalAccessService;
import com.jobproj.agentops.web.RequestIdHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class McpHttpClient {

    private final InternalAccessService internalAccessService;

    @Value("${agent.mcp.base-url:http://localhost:18084/internal/mcp}")
    private String baseUrl;

    private RestClient buildClient() {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    public McpInitializeResponse initialize() {
        ApiResponse<McpInitializeResponse> response = buildClient()
                .post()
                .uri("/initialize")
                .header("X-AgentOps-Internal-Key", internalAccessService.getInternalApiKey())
                .header(RequestIdHolder.HEADER_NAME, RequestIdHolder.currentOrGenerate())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<McpInitializeResponse>>() {});
        return response == null ? null : response.getData();
    }

    public List<ToolInfoResponse> listTools() {
        ApiResponse<ToolInfoResponse[]> response = buildClient()
                .get()
                .uri("/tools")
                .header("X-AgentOps-Internal-Key", internalAccessService.getInternalApiKey())
                .header(RequestIdHolder.HEADER_NAME, RequestIdHolder.currentOrGenerate())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<ToolInfoResponse[]>>() {});
        if (response == null || response.getData() == null) {
            return List.of();
        }
        return Arrays.asList(response.getData());
    }

    public McpToolCallResponse callTool(String toolName, JsonNode arguments) {
        McpToolCallRequest request = new McpToolCallRequest();
        request.setToolName(toolName);
        request.setArguments(arguments);
        ApiResponse<McpToolCallResponse> response = buildClient()
                .post()
                .uri("/tools/call")
                .header("X-AgentOps-Internal-Key", internalAccessService.getInternalApiKey())
                .header(RequestIdHolder.HEADER_NAME, RequestIdHolder.currentOrGenerate())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<McpToolCallResponse>>() {});
        return response == null ? null : response.getData();
    }
}
