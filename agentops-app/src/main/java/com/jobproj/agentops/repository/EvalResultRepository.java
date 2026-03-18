package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.EvalResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalResultRepository extends JpaRepository<EvalResult, Long> {

    List<EvalResult> findByEvalRunIdOrderByIdAsc(Long evalRunId);

    List<EvalResult> findByEvalRunIdInOrderByIdDesc(List<Long> evalRunIds);

    Optional<EvalResult> findByEvalRunIdAndCaseId(Long evalRunId, Long caseId);

    boolean existsByEvalRunIdAndCaseId(Long evalRunId, Long caseId);
}