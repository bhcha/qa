package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 최적화된 Gemini AI 분석기 V2
 * 단순화된 프롬프트와 강화된 파싱을 제공
 */
public class GeminiAnalyzerV2 implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalyzerV2.class);
    
    private final QaConfiguration config;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    
    // 분석 모드 설정
    private boolean useMultiStageAnalysis = false;
    
    public GeminiAnalyzerV2(QaConfiguration config) {
        this.config = config;
        this.promptBuilder = new GeminiPromptBuilder();
        this.responseParser = new GeminiResponseParser();
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
            logger.debug("Gemini CLI not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("=== Gemini AI V2 분석 시작 ===");
        
        if (!isAvailable()) {
            logger.warn("Gemini CLI 사용 불가 - 분석 건너뛰기");
            return createUnavailableResult();
        }
        
        try {
            if (useMultiStageAnalysis) {
                return performMultiStageAnalysis(projectPath);
            } else {
                return performUnifiedAnalysis(projectPath);
            }
        } catch (Exception e) {
            logger.error("Gemini 분석 중 오류 발생", e);
            throw new AnalysisException("Gemini 분석 실패", e);
        }
    }
    
    /**
     * 통합 분석 수행 (기본 모드)
     */
    private AnalysisResult performUnifiedAnalysis(Path projectPath) {
        logger.info("통합 분석 모드로 Gemini 분석 수행");
        
        try {
            // 프로젝트 컨텍스트 로드
            String projectContext = loadProjectContext(projectPath);
            
            // 통합 분석 프롬프트 생성
            String prompt = promptBuilder.buildUnifiedAnalysisPrompt(projectPath, projectContext);
            
            // Gemini 실행
            String response = executeGeminiCommand(projectPath, prompt);
            
            // 응답 파싱
            return responseParser.parseResponse(response);
            
        } catch (Exception e) {
            logger.error("통합 분석 실행 실패", e);
            return createAnalysisFailureResult(e);
        }
    }
    
    /**
     * 다단계 분석 수행 (고급 모드)
     */
    private AnalysisResult performMultiStageAnalysis(Path projectPath) {
        logger.info("다단계 분석 모드로 Gemini 분석 수행");
        
        List<AnalysisResult> stageResults = new ArrayList<>();
        
        try {
            // 1단계: 코드 품질 분석
            logger.info("1단계: 코드 품질 분석 수행");
            AnalysisResult codeQualityResult = performCodeQualityAnalysis(projectPath);
            stageResults.add(codeQualityResult);
            
            // 2단계: 아키텍처 분석
            logger.info("2단계: 아키텍처 분석 수행");
            AnalysisResult architectureResult = performArchitectureAnalysis(projectPath);
            stageResults.add(architectureResult);
            
            // 3단계: 결과 통합
            logger.info("분석 결과 통합 중");
            return aggregateStageResults(stageResults);
            
        } catch (Exception e) {
            logger.error("다단계 분석 실행 실패", e);
            return createAnalysisFailureResult(e);
        }
    }
    
    /**
     * 코드 품질 전용 분석
     */
    private AnalysisResult performCodeQualityAnalysis(Path projectPath) {
        try {
            String prompt = promptBuilder.buildCodeQualityPrompt(projectPath);
            String response = executeGeminiCommand(projectPath, prompt);
            return responseParser.parseResponse(response);
        } catch (Exception e) {
            logger.error("코드 품질 분석 실패", e);
            return createStageFailureResult("code_quality", e);
        }
    }
    
    /**
     * 아키텍처 전용 분석
     */
    private AnalysisResult performArchitectureAnalysis(Path projectPath) {
        try {
            String prompt = promptBuilder.buildArchitecturePrompt(projectPath);
            String response = executeGeminiCommand(projectPath, prompt);
            return responseParser.parseResponse(response);
        } catch (Exception e) {
            logger.error("아키텍처 분석 실패", e);
            return createStageFailureResult("architecture", e);
        }
    }
    
    /**
     * Gemini 명령 실행
     */
    private String executeGeminiCommand(Path projectPath, String prompt) throws Exception {
        List<String> command = Arrays.asList("gemini", "-m", config.getGeminiModel(), "-p", prompt);
        
        logger.debug("Gemini 명령 실행: {}", String.join(" ", command).substring(0, 
            Math.min(100, String.join(" ", command).length())) + "...");
        
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
                output.append(line).append("\\n");
            }
        }
        
        // 에러 출력 읽기
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\\n");
            }
        }
        
        // 프로세스 완료 대기 (타임아웃: 5분)
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Gemini 명령 타임아웃 (5분 초과)");
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
     * 프로젝트 컨텍스트 로드 (간소화)
     */
    private String loadProjectContext(Path projectPath) {
        StringBuilder context = new StringBuilder();
        
        try {
            // CLAUDE.md가 있으면 핵심 정보만 추출
            Path claudeFile = projectPath.resolve("CLAUDE.md");
            if (claudeFile.toFile().exists()) {
                context.append("프로젝트 가이드라인이 정의되어 있음\\n");
            }
            
            // 프로젝트 구조 간단 요약
            context.append("Java 프로젝트 구조 분석 대상\\n");
            
        } catch (Exception e) {
            logger.debug("프로젝트 컨텍스트 로드 중 오류: {}", e.getMessage());
        }
        
        return context.toString();
    }
    
    /**
     * 다단계 분석 결과 통합
     */
    private AnalysisResult aggregateStageResults(List<AnalysisResult> stageResults) {
        // 전체 점수 계산 (평균)
        int totalScore = stageResults.stream()
            .mapToInt(result -> (Integer) result.getMetrics().getOrDefault("aiScore", 0))
            .sum() / stageResults.size();
        
        // 모든 위반사항 수집
        List<com.ldx.qa.model.Violation> allViolations = new ArrayList<>();
        stageResults.forEach(result -> allViolations.addAll(result.getViolations()));
        
        // 통합 메트릭 생성
        Map<String, Object> aggregatedMetrics = new HashMap<>();
        aggregatedMetrics.put("aiScore", totalScore);
        aggregatedMetrics.put("stageCount", stageResults.size());
        
        // 통합 요약 생성
        String summary = String.format(
            "🤖 Gemini AI 다단계 분석 완료 (총점: %d/100)\\n\\n" +
            "📊 분석 단계: %d개\\n" +
            "⚠️ 발견된 이슈: %d개\\n\\n" +
            "다단계 분석을 통해 더 정확하고 세밀한 품질 평가를 수행했습니다.",
            totalScore, stageResults.size(), allViolations.size()
        );
        
        String status = totalScore >= 70 ? "pass" : "fail";
        
        return AnalysisResult.builder()
            .type(getName())
            .status(status)
            .summary(summary)
            .violations(allViolations)
            .metrics(aggregatedMetrics)
            .timestamp(LocalDateTime.now())
            .build();
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
     * 분석 실패 시 결과
     */
    private AnalysisResult createAnalysisFailureResult(Exception e) {
        return AnalysisResult.builder()
            .type(getName())
            .status("fail")
            .summary("Gemini AI 분석 중 오류 발생: " + e.getMessage())
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 단계별 분석 실패 시 결과
     */
    private AnalysisResult createStageFailureResult(String stage, Exception e) {
        return AnalysisResult.builder()
            .type(getName() + "_" + stage)
            .status("fail")
            .summary(String.format("%s 분석 실패: %s", stage, e.getMessage()))
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 다단계 분석 모드 설정
     */
    public void setMultiStageAnalysis(boolean enabled) {
        this.useMultiStageAnalysis = enabled;
        logger.info("다단계 분석 모드: {}", enabled ? "활성화" : "비활성화");
    }
}