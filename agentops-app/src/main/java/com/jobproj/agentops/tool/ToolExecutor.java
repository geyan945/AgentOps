package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ToolExecutor {

    String getName();

    String getDescription();

    List<String> getArgumentNames();

    ToolResult execute(JsonNode arguments, ToolContext context);
}