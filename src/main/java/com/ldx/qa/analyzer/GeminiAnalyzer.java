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
import java.util.concurrent.TimeUnit;

/**
 * 통합된 Gemini AI 분석기
 * 단일 분석, 다단계 분석, 순차 가이드 분석을 모두 지원
 */
public class GeminiAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalyzer.class);
    
    private final QaConfiguration config;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    private final GeminiFallbackAnalyzer fallbackAnalyzer;
    
    // 분석 모드
    public enum AnalysisMode {
        UNIFIED,        // 단일 통합 분석
        MULTI_STAGE,    // 다단계 분석
        SEQUENTIAL_GUIDE // 순차 가이드 분석
    }
    
    private AnalysisMode analysisMode = AnalysisMode.UNIFIED;
    
    // 다단계 분석 가중치
    private static final Map<String, Double> STAGE_WEIGHTS = Map.of(
        "code_quality", 0.4,
        "architecture", 0.3,
        "testing", 0.2,
        "security", 0.1
    );
    
    public GeminiAnalyzer(QaConfiguration config) {
        this.config = config;
        this.promptBuilder = new GeminiPromptBuilder();
        this.responseParser = new GeminiResponseParser();
        this.fallbackAnalyzer = new GeminiFallbackAnalyzer();
    }
    
    @Override
    public String getName() {
        return "gemini";
    }
    
    @Override
    public String getType() {
        return "ai";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gemini", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("Gemini CLI 사용 불가: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("=== Gemini AI 분석 시작 (모드: {}) ===", analysisMode);
        
        // 메트릭 수집 시작
        GeminiAnalysisMetrics.AnalysisSession session = GeminiAnalysisMetrics.getInstance().startAnalysis();
        
        if (!isAvailable()) {
            logger.warn("Gemini AI 사용 불가 - 분석 건너뛰기");
            AnalysisResult result = createUnavailableResult();
            GeminiAnalysisMetrics.getInstance().recordFailure(session, "CLI 사용 불가", false);
            return result;
        }
        
        try {
            AnalysisResult result;
            
            switch (analysisMode) {
                case MULTI_STAGE:
                    result = performMultiStageAnalysis(projectPath);
                    break;
                case SEQUENTIAL_GUIDE:
                    result = performSequentialGuideAnalysis(projectPath);
                    break;
                case UNIFIED:
                default:
                    result = performUnifiedAnalysis(projectPath);
                    break;
            }
            
            // 성공 메트릭 기록
            int score = (Integer) result.getMetrics().getOrDefault("aiScore", 0);
            boolean usedFallback = (Boolean) result.getMetrics().getOrDefault("fallbackAnalysisUsed", false);
            GeminiAnalysisMetrics.getInstance().recordSuccess(session, score, 0, 0, usedFallback);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Gemini 분석 중 오류 발생", e);
            AnalysisResult result = fallbackAnalyzer.createIntelligentFallback(projectPath, null, 
                "Gemini 실행 오류: " + e.getMessage());
            
            // 실패 메트릭 기록
            GeminiAnalysisMetrics.getInstance().recordFailure(session, e.getMessage(), true);
            
            return result;
        }
    }
    
    /**
     * 통합 분석 수행 (기본 모드)
     */
    private AnalysisResult performUnifiedAnalysis(Path projectPath) throws Exception {
        logger.info("통합 분석 모드로 Gemini 분석 수행");
        
        // 프로젝트 컨텍스트 로드 (간소화)
        String projectContext = loadSimpleProjectContext(projectPath);
        
        // 통합 분석 프롬프트 생성
        String prompt = promptBuilder.buildUnifiedAnalysisPrompt(projectPath, projectContext);
        
        // Gemini 실행
        String response = executeGeminiCommand(projectPath, prompt);
        
        // 응답 파싱
        return responseParser.parseResponse(response);
    }
    
    /**
     * 다단계 분석 수행
     */
    private AnalysisResult performMultiStageAnalysis(Path projectPath) throws Exception {
        logger.info("다단계 분석 모드로 Gemini 분석 수행");
        
        Map<String, AnalysisResult> stageResults = new HashMap<>();
        List<String> failedStages = new ArrayList<>();
        
        // 각 단계별 분석 수행
        for (String stage : STAGE_WEIGHTS.keySet()) {
            logger.info("{}단계 분석 수행", getStageName(stage));
            
            try {
                String prompt = getPromptForStage(stage, projectPath);
                if (prompt != null) {
                    String response = executeGeminiCommand(projectPath, prompt);
                    AnalysisResult result = responseParser.parseResponse(response);
                    
                    // 단계별 메타데이터 추가
                    if (result.getMetrics() instanceof HashMap) {
                        ((HashMap<String, Object>) result.getMetrics()).put("analysisStage", stage);
                    }
                    
                    stageResults.put(stage, result);
                    logger.info("{}단계 분석 완료 - 점수: {}", stage, 
                        result.getMetrics().getOrDefault("aiScore", 0));
                } else {
                    failedStages.add(stage);
                }
            } catch (Exception e) {
                logger.error("{}단계 분석 실패", stage, e);
                failedStages.add(stage);
            }
        }
        
        return aggregateMultiStageResults(stageResults, failedStages);
    }
    
    /**
     * 순차 가이드 분석 수행
     */
    private AnalysisResult performSequentialGuideAnalysis(Path projectPath) throws Exception {
        logger.info("순차 가이드 분석 모드로 Gemini 분석 수행");
        
        // SequentialGuideGeminiAnalyzer 로직 통합
        SequentialGuideGeminiAnalyzer sequentialAnalyzer = new SequentialGuideGeminiAnalyzer(config);
        return sequentialAnalyzer.analyze(projectPath);
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
        
        logger.debug("Gemini 명령 실행 - 프롬프트 길이: {} characters", prompt.length());
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        
        Process process = pb.start();
        
        // 응답 읽기
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        
        // 표준 출력 읽기
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 에러 출력 읽기
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }
        
        // 타임아웃 설정 (분석 모드에 따라 조정)
        int timeoutMinutes = analysisMode == AnalysisMode.MULTI_STAGE ? 3 : 5;
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Gemini 명령 타임아웃 (" + timeoutMinutes + "분 초과)");
        }
        
        int exitCode = process.exitValue();
        logger.info("Gemini 명령 완료, 종료 코드: {}", exitCode);
        
        String result = output.toString();
        if (result.trim().isEmpty() && !errorOutput.toString().trim().isEmpty()) {
            logger.info("표준 출력이 비어있음, 에러 출력 사용");
            result = errorOutput.toString();
        }
        
        if (result.trim().isEmpty()) {
            throw new Exception("Gemini로부터 응답을 받지 못했습니다");
        }
        
        logger.debug("Gemini 응답 길이: {} characters", result.length());
        return result;
    }
    
    /**
     * 간단한 프로젝트 컨텍스트 로드
     */
    private String loadSimpleProjectContext(Path projectPath) {
        StringBuilder context = new StringBuilder();
        
        try {
            // CLAUDE.md가 있으면 핵심 정보만 추출
            Path claudeFile = projectPath.resolve("CLAUDE.md");
            if (claudeFile.toFile().exists()) {
                context.append("프로젝트 가이드라인이 정의되어 있음\n");
            }
            
            // 프로젝트 구조 간단 요약
            context.append("Java 프로젝트 구조 분석 대상\n");
            
        } catch (Exception e) {
            logger.debug("프로젝트 컨텍스트 로드 중 오류: {}", e.getMessage());
            context.append("Java 프로젝트 품질 분석 대상");
        }
        
        return context.toString();
    }
    
    /**
     * 다단계 분석 결과 통합
     */
    private AnalysisResult aggregateMultiStageResults(Map<String, AnalysisResult> stageResults, List<String> failedStages) {
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
     * CLI 사용 불가 시 결과
     */
    private AnalysisResult createUnavailableResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("skipped")
            .summary("Gemini CLI가 사용 불가능하여 AI 분석을 건너뛰었습니다")
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
    
    // === 설정 메서드들 ===
    
    /**
     * 분석 모드 설정
     */
    public void setAnalysisMode(AnalysisMode mode) {
        this.analysisMode = mode;
        logger.info("Gemini 분석 모드 변경: {}", mode);
    }
    
    /**
     * 현재 분석 모드 반환
     */
    public AnalysisMode getAnalysisMode() {
        return analysisMode;
    }
}