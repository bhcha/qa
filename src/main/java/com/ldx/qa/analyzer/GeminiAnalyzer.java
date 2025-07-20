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
 * 최적화된 Gemini AI 분석기
 * 단순화된 프롬프트와 강화된 파싱으로 신뢰성 향상
 */
public class GeminiAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalyzer.class);
    
    private final QaConfiguration config;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    private final GeminiFallbackAnalyzer fallbackAnalyzer;
    
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
        logger.info("=== 최적화된 Gemini AI 분석 시작 ===");
        
        // 메트릭 수집 시작
        GeminiAnalysisMetrics.AnalysisSession session = GeminiAnalysisMetrics.getInstance().startAnalysis();
        
        if (!isAvailable()) {
            logger.warn("Gemini AI 사용 불가 - 분석 건너뛰기");
            AnalysisResult result = createUnavailableResult();
            GeminiAnalysisMetrics.getInstance().recordFailure(session, "CLI 사용 불가", false);
            return result;
        }
        
        try {
            // 통합 분석 모드로 실행
            AnalysisResult result = performOptimizedAnalysis(projectPath);
            
            // 성공 메트릭 기록
            int score = (Integer) result.getMetrics().getOrDefault("aiScore", 0);
            boolean usedFallback = (Boolean) result.getMetrics().getOrDefault("fallbackAnalysisUsed", false);
            GeminiAnalysisMetrics.getInstance().recordSuccess(session, score, 0, 0, usedFallback);
            GeminiAnalysisMetrics.getInstance().recordPromptEffectiveness("unified", !"fail".equals(result.getStatus()), score);
            
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
     * 최적화된 분석 수행
     */
    private AnalysisResult performOptimizedAnalysis(Path projectPath) throws Exception {
        logger.info("최적화된 방식으로 Gemini 분석 수행");
        
        // 프롬프트 생성 (간소화된 버전)
        String prompt = promptBuilder.buildUnifiedAnalysisPrompt(projectPath, loadSimpleProjectContext(projectPath));
        
        // Gemini 실행
        String response = executeGeminiCommand(projectPath, prompt);
        
        // 강화된 파싱으로 응답 처리
        return responseParser.parseResponse(response);
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
        
        // 응답 읽기 (단순화)
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 대기 시간 단축 (5분)
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Gemini 명령 타임아웃 (5분 초과)");
        }
        
        int exitCode = process.exitValue();
        logger.info("Gemini 명령 완료, 종료 코드: {}", exitCode);
        
        String result = output.toString();
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
            // 기본 정보만 포함 (가이드라인 파일 참조 제거)
            context.append("Java 프로젝트 품질 분석 대상");
            
        } catch (Exception e) {
            logger.debug("프로젝트 컨텍스트 로드 중 오류: {}", e.getMessage());
            context.append("Java 프로젝트 품질 분석 대상");
        }
        
        return context.toString();
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
            .summary("🤖 Gemini AI 분석 중 오류 발생\n\n" +
                    "오류 내용: " + e.getMessage() + "\n\n" +
                    "Gemini CLI 설치 상태와 네트워크 연결을 확인해주세요.")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
}