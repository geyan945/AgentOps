package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
}