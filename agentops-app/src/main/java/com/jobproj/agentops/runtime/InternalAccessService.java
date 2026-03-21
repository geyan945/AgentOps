package com.jobproj.agentops.runtime;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InternalAccessService {

    @Value("${agent.internal.api-key:agentops-internal-key}")
    private String internalApiKey;

    public void assertAuthorized(String providedKey) {
        if (!StringUtils.hasText(providedKey) || !internalApiKey.equals(providedKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内部接口访问失败");
        }
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }
}
