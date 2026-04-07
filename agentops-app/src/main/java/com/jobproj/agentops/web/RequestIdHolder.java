package com.jobproj.agentops.web;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestIdHolder {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdHolder() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrGenerate() {
        String value = current();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
