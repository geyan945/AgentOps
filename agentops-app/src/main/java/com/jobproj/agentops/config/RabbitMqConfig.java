package com.jobproj.agentops.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVAL_EXCHANGE = "agent.eval.exchange";
    public static final String EVAL_QUEUE = "agent.eval.run.queue";
    public static final String EVAL_ROUTING_KEY = "agent.eval.run";
    public static final String EVAL_DLX = "agent.eval.dlx";
    public static final String EVAL_DLQ = "agent.eval.run.dlq";
    public static final String EVAL_DL_ROUTING_KEY = "agent.eval.dlq";

    @Bean
    public DirectExchange evalExchange() {
        return new DirectExchange(EVAL_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange evalDeadLetterExchange() {
        return new DirectExchange(EVAL_DLX, true, false);
    }

    @Bean
    public Queue evalQueue() {
        return QueueBuilder.durable(EVAL_QUEUE)
                .deadLetterExchange(EVAL_DLX)
                .deadLetterRoutingKey(EVAL_DL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue evalDeadLetterQueue() {
        return QueueBuilder.durable(EVAL_DLQ).build();
    }

    @Bean
    public Binding evalBinding() {
        return BindingBuilder.bind(evalQueue()).to(evalExchange()).with(EVAL_ROUTING_KEY);
    }

    @Bean
    public Binding evalDeadLetterBinding() {
        return BindingBuilder.bind(evalDeadLetterQueue()).to(evalDeadLetterExchange()).with(EVAL_DL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}