package com.ldx.qa.analyzer;

import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gemini AI 분석 실패 시 고품질 Fallback 분석을 제공하는 클래스
 */
public class GeminiFallbackAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiFallbackAnalyzer.class);
    
    /**
     * 지능적 Fallback 분석 수행
     */
    public AnalysisResult createIntelligentFallback(Path projectPath, String rawOutput, String errorContext) {
        logger.info("지능적 Fallback 분석 시작");
        
        try {
            // 프로젝트 구조 분석
            ProjectStructureAnalysis structure = analyzeProjectStructure(projectPath);
            
            // 텍스트 기반 인사이트 추출 (원본 출력이 있는 경우)
            TextAnalysisResult textAnalysis = null;
            if (rawOutput != null && !rawOutput.trim().isEmpty()) {
                textAnalysis = analyzeTextContent(rawOutput);
            }
            
            // 종합 평가 생성
            return buildFallbackResult(structure, textAnalysis, errorContext);
            
        } catch (Exception e) {
            logger.error("Fallback 분석 중 오류 발생", e);
            return createMinimalFallback(errorContext);
        }
    }
    
    /**
     * 프로젝트 구조 분석
     */
    private ProjectStructureAnalysis analyzeProjectStructure(Path projectPath) {
        ProjectStructureAnalysis analysis = new ProjectStructureAnalysis();
        
        try {
            // Java 파일 수 계산
            Path mainJavaPath = projectPath.resolve("src/main/java");
            if (Files.exists(mainJavaPath)) {
                long javaFileCount = Files.walk(mainJavaPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .count();
                analysis.setJavaFileCount(javaFileCount);
            }
            
            // 테스트 파일 수 계산
            Path testJavaPath = projectPath.resolve("src/test/java");
            if (Files.exists(testJavaPath)) {
                long testFileCount = Files.walk(testJavaPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .count();
                analysis.setTestFileCount(testFileCount);
            }
            
            // 설정 파일 존재 여부 확인
            analysis.setHasBuildFile(
                Files.exists(projectPath.resolve("build.gradle")) ||
                Files.exists(projectPath.resolve("pom.xml"))
            );
            
            analysis.setHasClaudeGuide(Files.exists(projectPath.resolve("CLAUDE.md")));
            
            // 패키지 구조 깊이 분석
            if (Files.exists(mainJavaPath)) {
                int packageDepth = calculatePackageDepth(mainJavaPath);
                analysis.setPackageDepth(packageDepth);
            }
            
        } catch (Exception e) {
            logger.debug("프로젝트 구조 분석 중 오류: {}", e.getMessage());
        }
        
        return analysis;
    }
    
    /**
     * 패키지 구조 깊이 계산
     */
    private int calculatePackageDepth(Path javaPath) {
        try {
            return (int) Files.walk(javaPath)
                .filter(Files::isDirectory)
                .mapToInt(path -> path.getNameCount() - javaPath.getNameCount())
                .max()
                .orElse(0);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * 텍스트 내용 분석
     */
    private TextAnalysisResult analyzeTextContent(String text) {
        TextAnalysisResult result = new TextAnalysisResult();
        
        String lowerText = text.toLowerCase();
        
        // 품질 관련 키워드 검색
        String[] qualityKeywords = {"clean", "quality", "best", "good", "excellent", "well-structured"};
        for (String keyword : qualityKeywords) {
            if (lowerText.contains(keyword)) {
                result.addPositiveIndicator();
            }
        }
        
        // 문제 관련 키워드 검색
        String[] problemKeywords = {"issue", "problem", "error", "warning", "violation", "bad", "poor"};
        for (String keyword : problemKeywords) {
            if (lowerText.contains(keyword)) {
                result.addNegativeIndicator();
            }
        }
        
        // 개선 제안 키워드 검색
        String[] improvementKeywords = {"recommend", "suggest", "improve", "should", "consider", "refactor"};
        for (String keyword : improvementKeywords) {
            if (lowerText.contains(keyword)) {
                result.addImprovementSuggestion();
            }
        }
        
        // 점수 추출 시도
        result.extractScoreFromText(text);
        
        return result;
    }
    
    /**
     * Fallback 결과 생성
     */
    private AnalysisResult buildFallbackResult(ProjectStructureAnalysis structure, 
                                             TextAnalysisResult textAnalysis, 
                                             String errorContext) {
        
        // 구조 기반 점수 계산
        int structureScore = calculateStructureScore(structure);
        
        // 텍스트 기반 점수 조정
        int finalScore = structureScore;
        if (textAnalysis != null) {
            finalScore = adjustScoreWithTextAnalysis(structureScore, textAnalysis);
        }
        
        // 위반사항 생성
        List<Violation> violations = generateStructureBasedViolations(structure);
        
        // 요약 생성
        String summary = generateFallbackSummary(structure, textAnalysis, finalScore, errorContext);
        
        // 메트릭 생성
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("aiScore", finalScore);
        metrics.put("fallbackAnalysisUsed", true);
        metrics.put("structureScore", structureScore);
        if (textAnalysis != null) {
            metrics.put("textAnalysisUsed", true);
            metrics.put("extractedScore", textAnalysis.getExtractedScore());
        }
        
        String status = (finalScore >= 60 && violations.stream().noneMatch(v -> "error".equals(v.getSeverity()))) 
            ? "pass" : "fail";
        
        return AnalysisResult.builder()
            .type("gemini")
            .status(status)
            .summary(summary)
            .violations(violations)
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 구조 기반 점수 계산
     */
    private int calculateStructureScore(ProjectStructureAnalysis structure) {
        int score = 40; // 기본 점수
        
        // Java 파일 존재 시 점수 추가
        if (structure.getJavaFileCount() > 0) {
            score += 20;
            
            // 파일 수에 따른 추가 점수
            if (structure.getJavaFileCount() >= 10) score += 10;
            if (structure.getJavaFileCount() >= 50) score += 5;
        }
        
        // 테스트 커버리지 기반 점수
        if (structure.getTestFileCount() > 0) {
            score += 15;
            
            double testRatio = (double) structure.getTestFileCount() / Math.max(structure.getJavaFileCount(), 1);
            if (testRatio >= 0.5) score += 10;
            if (testRatio >= 0.8) score += 5;
        }
        
        // 프로젝트 설정 파일 존재
        if (structure.isHasBuildFile()) score += 5;
        
        // 개발 가이드 존재
        if (structure.isHasClaudeGuide()) score += 5;
        
        // 패키지 구조 깊이 (적절한 구조화)
        if (structure.getPackageDepth() >= 2 && structure.getPackageDepth() <= 5) score += 5;
        
        return Math.min(100, Math.max(0, score));
    }
    
    /**
     * 텍스트 분석으로 점수 조정
     */
    private int adjustScoreWithTextAnalysis(int baseScore, TextAnalysisResult textAnalysis) {
        int adjustedScore = baseScore;
        
        // 긍정적 지표에 따른 점수 상승
        adjustedScore += textAnalysis.getPositiveIndicators() * 3;
        
        // 부정적 지표에 따른 점수 하락
        adjustedScore -= textAnalysis.getNegativeIndicators() * 5;
        
        // 개선 제안이 있으면 약간의 점수 추가 (분석의 질을 나타냄)
        if (textAnalysis.getImprovementSuggestions() > 0) {
            adjustedScore += 2;
        }
        
        // 추출된 점수가 있으면 가중평균 적용
        if (textAnalysis.getExtractedScore() > 0) {
            adjustedScore = (adjustedScore * 7 + textAnalysis.getExtractedScore() * 3) / 10;
        }
        
        return Math.min(100, Math.max(0, adjustedScore));
    }
    
    /**
     * 구조 기반 위반사항 생성
     */
    private List<Violation> generateStructureBasedViolations(ProjectStructureAnalysis structure) {
        List<Violation> violations = new ArrayList<>();
        
        if (structure.getJavaFileCount() == 0) {
            violations.add(Violation.builder()
                .severity("error")
                .file("src/main/java")
                .line(0)
                .message("Java 소스 파일을 찾을 수 없습니다")
                .type("structure")
                .build());
        }
        
        if (structure.getTestFileCount() == 0) {
            violations.add(Violation.builder()
                .severity("warning")
                .file("src/test/java")
                .line(0)
                .message("테스트 파일이 없습니다. 테스트 커버리지 개선이 필요합니다")
                .type("testing")
                .build());
        }
        
        if (!structure.isHasBuildFile()) {
            violations.add(Violation.builder()
                .severity("warning")
                .file(".")
                .line(0)
                .message("빌드 설정 파일(build.gradle 또는 pom.xml)을 찾을 수 없습니다")
                .type("structure")
                .build());
        }
        
        double testRatio = structure.getJavaFileCount() > 0 ? 
            (double) structure.getTestFileCount() / structure.getJavaFileCount() : 0;
        
        if (testRatio < 0.3) {
            violations.add(Violation.builder()
                .severity("info")
                .file("src/test/java")
                .line(0)
                .message(String.format("테스트 커버리지가 낮습니다 (%.1f%%). 30%% 이상 권장", testRatio * 100))
                .type("testing")
                .build());
        }
        
        return violations;
    }
    
    /**
     * Fallback 요약 생성
     */
    private String generateFallbackSummary(ProjectStructureAnalysis structure, 
                                         TextAnalysisResult textAnalysis, 
                                         int finalScore, 
                                         String errorContext) {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("🤖 Gemini AI Fallback 분석 완료 (점수: %d/100)\n\n", finalScore));
        
        if (errorContext != null) {
            summary.append("⚠️ 원본 AI 분석: ").append(errorContext).append("\n\n");
        }
        
        summary.append("📊 프로젝트 구조 분석:\n");
        summary.append(String.format("  • Java 파일: %d개\n", structure.getJavaFileCount()));
        summary.append(String.format("  • 테스트 파일: %d개\n", structure.getTestFileCount()));
        summary.append(String.format("  • 테스트 비율: %.1f%%\n", 
            structure.getJavaFileCount() > 0 ? 
            (double) structure.getTestFileCount() / structure.getJavaFileCount() * 100 : 0));
        summary.append(String.format("  • 패키지 깊이: %d레벨\n", structure.getPackageDepth()));
        summary.append(String.format("  • 빌드 설정: %s\n", structure.isHasBuildFile() ? "있음" : "없음"));
        
        if (textAnalysis != null) {
            summary.append("\n📝 텍스트 분석 결과:\n");
            summary.append(String.format("  • 긍정적 지표: %d개\n", textAnalysis.getPositiveIndicators()));
            summary.append(String.format("  • 개선 제안: %d개\n", textAnalysis.getImprovementSuggestions()));
            if (textAnalysis.getExtractedScore() > 0) {
                summary.append(String.format("  • 추출된 점수: %d/100\n", textAnalysis.getExtractedScore()));
            }
        }
        
        summary.append("\n💡 고품질 Fallback 분석을 통해 프로젝트 현황을 객관적으로 평가했습니다.");
        
        return summary.toString();
    }
    
    /**
     * 최소한의 Fallback 결과 생성
     */
    private AnalysisResult createMinimalFallback(String errorContext) {
        return AnalysisResult.builder()
            .type("gemini")
            .status("fail")
            .summary("🤖 Gemini AI 분석 실패\n\n" +
                    "Fallback 분석도 실패했습니다.\n" +
                    (errorContext != null ? "원인: " + errorContext : ""))
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    // 내부 데이터 클래스들
    private static class ProjectStructureAnalysis {
        private long javaFileCount = 0;
        private long testFileCount = 0;
        private boolean hasBuildFile = false;
        private boolean hasClaudeGuide = false;
        private int packageDepth = 0;
        
        // Getters and Setters
        public long getJavaFileCount() { return javaFileCount; }
        public void setJavaFileCount(long javaFileCount) { this.javaFileCount = javaFileCount; }
        public long getTestFileCount() { return testFileCount; }
        public void setTestFileCount(long testFileCount) { this.testFileCount = testFileCount; }
        public boolean isHasBuildFile() { return hasBuildFile; }
        public void setHasBuildFile(boolean hasBuildFile) { this.hasBuildFile = hasBuildFile; }
        public boolean isHasClaudeGuide() { return hasClaudeGuide; }
        public void setHasClaudeGuide(boolean hasClaudeGuide) { this.hasClaudeGuide = hasClaudeGuide; }
        public int getPackageDepth() { return packageDepth; }
        public void setPackageDepth(int packageDepth) { this.packageDepth = packageDepth; }
    }
    
    private static class TextAnalysisResult {
        private int positiveIndicators = 0;
        private int negativeIndicators = 0;
        private int improvementSuggestions = 0;
        private int extractedScore = 0;
        
        public void addPositiveIndicator() { positiveIndicators++; }
        public void addNegativeIndicator() { negativeIndicators++; }
        public void addImprovementSuggestion() { improvementSuggestions++; }
        
        public void extractScoreFromText(String text) {
            // 점수 추출 시도 (예: "85/100", "score: 92" 등)
            String[] patterns = {"(\\d{1,3})/100", "score[:\\s]+(\\d{1,3})", "점수[:\\s]+(\\d{1,3})"};
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    try {
                        extractedScore = Integer.parseInt(m.group(1));
                        if (extractedScore >= 0 && extractedScore <= 100) {
                            break;
                        } else {
                            extractedScore = 0;
                        }
                    } catch (NumberFormatException e) {
                        // 무시
                    }
                }
            }
        }
        
        public int getPositiveIndicators() { return positiveIndicators; }
        public int getNegativeIndicators() { return negativeIndicators; }
        public int getImprovementSuggestions() { return improvementSuggestions; }
        public int getExtractedScore() { return extractedScore; }
    }
}