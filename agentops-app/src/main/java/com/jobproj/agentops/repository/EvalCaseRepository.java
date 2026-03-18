package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.EvalCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalCaseRepository extends JpaRepository<EvalCase, Long> {

    List<EvalCase> findByDatasetIdOrderByIdAsc(Long datasetId);

    long countByDatasetId(Long datasetId);
}