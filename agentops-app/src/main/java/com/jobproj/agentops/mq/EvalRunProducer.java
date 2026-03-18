package com.jobproj.agentops.mq;

import com.jobproj.agentops.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvalRunProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(EvalCaseMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EVAL_EXCHANGE, RabbitMqConfig.EVAL_ROUTING_KEY, message);
    }
}