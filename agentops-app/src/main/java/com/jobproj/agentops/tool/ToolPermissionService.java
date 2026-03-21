package com.jobproj.agentops.tool;

import com.jobproj.agentops.entity.SysUser;
import com.jobproj.agentops.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ToolPermissionService {

    private static final Map<String, Set<String>> ROLE_TOOLS = Map.of(
            "USER", Set.of("kb_search", "doc_fetch", "sql_query", "kb_search_remote", "sql_query_remote"),
            "ANALYST", Set.of("kb_search", "doc_fetch", "sql_query", "kb_search_remote", "sql_query_remote"),
            "ADMIN", Set.of("kb_search", "doc_fetch", "sql_query", "kb_search_remote", "sql_query_remote")
    );

    private final SysUserRepository userRepository;

    public boolean canUse(Long userId, String toolName) {
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Set<String> allowedTools = ROLE_TOOLS.getOrDefault(user.getRole(), ROLE_TOOLS.get("USER"));
        return allowedTools.contains(toolName);
    }
}
