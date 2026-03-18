package com.jobproj.agentops.dto.session;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @Size(max = 255, message = "标题长度不能超过 255")
    private String title;
}