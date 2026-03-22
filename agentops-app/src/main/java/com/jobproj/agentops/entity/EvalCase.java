package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "eval_case")
public class EvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String question;

    @Column(name = "expected_tool", length = 128)
    private String expectedTool;

    @Column(name = "expected_route", length = 64)
    private String expectedRoute;

    @Lob
    @Column(name = "expected_keywords_json", columnDefinition = "MEDIUMTEXT")
    private String expectedKeywordsJson;

    @Lob
    @Column(name = "expected_node_path_json", columnDefinition = "MEDIUMTEXT")
    private String expectedNodePathJson;

    @Column(name = "expected_approval_policy", length = 64)
    private String expectedApprovalPolicy;

    @Column(name = "expected_orchestration_mode", length = 64)
    private String expectedOrchestrationMode;

    @Column(name = "expected_citation_min")
    private Integer expectedCitationMin;

    @Lob
    @Column(name = "expected_artifact_types_json", columnDefinition = "MEDIUMTEXT")
    private String expectedArtifactTypesJson;

    @Lob
    @Column(name = "expected_skills_json", columnDefinition = "MEDIUMTEXT")
    private String expectedSkillsJson;

    @Lob
    @Column(name = "expected_reference_json", columnDefinition = "MEDIUMTEXT")
    private String expectedReferenceJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
