package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 다단계 분석을 지원하는 Gemini AI 분석기
 * 코드품질, 아키텍처, 보안, 테스트를 개별적으로 분석한 후 통합
 */
public class GeminiMultiStageAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiMultiStageAnalyzer.class);
    
    private final QaConfiguration config;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    
    // 각 분석 단계별 가중치
    private static final Map<String, Double> STAGE_WEIGHTS = Map.of(
        "code_quality", 0.4,
        "architecture", 0.3,
        "testing", 0.2,
        "security", 0.1
    );
    
    public GeminiMultiStageAnalyzer(QaConfiguration config) {
        this.config = config;
        this.promptBuilder = new GeminiPromptBuilder();
        this.responseParser = new GeminiResponseParser();
    }
    
    /**
     * 다단계 분석 수행
     */
    public AnalysisResult performMultiStageAnalysis(Path projectPath) {
        logger.info("=== Gemini 다단계 분석 시작 ===");
        
        Map<String, AnalysisResult> stageResults = new HashMap<>();
        List<String> failedStages = new ArrayList<>();
        
        try {
            // 1단계: 코드 품질 분석
            logger.info("1단계: 코드 품질 분석 수행");
            AnalysisResult codeQualityResult = performStageAnalysis(projectPath, "code_quality");
            if (codeQualityResult != null) {
                stageResults.put("code_quality", codeQualityResult);
            } else {
                failedStages.add("code_quality");
            }
            
            // 2단계: 아키텍처 분석
            logger.info("2단계: 아키텍처 분석 수행");
            AnalysisResult architectureResult = performStageAnalysis(projectPath, "architecture");
            if (architectureResult != null) {
                stageResults.put("architecture", architectureResult);
            } else {
                failedStages.add("architecture");
            }
            
            // 3단계: 테스트 품질 분석
            logger.info("3단계: 테스트 품질 분석 수행");
            AnalysisResult testingResult = performStageAnalysis(projectPath, "testing");
            if (testingResult != null) {
                stageResults.put("testing", testingResult);
            } else {
                failedStages.add("testing");
            }
            
            // 4단계: 보안 분석
            logger.info("4단계: 보안 분석 수행");
            AnalysisResult securityResult = performStageAnalysis(projectPath, "security");
            if (securityResult != null) {
                stageResults.put("security", securityResult);
            } else {
                failedStages.add("security");
            }
            
            // 결과 통합
            return aggregateResults(stageResults, failedStages);
            
        } catch (Exception e) {
            logger.error("다단계 분석 중 오류 발생", e);
            return createMultiStageFailureResult(e);
        }
    }
    
    /**
     * 개별 단계 분석 수행
     */
    private AnalysisResult performStageAnalysis(Path projectPath, String stage) {
        try {
            String prompt = getPromptForStage(stage, projectPath);
            if (prompt == null) {
                logger.warn("{}단계 프롬프트 생성 실패", stage);
                return null;
            }
            
            String response = executeGeminiCommand(projectPath, prompt);
            AnalysisResult result = responseParser.parseResponse(response);
            
            // 단계별 메타데이터 추가
            if (result.getMetrics() instanceof HashMap) {
                ((HashMap<String, Object>) result.getMetrics()).put("analysisStage", stage);
            }
            
            logger.info("{}단계 분석 완료 - 점수: {}", stage, 
                result.getMetrics().getOrDefault("aiScore", 0));
            
            return result;
            
        } catch (Exception e) {
            logger.error("{}단계 분석 실패", stage, e);
            return null;
        }
    }
    
    /**
     * 단계별 프롬프트 생성
     */
    private String getPromptForStage(String stage, Path projectPath) {
        switch (stage) {
            case "code_quality":
                return promptBuilder.buildCodeQualityPrompt(projectPath);
            case "architecture":
                return promptBuilder.buildArchitecturePrompt(projectPath);
            case "testing":
                return promptBuilder.buildTestingPrompt(projectPath);
            case "security":
                return promptBuilder.buildSecurityPrompt(projectPath);
            default:
                logger.warn("알 수 없는 분석 단계: {}", stage);
                return null;
        }
    }
    
    /**
     * Gemini 명령 실행
     */
    private String executeGeminiCommand(Path projectPath, String prompt) throws Exception {
        List<String> command = Arrays.asList("gemini", "-m", config.getGeminiModel(), "-p", prompt);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(3, TimeUnit.MINUTES); // 단계별로 시간 단축
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Gemini 명령 타임아웃 (3분 초과)");
        }
        
        String result = output.toString();
        if (result.trim().isEmpty()) {
            throw new Exception("Gemini로부터 응답을 받지 못했습니다");
        }
        
        return result;
    }
    
    /**
     * 다단계 분석 결과 통합
     */
    private AnalysisResult aggregateResults(Map<String, AnalysisResult> stageResults, List<String> failedStages) {
        logger.info("다단계 분석 결과 통합 시작");
        
        if (stageResults.isEmpty()) {
            return createAllStagesFailedResult(failedStages);
        }
        
        // 가중평균으로 전체 점수 계산
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, AnalysisResult> entry : stageResults.entrySet()) {
            String stage = entry.getKey();
            AnalysisResult result = entry.getValue();
            
            double weight = STAGE_WEIGHTS.getOrDefault(stage, 0.0);
            int stageScore = (Integer) result.getMetrics().getOrDefault("aiScore", 0);
            
            totalScore += stageScore * weight;
            totalWeight += weight;
        }
        
        int aggregatedScore = totalWeight > 0 ? (int) Math.round(totalScore / totalWeight) : 0;
        
        // 모든 위반사항 수집
        List<Violation> allViolations = new ArrayList<>();
        for (AnalysisResult result : stageResults.values()) {
            allViolations.addAll(result.getViolations());
        }
        
        // 위반사항을 심각도별로 정렬
        allViolations.sort((v1, v2) -> {
            int priority1 = getSeverityPriority(v1.getSeverity());
            int priority2 = getSeverityPriority(v2.getSeverity());
            return Integer.compare(priority1, priority2);
        });
        
        // 통합 메트릭 생성
        Map<String, Object> aggregatedMetrics = createAggregatedMetrics(stageResults, aggregatedScore, failedStages);
        
        // 통합 요약 생성
        String summary = createAggregatedSummary(stageResults, failedStages, aggregatedScore, allViolations.size());
        
        String status = determineOverallStatus(aggregatedScore, allViolations, failedStages);
        
        return AnalysisResult.builder()
            .type("gemini_multistage")
            .status(status)
            .summary(summary)
            .violations(allViolations)
            .metrics(aggregatedMetrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 심각도 우선순위 반환
     */
    private int getSeverityPriority(String severity) {
        switch (severity != null ? severity.toLowerCase() : "") {
            case "error": return 1;
            case "warning": return 2;
            case "info": return 3;
            default: return 4;
        }
    }
    
    /**
     * 통합 메트릭 생성
     */
    private Map<String, Object> createAggregatedMetrics(Map<String, AnalysisResult> stageResults, 
                                                       int aggregatedScore, List<String> failedStages) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("aiScore", aggregatedScore);
        metrics.put("completedStages", stageResults.size());
        metrics.put("failedStages", failedStages.size());
        metrics.put("totalStages", STAGE_WEIGHTS.size());
        
        // 각 단계별 점수 추가
        for (Map.Entry<String, AnalysisResult> entry : stageResults.entrySet()) {
            String stage = entry.getKey();
            int stageScore = (Integer) entry.getValue().getMetrics().getOrDefault("aiScore", 0);
            metrics.put(stage + "Score", stageScore);
        }
        
        return metrics;
    }
    
    /**
     * 통합 요약 생성
     */
    private String createAggregatedSummary(Map<String, AnalysisResult> stageResults, List<String> failedStages,
                                         int aggregatedScore, int totalViolations) {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("🤖 Gemini AI 다단계 분석 완료 (종합점수: %d/100)\n\n", aggregatedScore));
        
        summary.append("📊 분석 현황:\n");
        summary.append(String.format("  • 완료된 단계: %d/%d\n", stageResults.size(), STAGE_WEIGHTS.size()));
        summary.append(String.format("  • 발견된 이슈: %d개\n", totalViolations));
        
        if (!failedStages.isEmpty()) {
            summary.append(String.format("  • 실패한 단계: %s\n", String.join(", ", failedStages)));
        }
        
        summary.append("\n🎯 단계별 결과:\n");
        for (Map.Entry<String, AnalysisResult> entry : stageResults.entrySet()) {
            String stage = entry.getKey();
            int stageScore = (Integer) entry.getValue().getMetrics().getOrDefault("aiScore", 0);
            String stageName = getStageName(stage);
            summary.append(String.format("  • %s: %d/100\n", stageName, stageScore));
        }
        
        summary.append("\n💡 다단계 분석을 통해 프로젝트의 품질을 종합적이고 체계적으로 평가했습니다.");
        
        return summary.toString();
    }
    
    /**
     * 단계명 한글 변환
     */
    private String getStageName(String stage) {
        switch (stage) {
            case "code_quality": return "코드 품질";
            case "architecture": return "아키텍처";
            case "testing": return "테스트 품질";
            case "security": return "보안";
            default: return stage;
        }
    }
    
    /**
     * 전체 상태 결정
     */
    private String determineOverallStatus(int aggregatedScore, List<Violation> violations, List<String> failedStages) {
        if (failedStages.size() >= STAGE_WEIGHTS.size() / 2) { // 절반 이상 실패
            return "fail";
        }
        
        if (aggregatedScore >= 70 && violations.stream().noneMatch(v -> "error".equals(v.getSeverity()))) {
            return "pass";
        }
        
        return "fail";
    }
    
    /**
     * 다단계 분석 실패 시 결과
     */
    private AnalysisResult createMultiStageFailureResult(Exception e) {
        return AnalysisResult.builder()
            .type("gemini_multistage")
            .status("fail")
            .summary("🤖 Gemini AI 다단계 분석 중 오류 발생\n\n" +
                    "오류 내용: " + e.getMessage())
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 모든 단계 실패 시 결과
     */
    private AnalysisResult createAllStagesFailedResult(List<String> failedStages) {
        return AnalysisResult.builder()
            .type("gemini_multistage")
            .status("fail")
            .summary("🤖 Gemini AI 다단계 분석 실패\n\n" +
                    "모든 분석 단계가 실패했습니다.\n" +
                    "실패한 단계: " + String.join(", ", failedStages))
            .violations(Collections.emptyList())
            .metrics(Map.of("aiScore", 0, "failedStages", failedStages.size()))
            .timestamp(LocalDateTime.now())
            .build();
    }
}