package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.AgentHumanTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentHumanTaskRepository extends JpaRepository<AgentHumanTask, Long> {

    List<AgentHumanTask> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<AgentHumanTask> findByIdAndUserId(Long id, Long userId);

    Optional<AgentHumanTask> findFirstByRunIdAndStatusOrderByIdDesc(Long runId, String status);
}
