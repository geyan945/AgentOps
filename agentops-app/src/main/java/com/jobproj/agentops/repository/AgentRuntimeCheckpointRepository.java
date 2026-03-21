package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentRuntimeCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRuntimeCheckpointRepository extends JpaRepository<AgentRuntimeCheckpoint, Long> {

    Optional<AgentRuntimeCheckpoint> findByRunId(Long runId);

    void deleteByRunId(Long runId);
}
