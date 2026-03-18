package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findBySessionIdOrderByIdAsc(Long sessionId);
}