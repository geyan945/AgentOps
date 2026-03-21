package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentMemoryFact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMemoryFactRepository extends JpaRepository<AgentMemoryFact, Long> {

    List<AgentMemoryFact> findBySessionIdOrderByUpdatedAtDesc(Long sessionId);

    List<AgentMemoryFact> findByUserIdOrderByUpdatedAtDesc(Long userId);

    void deleteBySourceRunId(Long sourceRunId);
}
