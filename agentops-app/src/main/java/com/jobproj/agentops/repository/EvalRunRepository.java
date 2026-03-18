package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

    Optional<EvalRun> findByIdAndCreatedBy(Long id, Long createdBy);

    List<EvalRun> findByCreatedByOrderByIdDesc(Long createdBy);
}