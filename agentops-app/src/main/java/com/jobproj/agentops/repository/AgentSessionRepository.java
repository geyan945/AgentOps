package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    List<AgentSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<AgentSession> findByIdAndUserId(Long id, Long userId);
}