package com.ldx.qa.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini AI 응답을 단순 텍스트로 처리하는 파서 클래스
 * JSON 파싱을 제거하고 원본 응답을 그대로 활용
 */
public class GeminiResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(GeminiResponseParser.class);
    
    // 더 이상 점수 추출 불필요
    
    /**
     * Gemini 응답을 분석하여 AnalysisResult로 변환
     */
    public AnalysisResult parseResponse(String rawOutput) {
        logger.info("=== Gemini 응답 텍스트 처리 시작 ===");
        logger.debug("Raw output length: {} characters", rawOutput.length());
        
        try {
            // 1단계: 응답 전처리
            String cleanedOutput = preprocessGeminiOutput(rawOutput);
            
            // 2단계: 순수 텍스트 기반 AnalysisResult 객체 생성
            return buildPureTextAnalysisResult(cleanedOutput);
            
        } catch (Exception e) {
            logger.error("Gemini 응답 처리 중 오류 발생", e);
            return createErrorFallback(rawOutput, e);
        }
    }
    
    /**
     * 순수 텍스트 기반 AnalysisResult 생성 (점수 없음)
     */
    private AnalysisResult buildPureTextAnalysisResult(String cleanedOutput) {
        logger.info("순수 텍스트 기반 분석 결과 생성");
        
        // 메트릭 생성 (점수 제거)
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("textAnalysis", true);
        metrics.put("responseLength", cleanedOutput.length());
        
        // 요약 생성 (점수 없는 버전)
        String summary = buildSimpleTextSummary(cleanedOutput);
        
        return AnalysisResult.builder()
            .type("gemini")
            .status("pass") // AI 피드백은 항상 pass (정보 제공 목적)
            .summary(summary)
            .violations(Collections.emptyList()) // 구조화된 위반사항 없음
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 단순 텍스트 요약 생성 (점수 없음)
     */
    private String buildSimpleTextSummary(String originalOutput) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("🤖 Gemini AI 분석 피드백\n\n");
        
        // 원본 응답이 너무 길면 적절히 자르기
        if (originalOutput.length() > 3000) {
            summary.append(originalOutput.substring(0, 2500));
            summary.append("\n\n[피드백이 길어 일부 생략됨]");
        } else {
            summary.append(originalOutput);
        }
        
        return summary.toString();
    }
    
    /**
     * Gemini CLI 출력 전처리 (시스템 메시지 제거)
     */
    private String preprocessGeminiOutput(String response) {
        if (response == null) return "";
        
        String cleaned = response.trim();
        
        // Gemini CLI 시스템 메시지들 제거
        String[] systemMessages = {
            "Loaded cached credentials.",
            "Using cached credentials from",
            "Authentication successful",
            "Connected to Gemini",
            "Model initialized"
        };
        
        for (String message : systemMessages) {
            cleaned = cleaned.replace(message, "");
        }
        
        // 연속된 개행 문자 정리
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
    
    /**
     * 오류 발생 시 fallback 결과 생성
     */
    private AnalysisResult createErrorFallback(String rawOutput, Exception error) {
        return AnalysisResult.builder()
            .type("gemini")
            .status("fail")
            .summary("🤖 Gemini AI 분석 오류\n\n" +
                    "응답 처리 중 오류가 발생했습니다.\n" +
                    "오류: " + error.getMessage() + "\n\n" +
                    "원본 응답: " + (rawOutput.length() > 200 ? 
                    rawOutput.substring(0, 200) + "..." : rawOutput))
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
}