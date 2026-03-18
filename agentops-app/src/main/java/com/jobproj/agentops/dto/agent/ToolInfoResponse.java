package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ToolInfoResponse {

    private String name;
    private String description;
    private List<String> argumentNames;
}