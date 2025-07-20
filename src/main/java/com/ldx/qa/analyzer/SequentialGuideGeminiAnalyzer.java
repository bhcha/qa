package com.ldx.qa.analyzer;

import com.ldx.qa.analyzer.guide.GuideAnalysisResult;
import com.ldx.qa.analyzer.guide.GuideFileLoader;
import com.ldx.qa.analyzer.guide.GuideMetadata;
import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 지침별 완전 동기 순차 분석을 수행하는 Gemini AI 분석기
 */
public class SequentialGuideGeminiAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(SequentialGuideGeminiAnalyzer.class);
    
    private final QaConfiguration config;
    private final GuideFileLoader guideLoader;
    private final GuideSpecificPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    private final GeminiFallbackAnalyzer fallbackAnalyzer;
    
    public SequentialGuideGeminiAnalyzer(QaConfiguration config) {
        this.config = config;
        this.guideLoader = new GuideFileLoader(config);
        this.promptBuilder = new GuideSpecificPromptBuilder();
        this.responseParser = new GeminiResponseParser();
        this.fallbackAnalyzer = new GeminiFallbackAnalyzer();
    }
    
    @Override
    public String getName() {
        return "sequential-gemini";
    }
    
    @Override
    public String getType() {
        return "ai-sequential";
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
        logger.info("=== 지침별 순차 Gemini AI 분석 시작 ===");
        
        if (!isAvailable()) {
            logger.warn("Gemini CLI 사용 불가 - 분석 건너뛰기");
            return createUnavailableResult();
        }
        
        // 가이드 파일 로드
        List<GuideMetadata> guides = guideLoader.loadGuideFiles(projectPath);
        if (guides.isEmpty()) {
            logger.warn("분석할 가이드 파일이 없습니다");
            return createNoGuidesResult();
        }
        
        logger.info("총 {}개 가이드로 순차 분석 시작", guides.size());
        
        // 순차 분석 실행
        List<GuideAnalysisResult> guideResults = analyzeAllGuides(projectPath, guides);
        
        // 통합 결과 생성
        return combineResults(guideResults);
    }
    
    /**
     * 모든 가이드에 대해 완전 동기 순차 분석 실행
     */
    private List<GuideAnalysisResult> analyzeAllGuides(Path projectPath, List<GuideMetadata> guides) {
        List<GuideAnalysisResult> results = new ArrayList<>();
        
        for (int i = 0; i < guides.size(); i++) {
            GuideMetadata guide = guides.get(i);
            int currentStep = i + 1;
            int totalSteps = guides.size();
            
            try {
                logger.info("[{}/{}] {} 분석 중...", currentStep, totalSteps, guide.getDisplayName());
                
                // 개별 지침 분석 (완전 동기)
                GuideAnalysisResult result = analyzeWithGuide(projectPath, guide);
                results.add(result);
                
                logger.info("[{}/{}] {} 분석 완료 ✓ ({}초)", 
                           currentStep, totalSteps, guide.getDisplayName(), 
                           String.format("%.2f", result.getExecutionTimeInSeconds()));
                
                // 실시간 피드백 출력
                displayGuideFeedback(result, currentStep, totalSteps);
                
            } catch (Exception e) {
                logger.error("[{}/{}] {} 분석 실패: {}", 
                            currentStep, totalSteps, guide.getDisplayName(), e.getMessage());
                
                // 실패한 지침에 대한 fallback 결과 생성
                GuideAnalysisResult failureResult = createFailureResult(guide, e);
                results.add(failureResult);
            }
        }
        
        return results;
    }
    
    /**
     * 개별 가이드로 분석 수행 (완전 동기)
     */
    private GuideAnalysisResult analyzeWithGuide(Path projectPath, GuideMetadata guide) throws Exception {
        LocalDateTime startTime = LocalDateTime.now();
        
        // 가이드 내용 로드
        String guideContent = guideLoader.loadGuideContent(guide.getFilePath());
        if (guideContent.trim().isEmpty()) {
            throw new Exception("가이드 파일이 비어있습니다: " + guide.getFileName());
        }
        
        // 지침별 특화 프롬프트 생성
        String prompt = promptBuilder.buildPromptForGuide(projectPath, guide, guideContent);
        
        // Gemini 명령 실행 (완전 동기 대기)
        String response = executeGeminiCommand(projectPath, prompt);
        
        // 응답 파싱
        AnalysisResult baseResult = responseParser.parseResponse(response);
        
        // 실행 시간 계산
        Duration executionTime = Duration.between(startTime, LocalDateTime.now());
        
        // GuideAnalysisResult로 변환
        return GuideAnalysisResult.guideBuilder()
            .fromAnalysisResult(baseResult)
            .guideName(guide.getFileName())
            .guideDisplayName(guide.getDisplayName())
            .guideCategory(guide.getCategory())
            .executionTime(executionTime)
            .promptUsed(prompt)
            .responseLength(response.length())
            .build();
    }
    
    /**
     * Gemini 명령 실행 (완전 동기 대기)
     */
    private String executeGeminiCommand(Path projectPath, String prompt) throws Exception {
        List<String> command = Arrays.asList("gemini", "-m", config.getGeminiModel(), "-p", prompt);
        
        logger.debug("Gemini 명령 실행 - 프롬프트 길이: {} characters", prompt.length());
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        
        Process process = pb.start();
        
        // 응답 읽기
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 완전 동기 대기 (5분 타임아웃)
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Gemini 명령 타임아웃 (5분 초과)");
        }
        
        int exitCode = process.exitValue();
        logger.debug("Gemini 명령 완료, 종료 코드: {}", exitCode);
        
        String result = output.toString();
        if (result.trim().isEmpty()) {
            throw new Exception("Gemini로부터 응답을 받지 못했습니다");
        }
        
        return result;
    }
    
    /**
     * 여러 지침 분석 결과를 통합
     */
    private AnalysisResult combineResults(List<GuideAnalysisResult> guideResults) {
        if (guideResults.isEmpty()) {
            return createNoResultsError();
        }
        
        // 전체 상태 결정 (하나라도 fail이면 전체 fail)
        String overallStatus = guideResults.stream()
            .anyMatch(r -> "fail".equals(r.getStatus())) ? "fail" : "pass";
        
        // 통합 요약 생성
        String combinedSummary = buildCombinedSummary(guideResults);
        
        // 통합 메트릭 생성
        Map<String, Object> combinedMetrics = buildCombinedMetrics(guideResults);
        
        return AnalysisResult.builder()
            .type(getName())
            .status(overallStatus)
            .summary(combinedSummary)
            .violations(Collections.emptyList()) // 개별 지침에서 처리
            .metrics(combinedMetrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 통합 요약 생성
     */
    private String buildCombinedSummary(List<GuideAnalysisResult> results) {
        StringBuilder summary = new StringBuilder();
        summary.append("🤖 지침별 순차 Gemini AI 분석 완료\n\n");
        
        // 1. 분석 결과 요약
        summary.append("📊 분석 결과 요약:\n");
        for (GuideAnalysisResult result : results) {
            String statusIcon = "pass".equals(result.getStatus()) ? "✅" : 
                               "fail".equals(result.getStatus()) ? "❌" : "⚠️";
            summary.append(String.format("  %s %s (%.2f초)\n", 
                          statusIcon, result.getGuideDisplayName(), result.getExecutionTimeInSeconds()));
        }
        
        long passCount = results.stream().filter(r -> "pass".equals(r.getStatus())).count();
        double totalTime = results.stream().mapToDouble(GuideAnalysisResult::getExecutionTimeInSeconds).sum();
        
        summary.append(String.format("\n📈 전체 통계: %d/%d 성공, 총 %.2f초 소요\n\n", 
                                     passCount, results.size(), totalTime));
        
        // 2. 지침별 상세 피드백 섹션
        String detailSeparator = String.join("", Collections.nCopies(80, "="));
        summary.append(detailSeparator + "\n");
        summary.append("📋 지침별 상세 분석 피드백\n");
        summary.append(detailSeparator + "\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            GuideAnalysisResult result = results.get(i);
            
            // 지침별 섹션 헤더
            summary.append(String.format("🔍 [%d/%d] %s\n", 
                          i + 1, results.size(), result.getGuideDisplayName()));
            String sectionSeparator = String.join("", Collections.nCopies(60, "-"));
            summary.append(sectionSeparator + "\n");
            
            // 메타데이터
            summary.append(String.format("📁 지침 파일: %s\n", result.getGuideName()));
            summary.append(String.format("📂 카테고리: %s\n", getCategoryDisplayName(result.getGuideCategory())));
            summary.append(String.format("⏱️ 실행시간: %.2f초\n", result.getExecutionTimeInSeconds()));
            summary.append(String.format("✅ 상태: %s\n\n", result.getStatus()));
            
            // 피드백 내용
            summary.append(String.format("%s 분석 피드백:\n", getCategoryIcon(result.getGuideCategory())));
            summary.append(result.getSummary());
            summary.append("\n\n");
            
            // 구분선 (마지막 지침이 아닌 경우)
            if (i < results.size() - 1) {
                summary.append(detailSeparator + "\n\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * 카테고리별 표시명 반환
     */
    private String getCategoryDisplayName(String category) {
        switch (category != null ? category.toLowerCase() : "") {
            case "general": return "일반 코드 품질";
            case "security": return "보안";
            case "tdd": return "TDD";
            case "testing": return "테스팅";
            case "quality": return "품질 메트릭";
            default: return category != null ? category : "기타";
        }
    }
    
    /**
     * 카테고리별 아이콘 반환
     */
    private String getCategoryIcon(String category) {
        switch (category != null ? category.toLowerCase() : "") {
            case "general": return "🤖";
            case "security": return "🔒";
            case "tdd": return "🧪";
            case "testing": return "🎯";
            case "quality": return "📊";
            default: return "📋";
        }
    }
    
    /**
     * 통합 메트릭 생성
     */
    private Map<String, Object> buildCombinedMetrics(List<GuideAnalysisResult> results) {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("totalGuides", results.size());
        metrics.put("successfulGuides", results.stream().filter(r -> "pass".equals(r.getStatus())).count());
        metrics.put("failedGuides", results.stream().filter(r -> "fail".equals(r.getStatus())).count());
        metrics.put("totalExecutionTimeSeconds", results.stream().mapToDouble(GuideAnalysisResult::getExecutionTimeInSeconds).sum());
        metrics.put("averageExecutionTimeSeconds", results.stream().mapToDouble(GuideAnalysisResult::getExecutionTimeInSeconds).average().orElse(0.0));
        metrics.put("sequentialAnalysis", true);
        
        // 카테고리별 통계
        Map<String, Long> categoryStats = new HashMap<>();
        for (GuideAnalysisResult result : results) {
            categoryStats.merge(result.getGuideCategory(), 1L, Long::sum);
        }
        metrics.put("categoryStats", categoryStats);
        
        return metrics;
    }
    
    /**
     * 실패한 지침에 대한 결과 생성
     */
    private GuideAnalysisResult createFailureResult(GuideMetadata guide, Exception error) {
        return GuideAnalysisResult.guideBuilder()
            .type(getName())
            .status("fail")
            .summary(String.format("🤖 %s 분석 실패\n\n오류: %s", 
                                  guide.getDisplayName(), error.getMessage()))
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("error", error.getMessage()))
            .guideName(guide.getFileName())
            .guideDisplayName(guide.getDisplayName())
            .guideCategory(guide.getCategory())
            .executionTime(Duration.ZERO)
            .promptUsed("")
            .responseLength(0)
            .build();
    }
    
    /**
     * CLI 사용 불가 시 결과
     */
    private AnalysisResult createUnavailableResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("skipped")
            .summary("Gemini CLI가 사용 불가능하여 지침별 AI 분석을 건너뛰었습니다")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("reason", "CLI 사용 불가"))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 가이드 파일 없음 결과
     */
    private AnalysisResult createNoGuidesResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("skipped")
            .summary("config/ai 디렉토리에 분석할 가이드 파일이 없습니다")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("reason", "가이드 파일 없음"))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 결과 없음 오류
     */
    private AnalysisResult createNoResultsError() {
        return AnalysisResult.builder()
            .type(getName())
            .status("fail")
            .summary("지침별 분석 결과를 생성할 수 없습니다")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("error", "결과 없음"))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 지침별 피드백을 콘솔에 실시간 출력
     */
    private void displayGuideFeedback(GuideAnalysisResult result, int currentStep, int totalSteps) {
        // 구분선 출력
        String separator = String.join("", Collections.nCopies(60, "="));
        logger.info("\n" + separator);
        logger.info(result.getFormattedSectionHeader(currentStep, totalSteps));
        logger.info(separator);
        
        // 응답 길이 정보 표시
        String originalSummary = result.getSummary();
        logger.info("📊 응답 길이: {}자", originalSummary.length());
        if (originalSummary.length() > 3000) {
            logger.info("⚠️ 응답이 길어 일부 표시가 제한될 수 있습니다");
        } else if (originalSummary.length() <= 2000) {
            logger.info("✅ 적절한 길이의 응답");
        }
        logger.info("");
        
        // 피드백 내용 출력
        String formattedFeedback = result.getFormattedFeedback();
        
        // 피드백 내용을 그대로 출력 (줄바꿈 처리 제거)
        String[] lines = formattedFeedback.split("\n");
        for (String line : lines) {
            logger.info(line);
        }
        
        // 하단 구분선
        logger.info(separator + "\n");
    }
}