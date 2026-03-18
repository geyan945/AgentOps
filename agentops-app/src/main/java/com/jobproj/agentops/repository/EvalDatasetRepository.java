package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.EvalDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalDatasetRepository extends JpaRepository<EvalDataset, Long> {

    List<EvalDataset> findByCreatedByOrderByIdDesc(Long createdBy);

    Optional<EvalDataset> findByIdAndCreatedBy(Long id, Long createdBy);
}