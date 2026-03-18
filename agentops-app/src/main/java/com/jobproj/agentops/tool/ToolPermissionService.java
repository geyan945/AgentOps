package com.jobproj.agentops.tool;

import org.springframework.stereotype.Service;

@Service
public class ToolPermissionService {

    public boolean canUse(Long userId, String toolName) {
        return true;
    }
}