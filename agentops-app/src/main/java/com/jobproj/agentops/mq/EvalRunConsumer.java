package com.jobproj.agentops.mq;

import com.jobproj.agentops.config.RabbitMqConfig;
import com.jobproj.agentops.service.EvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvalRunConsumer {

    private final EvalService evalService;

    @RabbitListener(queues = RabbitMqConfig.EVAL_QUEUE)
    public void handle(EvalCaseMessage message) {
        evalService.handleEvalCaseMessage(message);
    }
}