package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    Optional<AgentRun> findByIdAndUserId(Long id, Long userId);

    List<AgentRun> findBySessionIdOrderByIdDesc(Long sessionId);
}
