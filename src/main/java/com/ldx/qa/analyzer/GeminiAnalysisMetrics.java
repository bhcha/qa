package com.ldx.qa.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gemini AI 분석 성능 메트릭 수집 및 모니터링 클래스
 */
public class GeminiAnalysisMetrics {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalysisMetrics.class);
    
    // 싱글톤 인스턴스
    private static final GeminiAnalysisMetrics INSTANCE = new GeminiAnalysisMetrics();
    
    // 메트릭 데이터
    private final AtomicInteger totalAnalyses = new AtomicInteger(0);
    private final AtomicInteger successfulAnalyses = new AtomicInteger(0);
    private final AtomicInteger failedAnalyses = new AtomicInteger(0);
    private final AtomicInteger fallbackUsed = new AtomicInteger(0);
    private final AtomicInteger jsonParsingFailures = new AtomicInteger(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong totalPromptLength = new AtomicLong(0);
    private final AtomicLong totalResponseLength = new AtomicLong(0);
    
    // 점수 분포 추적
    private final ConcurrentHashMap<String, AtomicInteger> scoreRanges = new ConcurrentHashMap<>();
    
    // 프롬프트 효과성 추적
    private final ConcurrentHashMap<String, PromptMetrics> promptMetrics = new ConcurrentHashMap<>();
    
    private GeminiAnalysisMetrics() {
        initializeScoreRanges();
    }
    
    public static GeminiAnalysisMetrics getInstance() {
        return INSTANCE;
    }
    
    private void initializeScoreRanges() {
        scoreRanges.put("0-20", new AtomicInteger(0));
        scoreRanges.put("21-40", new AtomicInteger(0));
        scoreRanges.put("41-60", new AtomicInteger(0));
        scoreRanges.put("61-80", new AtomicInteger(0));
        scoreRanges.put("81-100", new AtomicInteger(0));
    }
    
    /**
     * 분석 시작 기록
     */
    public AnalysisSession startAnalysis() {
        totalAnalyses.incrementAndGet();
        return new AnalysisSession();
    }
    
    /**
     * 성공적인 분석 완료 기록
     */
    public void recordSuccess(AnalysisSession session, int score, int promptLength, int responseLength, boolean usedFallback) {
        successfulAnalyses.incrementAndGet();
        
        if (usedFallback) {
            fallbackUsed.incrementAndGet();
        }
        
        // 실행 시간 기록
        long executionTime = session.getExecutionTime();
        totalExecutionTime.addAndGet(executionTime);
        
        // 프롬프트/응답 길이 기록
        totalPromptLength.addAndGet(promptLength);
        totalResponseLength.addAndGet(responseLength);
        
        // 점수 범위 기록
        recordScoreRange(score);
        
        logger.debug("Gemini 분석 성공 기록 - 점수: {}, 실행시간: {}ms, 프롬프트: {}chars, 응답: {}chars", 
            score, executionTime, promptLength, responseLength);
    }
    
    /**
     * 실패한 분석 기록
     */
    public void recordFailure(AnalysisSession session, String failureReason, boolean usedFallback) {
        failedAnalyses.incrementAndGet();
        
        if (usedFallback) {
            fallbackUsed.incrementAndGet();
        }
        
        // 실행 시간 기록 (실패한 경우도 성능 분석에 중요)
        long executionTime = session.getExecutionTime();
        totalExecutionTime.addAndGet(executionTime);
        
        logger.debug("Gemini 분석 실패 기록 - 이유: {}, 실행시간: {}ms", failureReason, executionTime);
    }
    
    /**
     * JSON 파싱 실패 기록
     */
    public void recordJsonParsingFailure() {
        jsonParsingFailures.incrementAndGet();
    }
    
    /**
     * 프롬프트 효과성 기록
     */
    public void recordPromptEffectiveness(String promptType, boolean successful, int score) {
        promptMetrics.computeIfAbsent(promptType, k -> new PromptMetrics()).record(successful, score);
    }
    
    /**
     * 점수 범위 기록
     */
    private void recordScoreRange(int score) {
        if (score <= 20) {
            scoreRanges.get("0-20").incrementAndGet();
        } else if (score <= 40) {
            scoreRanges.get("21-40").incrementAndGet();
        } else if (score <= 60) {
            scoreRanges.get("41-60").incrementAndGet();
        } else if (score <= 80) {
            scoreRanges.get("61-80").incrementAndGet();
        } else {
            scoreRanges.get("81-100").incrementAndGet();
        }
    }
    
    /**
     * 메트릭 요약 생성
     */
    public MetricsSummary generateSummary() {
        int total = totalAnalyses.get();
        int successful = successfulAnalyses.get();
        int failed = failedAnalyses.get();
        int fallback = fallbackUsed.get();
        
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        double fallbackRate = total > 0 ? (double) fallback / total * 100 : 0;
        double averageExecutionTime = total > 0 ? (double) totalExecutionTime.get() / total : 0;
        double averagePromptLength = total > 0 ? (double) totalPromptLength.get() / total : 0;
        double averageResponseLength = successful > 0 ? (double) totalResponseLength.get() / successful : 0;
        
        return new MetricsSummary(
            total, successful, failed, fallback, jsonParsingFailures.get(),
            successRate, fallbackRate, averageExecutionTime, 
            averagePromptLength, averageResponseLength,
            scoreRanges, promptMetrics
        );
    }
    
    /**
     * 메트릭 로그 출력
     */
    public void logMetrics() {
        MetricsSummary summary = generateSummary();
        
        logger.info("=== Gemini AI 분석 메트릭 요약 ===");
        logger.info("총 분석 수행: {}", summary.getTotalAnalyses());
        logger.info("성공률: {:.1f}% ({}/{})", summary.getSuccessRate(), summary.getSuccessfulAnalyses(), summary.getTotalAnalyses());
        logger.info("Fallback 사용률: {:.1f}% ({}회)", summary.getFallbackRate(), summary.getFallbackUsed());
        logger.info("JSON 파싱 실패: {}회", summary.getJsonParsingFailures());
        logger.info("평균 실행 시간: {:.0f}ms", summary.getAverageExecutionTime());
        logger.info("평균 프롬프트 길이: {:.0f} characters", summary.getAveragePromptLength());
        logger.info("평균 응답 길이: {:.0f} characters", summary.getAverageResponseLength());
        
        logger.info("점수 분포:");
        for (String range : scoreRanges.keySet()) {
            int count = scoreRanges.get(range).get();
            double percentage = summary.getTotalAnalyses() > 0 ? (double) count / summary.getTotalAnalyses() * 100 : 0;
            logger.info("  {}: {}회 ({:.1f}%)", range, count, percentage);
        }
        
        if (!promptMetrics.isEmpty()) {
            logger.info("프롬프트 유형별 효과성:");
            for (String promptType : promptMetrics.keySet()) {
                PromptMetrics metrics = promptMetrics.get(promptType);
                logger.info("  {}: 성공률 {:.1f}%, 평균점수 {:.1f}", 
                    promptType, metrics.getSuccessRate(), metrics.getAverageScore());
            }
        }
        
        logger.info("=== 메트릭 요약 완료 ===");
    }
    
    /**
     * 메트릭 초기화
     */
    public void resetMetrics() {
        totalAnalyses.set(0);
        successfulAnalyses.set(0);
        failedAnalyses.set(0);
        fallbackUsed.set(0);
        jsonParsingFailures.set(0);
        totalExecutionTime.set(0);
        totalPromptLength.set(0);
        totalResponseLength.set(0);
        
        scoreRanges.values().forEach(counter -> counter.set(0));
        promptMetrics.clear();
        
        logger.info("Gemini AI 분석 메트릭이 초기화되었습니다");
    }
    
    /**
     * 분석 세션 클래스
     */
    public static class AnalysisSession {
        private final LocalDateTime startTime;
        
        private AnalysisSession() {
            this.startTime = LocalDateTime.now();
        }
        
        public long getExecutionTime() {
            return Duration.between(startTime, LocalDateTime.now()).toMillis();
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
    }
    
    /**
     * 프롬프트 메트릭 클래스
     */
    private static class PromptMetrics {
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        private final AtomicInteger successfulRequests = new AtomicInteger(0);
        private final AtomicLong totalScore = new AtomicLong(0);
        
        public void record(boolean successful, int score) {
            totalRequests.incrementAndGet();
            if (successful) {
                successfulRequests.incrementAndGet();
                totalScore.addAndGet(score);
            }
        }
        
        public double getSuccessRate() {
            int total = totalRequests.get();
            return total > 0 ? (double) successfulRequests.get() / total * 100 : 0;
        }
        
        public double getAverageScore() {
            int successful = successfulRequests.get();
            return successful > 0 ? (double) totalScore.get() / successful : 0;
        }
    }
    
    /**
     * 메트릭 요약 클래스
     */
    public static class MetricsSummary {
        private final int totalAnalyses;
        private final int successfulAnalyses;
        private final int failedAnalyses;
        private final int fallbackUsed;
        private final int jsonParsingFailures;
        private final double successRate;
        private final double fallbackRate;
        private final double averageExecutionTime;
        private final double averagePromptLength;
        private final double averageResponseLength;
        private final ConcurrentHashMap<String, AtomicInteger> scoreRanges;
        private final ConcurrentHashMap<String, PromptMetrics> promptMetrics;
        
        public MetricsSummary(int totalAnalyses, int successfulAnalyses, int failedAnalyses,
                             int fallbackUsed, int jsonParsingFailures, double successRate,
                             double fallbackRate, double averageExecutionTime,
                             double averagePromptLength, double averageResponseLength,
                             ConcurrentHashMap<String, AtomicInteger> scoreRanges,
                             ConcurrentHashMap<String, PromptMetrics> promptMetrics) {
            this.totalAnalyses = totalAnalyses;
            this.successfulAnalyses = successfulAnalyses;
            this.failedAnalyses = failedAnalyses;
            this.fallbackUsed = fallbackUsed;
            this.jsonParsingFailures = jsonParsingFailures;
            this.successRate = successRate;
            this.fallbackRate = fallbackRate;
            this.averageExecutionTime = averageExecutionTime;
            this.averagePromptLength = averagePromptLength;
            this.averageResponseLength = averageResponseLength;
            this.scoreRanges = scoreRanges;
            this.promptMetrics = promptMetrics;
        }
        
        // Getters
        public int getTotalAnalyses() { return totalAnalyses; }
        public int getSuccessfulAnalyses() { return successfulAnalyses; }
        public int getFailedAnalyses() { return failedAnalyses; }
        public int getFallbackUsed() { return fallbackUsed; }
        public int getJsonParsingFailures() { return jsonParsingFailures; }
        public double getSuccessRate() { return successRate; }
        public double getFallbackRate() { return fallbackRate; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getAveragePromptLength() { return averagePromptLength; }
        public double getAverageResponseLength() { return averageResponseLength; }
    }
}