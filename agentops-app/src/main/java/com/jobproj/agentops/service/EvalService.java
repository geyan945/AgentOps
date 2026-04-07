package com.jobproj.agentops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.agent.EvalCaseExecutionResult;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.eval.CreateEvalDatasetRequest;
import com.jobproj.agentops.dto.eval.CreateEvalRunRequest;
import com.jobproj.agentops.dto.eval.EvalCaseResponse;
import com.jobproj.agentops.dto.eval.EvalDashboardResponse;
import com.jobproj.agentops.dto.eval.EvalDatasetResponse;
import com.jobproj.agentops.dto.eval.EvalFailureSampleResponse;
import com.jobproj.agentops.dto.eval.EvalResultResponse;
import com.jobproj.agentops.dto.eval.EvalRunResponse;
import com.jobproj.agentops.dto.runtime.RuntimeCommandResponse;
import com.jobproj.agentops.dto.runtime.RuntimeStartRunRequest;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import com.jobproj.agentops.entity.AgentSession;
import com.jobproj.agentops.entity.EvalCase;
import com.jobproj.agentops.entity.EvalDataset;
import com.jobproj.agentops.entity.EvalResult;
import com.jobproj.agentops.entity.EvalRun;
import com.jobproj.agentops.mq.EvalCaseMessage;
import com.jobproj.agentops.mq.EvalRunProducer;
import com.jobproj.agentops.repository.AgentRunRepository;
import com.jobproj.agentops.repository.AgentRunStepRepository;
import com.jobproj.agentops.repository.AgentSessionRepository;
import com.jobproj.agentops.repository.EvalCaseRepository;
import com.jobproj.agentops.repository.EvalDatasetRepository;
import com.jobproj.agentops.repository.EvalResultRepository;
import com.jobproj.agentops.repository.EvalRunRepository;
import com.jobproj.agentops.repository.SysUserRepository;
import com.jobproj.agentops.runtime.RuntimeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvalService {

    private final EvalDatasetRepository evalDatasetRepository;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalResultRepository evalResultRepository;
    private final EvalRunProducer evalRunProducer;
    private final RateLimitService rateLimitService;
    private final EvalScoringService evalScoringService;
    private final ObjectMapper objectMapper;
    private final RuntimeClient runtimeClient;
    private final AgentSessionRepository agentSessionRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunStepRepository agentRunStepRepository;
    private final SysUserRepository sysUserRepository;

    @Transactional
    public EvalDatasetResponse createDataset(Long userId, CreateEvalDatasetRequest request) {
        EvalDataset dataset = new EvalDataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(userId);
        dataset.setTenantId(sysUserRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getTenantId());
        EvalDataset savedDataset = evalDatasetRepository.save(dataset);

        List<EvalCase> cases = new ArrayList<>();
        request.getCases().forEach(item -> {
            EvalCase evalCase = new EvalCase();
            evalCase.setDatasetId(savedDataset.getId());
            evalCase.setQuestion(item.getQuestion());
            evalCase.setExpectedTool(item.getExpectedTool());
            evalCase.setExpectedRoute(item.getExpectedRoute());
            evalCase.setExpectedKeywordsJson(toJson(item.getExpectedKeywords()));
            evalCase.setExpectedNodePathJson(toJson(item.getExpectedNodePath()));
            evalCase.setExpectedApprovalPolicy(item.getExpectedApprovalPolicy());
            evalCase.setExpectedCitationMin(item.getExpectedCitationMin());
            evalCase.setExpectedArtifactTypesJson(toJson(item.getExpectedArtifactTypes()));
            evalCase.setExpectedOrchestrationMode(item.getExpectedOrchestrationMode());
            evalCase.setExpectedSkillsJson(toJson(item.getExpectedSkills()));
            cases.add(evalCase);
        });
        evalCaseRepository.saveAll(cases);
        return toDatasetResponse(savedDataset, cases);
    }

    @Transactional(readOnly = true)
    public List<EvalDatasetResponse> listDatasets(Long userId) {
        return evalDatasetRepository.findByCreatedByOrderByIdDesc(userId).stream()
                .map(dataset -> toDatasetResponse(dataset, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvalDatasetResponse getDataset(Long userId, Long datasetId) {
        EvalDataset dataset = getRequiredDataset(userId, datasetId);
        return toDatasetResponse(dataset, evalCaseRepository.findByDatasetIdOrderByIdAsc(datasetId));
    }

    @Transactional
    public EvalRunResponse createRun(Long userId, CreateEvalRunRequest request) {
        rateLimitService.checkEvalAllowed(userId);
        EvalDataset dataset = getRequiredDataset(userId, request.getDatasetId());
        List<EvalCase> cases = evalCaseRepository.findByDatasetIdOrderByIdAsc(dataset.getId());
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评测集不能为空");
        }
        EvalRun evalRun = new EvalRun();
        evalRun.setDatasetId(dataset.getId());
        evalRun.setCreatedBy(userId);
        evalRun.setTenantId(dataset.getTenantId());
        evalRun.setStatus("RUNNING");
        evalRun.setTotalCases(cases.size());
        evalRun = evalRunRepository.save(evalRun);

        for (EvalCase evalCase : cases) {
            evalRunProducer.send(new EvalCaseMessage(evalRun.getId(), dataset.getId(), evalCase.getId(), userId));
        }
        return toRunResponse(evalRun);
    }

    @Transactional(readOnly = true)
    public EvalRunResponse getRun(Long userId, Long runId) {
        return toRunResponse(getRequiredRun(userId, runId));
    }

    @Transactional(readOnly = true)
    public List<EvalResultResponse> listResults(Long userId, Long runId) {
        getRequiredRun(userId, runId);
        return evalResultRepository.findByEvalRunIdOrderByIdAsc(runId).stream()
                .map(this::toResultResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvalDashboardResponse getDashboard(Long userId) {
        List<EvalDataset> datasets = evalDatasetRepository.findByCreatedByOrderByIdDesc(userId);
        List<EvalRun> runs = evalRunRepository.findByCreatedByOrderByIdDesc(userId);
        List<Long> runIds = runs.stream().map(EvalRun::getId).toList();
        List<EvalResult> results = runIds.isEmpty() ? List.of() : evalResultRepository.findByEvalRunIdInOrderByIdDesc(runIds);
        int runningRunCount = (int) runs.stream().filter(run -> "RUNNING".equalsIgnoreCase(run.getStatus())).count();
        int completedRunCount = (int) runs.stream().filter(run -> "COMPLETED".equalsIgnoreCase(run.getStatus())).count();
        int failedResultCount = (int) results.stream().filter(result -> !Boolean.TRUE.equals(result.getSuccess())).count();
        double avgScore = results.isEmpty() ? 0D : results.stream().map(EvalResult::getScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0D);
        double passRate = results.isEmpty() ? 0D : results.stream().filter(result -> Boolean.TRUE.equals(result.getSuccess())).count() * 1.0D / results.size();
        long avgLatencyMs = results.isEmpty() ? 0L : Math.round(results.stream().map(EvalResult::getLatencyMs).filter(v -> v != null).mapToLong(Long::longValue).average().orElse(0D));
        return EvalDashboardResponse.builder()
                .datasetCount(datasets.size())
                .runCount(runs.size())
                .runningRunCount(runningRunCount)
                .completedRunCount(completedRunCount)
                .failedResultCount(failedResultCount)
                .avgScore(avgScore)
                .avgGroundingScore(results.isEmpty() ? 0D : results.stream().map(EvalResult::getGroundingScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0D))
                .avgCitationScore(results.isEmpty() ? 0D : results.stream().map(EvalResult::getCitationScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0D))
                .passRate(passRate)
                .avgLatencyMs(avgLatencyMs)
                .latestFailedSamples(listFailedSamples(userId, 10))
                .build();
    }

    @Transactional(readOnly = true)
    public List<EvalFailureSampleResponse> listFailedSamples(Long userId, int limit) {
        List<EvalRun> runs = evalRunRepository.findByCreatedByOrderByIdDesc(userId);
        if (runs.isEmpty()) {
            return List.of();
        }
        List<Long> runIds = runs.stream().map(EvalRun::getId).toList();
        List<EvalResult> results = evalResultRepository.findByEvalRunIdInOrderByIdDesc(runIds).stream()
                .filter(result -> !Boolean.TRUE.equals(result.getSuccess()))
                .limit(Math.max(1, limit))
                .toList();
        if (results.isEmpty()) {
            return List.of();
        }
        Map<Long, EvalCase> caseById = evalCaseRepository.findAllById(results.stream().map(EvalResult::getCaseId).distinct().toList())
                .stream().collect(Collectors.toMap(EvalCase::getId, item -> item));
        return results.stream().map(result -> {
            EvalCase evalCase = caseById.get(result.getCaseId());
            return EvalFailureSampleResponse.builder()
                    .resultId(result.getId())
                    .runId(result.getEvalRunId())
                    .caseId(result.getCaseId())
                    .question(evalCase == null ? null : evalCase.getQuestion())
                    .actualTool(result.getActualTool())
                    .score(result.getScore())
                    .reason(result.getReason())
                    .latencyMs(result.getLatencyMs())
                    .createdAt(result.getCreatedAt())
                    .build();
        }).toList();
    }

    @Transactional
    public void handleEvalCaseMessage(EvalCaseMessage message) {
        EvalRun evalRun = evalRunRepository.findById(message.getEvalRunId()).orElse(null);
        if (evalRun == null || evalResultRepository.existsByEvalRunIdAndCaseId(message.getEvalRunId(), message.getCaseId())) {
            return;
        }
        EvalCase evalCase = evalCaseRepository.findById(message.getCaseId()).orElse(null);
        if (evalCase == null) {
            saveFailureResult(evalRun, message.getCaseId(), "评测用例不存在");
            refreshRunStats(evalRun.getId());
            return;
        }

        try {
            EvalCaseExecutionResult executionResult = executeCase(message.getUserId(), evalCase);
            EvalScoringService.ScoreOutcome scoreOutcome = evalScoringService.score(evalCase, executionResult);

            EvalResult evalResult = new EvalResult();
            evalResult.setEvalRunId(evalRun.getId());
            evalResult.setCaseId(evalCase.getId());
            evalResult.setActualTool(executionResult.getActualTool());
            evalResult.setAnswerText(executionResult.getAnswerText());
            evalResult.setSuccess(scoreOutcome.isSuccess());
            evalResult.setScore(scoreOutcome.getScore());
            evalResult.setRouteScore(scoreOutcome.getRouteScore());
            evalResult.setGroundingScore(scoreOutcome.getGroundingScore());
            evalResult.setCitationScore(scoreOutcome.getCitationScore());
            evalResult.setFinalScore(scoreOutcome.getFinalScore());
            evalResult.setRetryCount(executionResult.getRetryCount());
            evalResult.setReason(scoreOutcome.getReason());
            evalResult.setToolTraceJson(toJson(executionResult.getToolTrace()));
            evalResult.setNodePathJson(toJson(executionResult.getNodePath()));
            evalResult.setApprovalTriggered(executionResult.getApprovalTriggered());
            evalResult.setApprovalDecision(executionResult.getApprovalDecision());
            evalResult.setSkillsUsedJson(toJson(executionResult.getSkillsUsed()));
            evalResult.setReplayRecovered(executionResult.getReplayRecovered());
            evalResult.setCostUsageJson(toJson(executionResult.getCostUsage()));
            evalResult.setJudgeModel(scoreOutcome.getJudgeModel());
            evalResult.setJudgeReason(scoreOutcome.getJudgeReason());
            evalResult.setLatencyMs(executionResult.getLatencyMs());
            evalResultRepository.save(evalResult);
        } catch (Exception ex) {
            saveFailureResult(evalRun, evalCase.getId(), ex.getMessage());
        }
        refreshRunStats(evalRun.getId());
    }

    private EvalCaseExecutionResult executeCase(Long userId, EvalCase evalCase) {
        Instant start = Instant.now();
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTenantId(sysUserRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getTenantId());
        session.setTitle(buildEvalSessionTitle(evalCase.getQuestion()));
        session.setStatus("ACTIVE");
        session = agentSessionRepository.save(session);

        AgentRun run = new AgentRun();
        run.setSessionId(session.getId());
        run.setUserId(userId);
        run.setTenantId(session.getTenantId());
        run.setUserInput(evalCase.getQuestion());
        run.setStatus("QUEUED");
        run.setRuntimeType("LANGGRAPH");
        run.setExecutionMode("EVAL");
        run.setApprovalPolicy(resolveApprovalPolicy(evalCase));
        run.setOrchestrationMode(resolveOrchestrationMode(evalCase));
        run.setGraphName("enterprise-copilot");
        run.setGraphVersion("v2.1");
        run.setCurrentNode("intake_guardrail");
        run = agentRunRepository.save(run);

        try {
            RuntimeCommandResponse ignored = runtimeClient.startRun(RuntimeStartRunRequest.builder()
                    .runId(run.getId())
                    .sessionId(run.getSessionId())
                    .userId(userId)
                    .tenantId(run.getTenantId())
                    .userInput(evalCase.getQuestion())
                    .executionMode(run.getExecutionMode())
                    .approvalPolicy(run.getApprovalPolicy())
                    .orchestrationMode(run.getOrchestrationMode())
                    .waitForCompletion(true)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.RUNTIME_UNAVAILABLE, "Eval runtime 执行失败: " + ex.getMessage());
        }

        AgentRun refreshed = agentRunRepository.findById(run.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        List<AgentRunStep> steps = agentRunStepRepository.findByRunIdOrderByStepNoAsc(run.getId());
        return buildExecutionResult(refreshed, steps, Duration.between(start, Instant.now()).toMillis());
    }

    private EvalCaseExecutionResult buildExecutionResult(AgentRun run, List<AgentRunStep> steps, long fallbackLatencyMs) {
        List<AgentRunStep> toolSteps = steps.stream()
                .filter(step -> "TOOL_CALL".equalsIgnoreCase(step.getStepType()) && StringUtils.hasText(step.getToolName()))
                .toList();
        List<AgentRunStep> graphSteps = steps.stream()
                .filter(step -> !"TOOL_CALL".equalsIgnoreCase(step.getStepType()) && StringUtils.hasText(step.getNodeId()))
                .toList();

        Set<String> tools = new LinkedHashSet<>();
        toolSteps.forEach(step -> tools.add(step.getToolName()));
        Set<String> nodePath = new LinkedHashSet<>();
        graphSteps.forEach(step -> nodePath.add(step.getNodeId()));

        String route = graphSteps.stream()
                .map(step -> readStringField(step.getObservationJson(), "route"))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> graphSteps.stream()
                        .map(step -> readStringField(step.getStateAfterJson(), "route"))
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse("unknown"));

        String approvalDecision = graphSteps.stream()
                .map(step -> readStringField(step.getStateAfterJson(), "humanDecision"))
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second)
                .orElse(null);

        int retryCount = (int) graphSteps.stream()
                .filter(step -> "REVIEW".equalsIgnoreCase(step.getStepType()))
                .map(step -> readStringField(step.getObservationJson(), "decision"))
                .filter("replan"::equalsIgnoreCase)
                .count();

        List<Map<String, Object>> artifacts = readObjectList(run.getArtifactsJson());
        List<Map<String, Object>> citations = readObjectList(run.getCitationsJson());
        List<String> artifactTypes = artifacts.stream()
                .map(item -> String.valueOf(item.get("type")))
                .filter(StringUtils::hasText)
                .toList();

        return EvalCaseExecutionResult.builder()
                .decision(run.getStatus())
                .actualTool(tools.isEmpty() ? "ANSWER_DIRECTLY" : String.join(",", tools))
                .answerText(run.getFinalAnswer())
                .latencyMs(run.getTotalLatencyMs() == null ? fallbackLatencyMs : run.getTotalLatencyMs())
                .success("SUCCEEDED".equalsIgnoreCase(run.getStatus()))
                .errorMessage(run.getErrorMessage())
                .retryCount(retryCount)
                .citationCount(citations.size())
                .route(route)
                .approvalTriggered(nodePath.contains("human_approval"))
                .approvalDecision(StringUtils.hasText(approvalDecision) ? approvalDecision.toUpperCase() : (nodePath.contains("human_approval") ? "PENDING" : "NONE"))
                .orchestrationMode(run.getOrchestrationMode())
                .nodePath(List.copyOf(nodePath))
                .artifactTypes(artifactTypes)
                .toolTrace(new ArrayList<>(tools))
                .skillsUsed(steps.stream().map(AgentRunStep::getSkillName).filter(StringUtils::hasText).distinct().toList())
                .replayRecovered(Boolean.TRUE.equals(run.getReplayRecovered()))
                .costUsage(readJsonObject(run.getCostUsageJson()))
                .build();
    }

    private void saveFailureResult(EvalRun evalRun, Long caseId, String errorMessage) {
        EvalResult evalResult = evalResultRepository.findByEvalRunIdAndCaseId(evalRun.getId(), caseId).orElseGet(EvalResult::new);
        evalResult.setEvalRunId(evalRun.getId());
        evalResult.setCaseId(caseId);
        evalResult.setActualTool("FAILED");
        evalResult.setAnswerText(null);
        evalResult.setSuccess(false);
        evalResult.setScore(0D);
        evalResult.setRouteScore(0D);
        evalResult.setGroundingScore(0D);
        evalResult.setCitationScore(0D);
        evalResult.setFinalScore(0D);
        evalResult.setRetryCount(0);
        evalResult.setReason("执行失败: " + errorMessage);
        evalResult.setToolTraceJson("[]");
        evalResult.setNodePathJson("[]");
        evalResult.setApprovalTriggered(false);
        evalResult.setApprovalDecision("NONE");
        evalResult.setSkillsUsedJson("[]");
        evalResult.setReplayRecovered(false);
        evalResult.setCostUsageJson("{}");
        evalResult.setJudgeModel("rule-keyword");
        evalResult.setJudgeReason("执行失败，未进入 judge");
        evalResult.setLatencyMs(0L);
        evalResultRepository.save(evalResult);
    }

    private void refreshRunStats(Long evalRunId) {
        EvalRun evalRun = evalRunRepository.findById(evalRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVAL_RUN_NOT_FOUND));
        List<EvalResult> results = evalResultRepository.findByEvalRunIdOrderByIdAsc(evalRunId);
        evalRun.setFinishedCases(results.size());
        evalRun.setPassedCases((int) results.stream().filter(result -> Boolean.TRUE.equals(result.getSuccess())).count());
        evalRun.setAvgLatencyMs(results.isEmpty() ? 0L : Math.round(results.stream().map(EvalResult::getLatencyMs).filter(v -> v != null).mapToLong(Long::longValue).average().orElse(0D)));
        if (results.size() >= evalRun.getTotalCases()) {
            evalRun.setStatus("COMPLETED");
            evalRun.setFinishedAt(LocalDateTime.now());
        } else {
            evalRun.setStatus("RUNNING");
        }
        evalRunRepository.save(evalRun);
    }

    private EvalDataset getRequiredDataset(Long userId, Long datasetId) {
        return evalDatasetRepository.findByIdAndCreatedBy(datasetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVAL_DATASET_NOT_FOUND));
    }

    private EvalRun getRequiredRun(Long userId, Long runId) {
        return evalRunRepository.findByIdAndCreatedBy(runId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVAL_RUN_NOT_FOUND));
    }

    private EvalDatasetResponse toDatasetResponse(EvalDataset dataset, List<EvalCase> cases) {
        List<EvalCase> actualCases = cases == null ? evalCaseRepository.findByDatasetIdOrderByIdAsc(dataset.getId()) : cases;
        return EvalDatasetResponse.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .caseCount(actualCases.size())
                .createdAt(dataset.getCreatedAt())
                .cases(actualCases.stream().map(this::toCaseResponse).toList())
                .build();
    }

    private EvalCaseResponse toCaseResponse(EvalCase evalCase) {
        return EvalCaseResponse.builder()
                .id(evalCase.getId())
                .question(evalCase.getQuestion())
                .expectedTool(evalCase.getExpectedTool())
                .expectedRoute(evalCase.getExpectedRoute())
                .expectedKeywords(readStringList(evalCase.getExpectedKeywordsJson()))
                .expectedNodePath(readStringList(evalCase.getExpectedNodePathJson()))
                .expectedApprovalPolicy(evalCase.getExpectedApprovalPolicy())
                .expectedCitationMin(evalCase.getExpectedCitationMin())
                .expectedArtifactTypes(readStringList(evalCase.getExpectedArtifactTypesJson()))
                .expectedOrchestrationMode(evalCase.getExpectedOrchestrationMode())
                .expectedSkills(readStringList(evalCase.getExpectedSkillsJson()))
                .build();
    }

    private EvalRunResponse toRunResponse(EvalRun evalRun) {
        return EvalRunResponse.builder()
                .id(evalRun.getId())
                .datasetId(evalRun.getDatasetId())
                .status(evalRun.getStatus())
                .totalCases(evalRun.getTotalCases())
                .finishedCases(evalRun.getFinishedCases())
                .passedCases(evalRun.getPassedCases())
                .avgLatencyMs(evalRun.getAvgLatencyMs())
                .createdAt(evalRun.getCreatedAt())
                .finishedAt(evalRun.getFinishedAt())
                .build();
    }

    private EvalResultResponse toResultResponse(EvalResult evalResult) {
        return EvalResultResponse.builder()
                .id(evalResult.getId())
                .caseId(evalResult.getCaseId())
                .actualTool(evalResult.getActualTool())
                .answerText(evalResult.getAnswerText())
                .success(evalResult.getSuccess())
                .score(evalResult.getScore())
                .routeScore(evalResult.getRouteScore())
                .groundingScore(evalResult.getGroundingScore())
                .citationScore(evalResult.getCitationScore())
                .finalScore(evalResult.getFinalScore())
                .retryCount(evalResult.getRetryCount())
                .reason(evalResult.getReason())
                .toolTraceJson(evalResult.getToolTraceJson())
                .nodePathJson(evalResult.getNodePathJson())
                .approvalTriggered(evalResult.getApprovalTriggered())
                .approvalDecision(evalResult.getApprovalDecision())
                .skillsUsedJson(evalResult.getSkillsUsedJson())
                .replayRecovered(evalResult.getReplayRecovered())
                .costUsageJson(evalResult.getCostUsageJson())
                .judgeModel(evalResult.getJudgeModel())
                .judgeReason(evalResult.getJudgeReason())
                .latencyMs(evalResult.getLatencyMs())
                .createdAt(evalResult.getCreatedAt())
                .build();
    }

    private String buildEvalSessionTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "Eval Run";
        }
        String trimmed = question.trim();
        return trimmed.length() > 24 ? "Eval " + trimmed.substring(0, 24) + "..." : "Eval " + trimmed;
    }

    private String resolveApprovalPolicy(EvalCase evalCase) {
        if (!StringUtils.hasText(evalCase.getExpectedApprovalPolicy())) {
            return "AUTO_APPROVE";
        }
        String normalized = evalCase.getExpectedApprovalPolicy().trim().toUpperCase();
        if ("APPROVE".equals(normalized)) {
            return "AUTO_APPROVE";
        }
        if ("REJECT".equals(normalized)) {
            return "AUTO_REJECT";
        }
        if ("NONE".equals(normalized)) {
            return "MANUAL";
        }
        return normalized;
    }

    private String resolveOrchestrationMode(EvalCase evalCase) {
        if (!StringUtils.hasText(evalCase.getExpectedOrchestrationMode())) {
            return "SINGLE_GRAPH";
        }
        return evalCase.getExpectedOrchestrationMode().trim().toUpperCase();
    }

    private String readStringField(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}).get(fieldName) == null
                    ? null
                    : String.valueOf(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}).get(fieldName));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Map<String, Object>> readObjectList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
