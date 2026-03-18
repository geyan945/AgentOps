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

    @Lob
    @Column(name = "expected_keywords_json", columnDefinition = "MEDIUMTEXT")
    private String expectedKeywordsJson;

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