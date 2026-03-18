package com.jobproj.agentops.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    BAD_REQUEST(4000, "请求参数错误"),
    UNAUTHORIZED(4001, "未登录或登录已过期"),
    FORBIDDEN(4003, "无权限执行当前操作"),
    RATE_LIMITED(4009, "请求过于频繁，请稍后再试"),
    USER_ALREADY_EXISTS(4101, "用户名已存在"),
    USER_NOT_FOUND(4102, "用户不存在"),
    BAD_CREDENTIALS(4103, "用户名或密码错误"),
    SESSION_NOT_FOUND(4201, "会话不存在"),
    RUN_NOT_FOUND(4202, "运行记录不存在"),
    MESSAGE_NOT_FOUND(4203, "消息不存在"),
    EVAL_DATASET_NOT_FOUND(4251, "评测数据集不存在"),
    EVAL_RUN_NOT_FOUND(4252, "评测任务不存在"),
    TOOL_NOT_FOUND(4301, "工具不存在或未启用"),
    TOOL_EXECUTION_FAILED(4302, "工具执行失败"),
    DOCUMENT_NOT_FOUND(4303, "文档不存在"),
    KB_SEARCH_FAILED(4304, "知识库检索失败"),
    INVALID_SQL_QUERY_TYPE(4305, "不支持的 SQL 查询模板"),
    AGENT_EXECUTION_FAILED(4401, "Agent 执行失败"),
    INTERNAL_ERROR(5000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}