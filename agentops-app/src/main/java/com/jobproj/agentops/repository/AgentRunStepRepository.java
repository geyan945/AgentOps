package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentRunStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunStepRepository extends JpaRepository<AgentRunStep, Long> {

    List<AgentRunStep> findByRunIdOrderByStepNoAsc(Long runId);

    long countByRunId(Long runId);
}
