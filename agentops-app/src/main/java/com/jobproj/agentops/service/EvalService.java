package com.jobproj.agentops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.agent.AgentAnswerService;
import com.jobproj.agentops.agent.AgentPlannerService;
import com.jobproj.agentops.agent.EvalCaseExecutionResult;
import com.jobproj.agentops.agent.PlannerResult;
import com.jobproj.agentops.agent.ToolCallPlan;
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
import com.jobproj.agentops.entity.EvalCase;
import com.jobproj.agentops.entity.EvalDataset;
import com.jobproj.agentops.entity.EvalResult;
import com.jobproj.agentops.entity.EvalRun;
import com.jobproj.agentops.mq.EvalCaseMessage;
import com.jobproj.agentops.mq.EvalRunProducer;
import com.jobproj.agentops.repository.EvalCaseRepository;
import com.jobproj.agentops.repository.EvalDatasetRepository;
import com.jobproj.agentops.repository.EvalResultRepository;
import com.jobproj.agentops.repository.EvalRunRepository;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.tool.ToolExecutor;
import com.jobproj.agentops.tool.ToolPermissionService;
import com.jobproj.agentops.tool.ToolRegistry;
import com.jobproj.agentops.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final AgentPlannerService agentPlannerService;
    private final AgentAnswerService agentAnswerService;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionService toolPermissionService;
    private final EvalScoringService evalScoringService;
    private final ObjectMapper objectMapper;

    @Value("${agent.max-tool-calls:2}")
    private int maxToolCalls;

    @Transactional
    public EvalDatasetResponse createDataset(Long userId, CreateEvalDatasetRequest request) {
        EvalDataset dataset = new EvalDataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(userId);
        final EvalDataset savedDataset = evalDatasetRepository.save(dataset);

        List<EvalCase> cases = new ArrayList<>();
        request.getCases().forEach(item -> {
            EvalCase evalCase = new EvalCase();
            evalCase.setDatasetId(savedDataset.getId());
            evalCase.setQuestion(item.getQuestion());
            evalCase.setExpectedTool(item.getExpectedTool());
            evalCase.setExpectedKeywordsJson(toJson(item.getExpectedKeywords()));
            cases.add(evalCase);
        });
        evalCaseRepository.saveAll(cases);
        return toDatasetResponse(savedDataset, cases);
    }

    @Transactional(readOnly = true)
    public List<EvalDatasetResponse> listDatasets(Long userId) {
        return evalDatasetRepository.findByCreatedByOrderByIdDesc(userId).stream().map(dataset -> toDatasetResponse(dataset, null)).toList();
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
        return evalResultRepository.findByEvalRunIdOrderByIdAsc(runId).stream().map(this::toResultResponse).toList();
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
            EvalCaseExecutionResult executionResult = executeCase(message.getUserId(), evalCase.getQuestion());
            EvalScoringService.ScoreOutcome scoreOutcome = evalScoringService.score(evalCase, executionResult);
            EvalResult evalResult = new EvalResult();
            evalResult.setEvalRunId(evalRun.getId());
            evalResult.setCaseId(evalCase.getId());
            evalResult.setActualTool(executionResult.getActualTool());
            evalResult.setAnswerText(executionResult.getAnswerText());
            evalResult.setSuccess(scoreOutcome.isSuccess());
            evalResult.setScore(scoreOutcome.getScore());
            evalResult.setReason(scoreOutcome.getReason());
            evalResult.setLatencyMs(executionResult.getLatencyMs());
            evalResultRepository.save(evalResult);
        } catch (Exception ex) {
            saveFailureResult(evalRun, evalCase.getId(), ex.getMessage());
        }
        refreshRunStats(evalRun.getId());
    }

    private EvalCaseExecutionResult executeCase(Long userId, String question) {
        Instant start = Instant.now();
        PlannerResult plannerResult = agentPlannerService.plan(question, toolRegistry.listTools());
        List<ToolResult> toolResults = new ArrayList<>();
        List<String> actualTools = new ArrayList<>();
        if ("CALL_TOOLS".equalsIgnoreCase(plannerResult.getDecision()) && plannerResult.getToolCalls() != null) {
            int toolCallCount = 0;
            for (ToolCallPlan toolCallPlan : plannerResult.getToolCalls()) {
                if (toolCallCount >= maxToolCalls) {
                    break;
                }
                if (!toolPermissionService.canUse(userId, toolCallPlan.getToolName())) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户无权限调用工具: " + toolCallPlan.getToolName());
                }
                ToolExecutor toolExecutor = toolRegistry.getRequired(toolCallPlan.getToolName());
                ToolResult toolResult = toolExecutor.execute(toolCallPlan.getArguments(), ToolContext.builder().userId(userId).build());
                toolResults.add(toolResult);
                actualTools.add(toolExecutor.getName());
                toolCallCount++;
            }
        }
        return EvalCaseExecutionResult.builder()
                .decision(plannerResult.getDecision())
                .actualTool(actualTools.isEmpty() ? "ANSWER_DIRECTLY" : String.join(",", actualTools))
                .answerText(agentAnswerService.answer(question, toolResults))
                .latencyMs(Duration.between(start, Instant.now()).toMillis())
                .success(true)
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
        evalResult.setReason("执行失败: " + errorMessage);
        evalResult.setLatencyMs(0L);
        evalResultRepository.save(evalResult);
    }

    private void refreshRunStats(Long evalRunId) {
        EvalRun evalRun = evalRunRepository.findById(evalRunId).orElseThrow(() -> new BusinessException(ErrorCode.EVAL_RUN_NOT_FOUND));
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
        return evalDatasetRepository.findByIdAndCreatedBy(datasetId, userId).orElseThrow(() -> new BusinessException(ErrorCode.EVAL_DATASET_NOT_FOUND));
    }

    private EvalRun getRequiredRun(Long userId, Long runId) {
        return evalRunRepository.findByIdAndCreatedBy(runId, userId).orElseThrow(() -> new BusinessException(ErrorCode.EVAL_RUN_NOT_FOUND));
    }

    private EvalDatasetResponse toDatasetResponse(EvalDataset dataset, List<EvalCase> cases) {
        List<EvalCase> actualCases = cases == null ? evalCaseRepository.findByDatasetIdOrderByIdAsc(dataset.getId()) : cases;
        return EvalDatasetResponse.builder().id(dataset.getId()).name(dataset.getName()).description(dataset.getDescription()).caseCount(actualCases.size()).createdAt(dataset.getCreatedAt()).cases(actualCases.stream().map(this::toCaseResponse).toList()).build();
    }

    private EvalCaseResponse toCaseResponse(EvalCase evalCase) {
        return EvalCaseResponse.builder().id(evalCase.getId()).question(evalCase.getQuestion()).expectedTool(evalCase.getExpectedTool()).expectedKeywords(readKeywords(evalCase.getExpectedKeywordsJson())).build();
    }

    private EvalRunResponse toRunResponse(EvalRun evalRun) {
        return EvalRunResponse.builder().id(evalRun.getId()).datasetId(evalRun.getDatasetId()).status(evalRun.getStatus()).totalCases(evalRun.getTotalCases()).finishedCases(evalRun.getFinishedCases()).passedCases(evalRun.getPassedCases()).avgLatencyMs(evalRun.getAvgLatencyMs()).createdAt(evalRun.getCreatedAt()).finishedAt(evalRun.getFinishedAt()).build();
    }

    private EvalResultResponse toResultResponse(EvalResult evalResult) {
        return EvalResultResponse.builder().id(evalResult.getId()).caseId(evalResult.getCaseId()).actualTool(evalResult.getActualTool()).answerText(evalResult.getAnswerText()).success(evalResult.getSuccess()).score(evalResult.getScore()).reason(evalResult.getReason()).latencyMs(evalResult.getLatencyMs()).createdAt(evalResult.getCreatedAt()).build();
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

    private List<String> readKeywords(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}