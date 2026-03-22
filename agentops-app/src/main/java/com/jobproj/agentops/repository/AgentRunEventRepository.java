package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentRunEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunEventRepository extends JpaRepository<AgentRunEvent, Long> {

    List<AgentRunEvent> findByRunIdOrderByEventSequenceAsc(Long runId);

    long countByRunId(Long runId);
}
