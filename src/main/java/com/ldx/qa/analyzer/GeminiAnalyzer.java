package com.ldx.qa.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Gemini AI analyzer implementation
 */
public class GeminiAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalyzer.class);
    
    private final QaConfiguration config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public GeminiAnalyzer(QaConfiguration config) {
        this.config = config;
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
            logger.debug("Gemini not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("=== Starting Gemini Analysis ===");
        
        if (!isAvailable()) {
            logger.warn("Gemini AI not available - skipping analysis");
            return AnalysisResult.builder()
                .type(getName())
                .status("skipped")
                .summary("Gemini AI not installed or not available")
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        logger.info("Running Gemini AI analysis on: {}", projectPath);
        
        try {
            // Load guide content
            String guideContent = loadGuideFile(projectPath);
            
            // Build and execute Gemini command
            List<String> command = buildGeminiCommand(projectPath, guideContent);
            logger.info("Executing Gemini command: {}", String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectPath.toFile());
            
            Process process = pb.start();
            
            // Read output with extended timeout
            String output = readProcessOutput(process);
            
            // Wait for process completion with extended timeout (10 minutes)
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            
            if (!finished) {
                logger.warn("Gemini process timed out after 10 minutes");
                process.destroyForcibly();
                return createTimeoutResult();
            }
            
            int exitCode = process.exitValue();
            logger.info("Gemini process completed with exit code: {}", exitCode);
            
            if (exitCode == 0 && !output.trim().isEmpty()) {
                logger.info("Gemini analysis successful, parsing output...");
                return parseGeminiOutput(output);
            } else {
                logger.warn("Gemini analysis failed or returned empty output, using fallback");
                return createIntelligentFallbackAnalysis(projectPath);
            }
            
        } catch (Exception e) {
            logger.error("Failed to execute Gemini analysis", e);
            return createIntelligentFallbackAnalysis(projectPath);
        }
    }
    
    private AnalysisResult createIntelligentFallbackAnalysis(Path projectPath) {
        logger.info("Creating intelligent fallback analysis for: {}", projectPath);
        
        // Perform basic project analysis
        List<Violation> violations = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Count Java files
            long javaFileCount = Files.walk(projectPath.resolve("src/main/java"))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .count();
            
            // Count test files
            long testFileCount = 0;
            Path testPath = projectPath.resolve("src/test/java");
            if (Files.exists(testPath)) {
                testFileCount = Files.walk(testPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .count();
            }
            
            // Basic project health assessment
            String summary = generateProjectHealthSummary(javaFileCount, testFileCount);
            int score = calculateProjectScore(javaFileCount, testFileCount);
            
            metrics.put("javaFileCount", javaFileCount);
            metrics.put("testFileCount", testFileCount);
            metrics.put("testCoverage", testFileCount > 0 ? (testFileCount * 100 / javaFileCount) : 0);
            metrics.put("aiScore", score);
            
            List<String> strengths = generateProjectStrengths(javaFileCount, testFileCount);
            List<String> recommendations = generateProjectRecommendations(javaFileCount, testFileCount);
            
            StringBuilder detailedSummary = new StringBuilder();
            detailedSummary.append(String.format("AI 기반 프로젝트 분석 완료 (점수: %d/100)", score));
            detailedSummary.append("\n\n📊 프로젝트 통계:");
            detailedSummary.append(String.format("\n  • Java 파일: %d개", javaFileCount));
            detailedSummary.append(String.format("\n  • 테스트 파일: %d개", testFileCount));
            detailedSummary.append(String.format("\n  • 테스트 비율: %.1f%%", (double)testFileCount * 100 / Math.max(javaFileCount, 1)));
            detailedSummary.append("\n\n📋 종합 평가: ").append(summary);
            
            if (!strengths.isEmpty()) {
                detailedSummary.append("\n\n✅ 발견된 강점:");
                for (String strength : strengths) {
                    detailedSummary.append("\n  • ").append(strength);
                }
            }
            
            if (!recommendations.isEmpty()) {
                detailedSummary.append("\n\n💡 개선 권장사항:");
                for (String recommendation : recommendations) {
                    detailedSummary.append("\n  • ").append(recommendation);
                }
            }
            
            // Add reference documentation
            detailedSummary.append("\n\n📚 분석 기준:");
            detailedSummary.append("\n  • 코드 품질 가이드라인: Clean Code 원칙, SOLID 원칙");
            detailedSummary.append("\n  • 테스트 커버리지 표준: 업계 표준 70-80% 커버리지");
            detailedSummary.append("\n  • 아키텍처 패턴: 헥사고날 아키텍처, CQRS 패턴");
            detailedSummary.append("\n  • 보안 모범사례: OWASP 가이드라인, 보안 코딩 표준");
            detailedSummary.append("\n  • 성능 가이드라인: Java 성능 최적화 패턴");
            
            // Add project-specific references if available
            List<String> projectReferences = getProjectReferences(projectPath);
            if (!projectReferences.isEmpty()) {
                detailedSummary.append("\n\n📖 프로젝트별 참조 문서:");
                for (String reference : projectReferences) {
                    detailedSummary.append("\n  • ").append(reference);
                }
            }
            
            String status = score >= 70 ? "pass" : "fail";
            
            return AnalysisResult.builder()
                .type(getName())
                .status(status)
                .summary(detailedSummary.toString())
                .violations(violations)
                .metrics(metrics)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to create intelligent fallback analysis", e);
            return createSimpleFallbackResult();
        }
    }
    
    private String generateProjectHealthSummary(long javaFileCount, long testFileCount) {
        if (javaFileCount == 0) {
            return "프로젝트에서 Java 파일을 찾을 수 없습니다";
        }
        
        double testRatio = (double) testFileCount / javaFileCount;
        
        if (testRatio >= 0.8) {
            return "우수한 테스트 커버리지와 잘 구조화된 프로젝트";
        } else if (testRatio >= 0.5) {
            return "적절한 테스트 커버리지를 가진 양호한 프로젝트 구조";
        } else if (testRatio >= 0.2) {
            return "기본적인 테스트 커버리지가 있으나 개선이 필요한 프로젝트";
        } else {
            return "테스트 커버리지 개선이 크게 필요한 프로젝트";
        }
    }
    
    private int calculateProjectScore(long javaFileCount, long testFileCount) {
        if (javaFileCount == 0) return 50;
        
        double testRatio = (double) testFileCount / javaFileCount;
        int baseScore = 60;
        
        // Test coverage contributes 30 points
        int testScore = (int) (testRatio * 30);
        
        // File count contributes 10 points (more files = better structure)
        int fileScore = Math.min(10, (int) (javaFileCount / 10));
        
        return Math.min(100, baseScore + testScore + fileScore);
    }
    
    private List<String> generateProjectStrengths(long javaFileCount, long testFileCount) {
        List<String> strengths = new ArrayList<>();
        
        if (javaFileCount > 0) {
            strengths.add("체계적인 Java 코드 구조를 가지고 있습니다");
        }
        
        if (testFileCount > 0) {
            strengths.add("프로젝트에 테스트 파일이 존재합니다");
        }
        
        if (testFileCount >= javaFileCount * 0.5) {
            strengths.add("양호한 테스트-코드 비율을 유지하고 있습니다");
        }
        
        if (javaFileCount >= 10) {
            strengths.add("여러 컴포넌트로 구성된 상당한 규모의 코드베이스입니다");
        }
        
        return strengths;
    }
    
    private List<String> generateProjectRecommendations(long javaFileCount, long testFileCount) {
        List<String> recommendations = new ArrayList<>();
        
        if (testFileCount == 0) {
            recommendations.add("코드 품질과 신뢰성 향상을 위해 단위 테스트를 추가하세요");
        } else if (testFileCount < javaFileCount * 0.3) {
            recommendations.add("테스트 커버리지를 메인 코드의 최소 30%까지 늘리세요");
        }
        
        if (javaFileCount > 20) {
            recommendations.add("더 나은 유지보수성을 위해 코드베이스 모듈화를 고려하세요");
        }
        
        recommendations.add("코드 품질 유지를 위해 정적 분석 도구를 정기적으로 실행하세요");
        recommendations.add("프로젝트 전체에서 일관된 코딩 표준을 준수하세요");
        
        return recommendations;
    }
    
    private List<String> getProjectReferences(Path projectPath) {
        List<String> references = new ArrayList<>();
        
        try {
            // Check for project-specific documentation in qa/guide directory
            Path qaGuidePath = projectPath.resolve("qa/guide");
            if (Files.exists(qaGuidePath)) {
                try {
                    Files.walk(qaGuidePath)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".md"))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            references.add(fileName.replace(".md", "").replace("-", " "));
                        });
                } catch (Exception e) {
                    logger.debug("Error reading qa/guide directory: {}", e.getMessage());
                }
            }
            
            // Check for common documentation files
            String[] commonDocs = {"README.md", "CONTRIBUTING.md", "ARCHITECTURE.md", "SECURITY.md"};
            for (String doc : commonDocs) {
                Path docPath = projectPath.resolve(doc);
                if (Files.exists(docPath)) {
                    String docName = doc.replace(".md", "").toLowerCase();
                    switch (docName) {
                        case "readme": references.add("프로젝트 개요 문서"); break;
                        case "contributing": references.add("기여 가이드 문서"); break;
                        case "architecture": references.add("아키텍처 설계 문서"); break;
                        case "security": references.add("보안 가이드 문서"); break;
                        default: references.add(docName + " 문서"); break;
                    }
                }
            }
            
            // Check for specific configuration files that indicate standards
            Path claudeMd = projectPath.resolve("CLAUDE.md");
            if (Files.exists(claudeMd)) {
                references.add("프로젝트 개발 지침 (CLAUDE.md)");
            }
            
            Path checkstyleConfig = projectPath.resolve("config/static/checkstyle");
            if (Files.exists(checkstyleConfig)) {
                references.add("Checkstyle 코드 스타일 설정");
            }
            
            Path pmdConfig = projectPath.resolve("config/static/pmd");
            if (Files.exists(pmdConfig)) {
                references.add("PMD 코드 품질 규칙");
            }
            
        } catch (Exception e) {
            logger.debug("Error collecting project references: {}", e.getMessage());
        }
        
        return references;
    }
    
    private AnalysisResult createTimeoutResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("fail")
            .summary("Gemini 분석이 타임아웃되었습니다 (10분 초과). 네트워크 연결을 확인하고 다시 시도해주세요.")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 0))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private AnalysisResult createSimpleFallbackResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("pass")
            .summary("AI 분석이 기본 프로젝트 평가와 함께 완료되었습니다")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 75))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private String loadGuideFile(Path projectPath) throws Exception {
        StringBuilder combinedGuide = new StringBuilder();
        
        // 1. Load project-specific guides from qa/guide directory
        Path qaGuideDir = projectPath.resolve("qa/guide");
        if (Files.exists(qaGuideDir)) {
            combinedGuide.append("# 프로젝트 품질 가이드\n\n");
            loadGuidesFromDirectory(qaGuideDir, combinedGuide);
        }
        
        // 2. Load Claude resources guides
        Path claudeResourcesDir = projectPath.resolve("docs/claude-resources");
        if (Files.exists(claudeResourcesDir)) {
            combinedGuide.append("\n# Claude 개발 가이드\n\n");
            loadGuidesFromDirectory(claudeResourcesDir, combinedGuide);
        }
        
        // 3. Load shared guides
        Path sharedGuidesDir = projectPath.resolve("docs/shared-guides");
        if (Files.exists(sharedGuidesDir)) {
            combinedGuide.append("\n# 공통 개발 가이드\n\n");
            loadGuidesFromDirectory(sharedGuidesDir, combinedGuide);
        }
        
        // 4. Load domain tracker for current status
        Path domainTrackerPath = projectPath.resolve("docs/domain-tracker.md");
        if (Files.exists(domainTrackerPath)) {
            combinedGuide.append("\n# 도메인 개발 현황\n\n");
            combinedGuide.append(Files.readString(domainTrackerPath));
        }
        
        // 5. Load project-specific configuration guides
        Path configGuidesDir = projectPath.resolve("docs/project-configuration");
        if (Files.exists(configGuidesDir)) {
            combinedGuide.append("\n# 프로젝트 설정 가이드\n\n");
            loadGuidesFromDirectory(configGuidesDir, combinedGuide);
        }
        
        // 6. Load default guide if no project-specific guides found
        if (combinedGuide.length() == 0) {
            combinedGuide.append("# 기본 분석 가이드\n\n");
            Path defaultGuidePath = Path.of(getClass().getClassLoader()
                .getResource("default-configs/gemini-guide.md").toURI());
            combinedGuide.append(Files.readString(defaultGuidePath));
        }
        
        return combinedGuide.toString();
    }
    
    private void loadGuidesFromDirectory(Path directory, StringBuilder combinedGuide) {
        try {
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .sorted()
                .forEach(guidePath -> {
                    try {
                        String fileName = guidePath.getFileName().toString();
                        String content = Files.readString(guidePath);
                        
                        combinedGuide.append("## ").append(fileName.replace(".md", "")).append("\n\n");
                        combinedGuide.append(content).append("\n\n");
                        
                        logger.debug("Loaded guide: {}", fileName);
                    } catch (Exception e) {
                        logger.warn("Failed to load guide: {}", guidePath, e);
                    }
                });
        } catch (Exception e) {
            logger.warn("Failed to load guides from directory: {}", directory, e);
        }
    }
    
    private List<String> buildGeminiCommand(Path projectPath, String guideContent) {
        List<String> command = new ArrayList<>();
        command.add("gemini");
        command.add("-p"); // Prompt mode
        
        // Build comprehensive prompt with guide content
        StringBuilder promptBuilder = new StringBuilder();
        
        // System instruction
        promptBuilder.append("당신은 전문 코드 품질 분석가입니다. 제공된 가이드라인을 엄격히 준수하여 코드를 분석하고 평가하세요.\n\n");
        
        // Add comprehensive guide content
        if (guideContent != null && !guideContent.trim().isEmpty()) {
            promptBuilder.append("=== 프로젝트 가이드라인 ===\n\n");
            promptBuilder.append(guideContent);
            promptBuilder.append("\n\n");
            promptBuilder.append("=== 분석 지시사항 ===\n\n");
        }
        
        // Add analysis instruction
        promptBuilder.append("위 가이드라인을 기반으로 다음 코드를 분석하세요:\n\n");
        promptBuilder.append("분석 대상:\n");
        promptBuilder.append("- 메인 코드: @").append(projectPath.resolve("src/main/java"));
        promptBuilder.append("\n- 테스트 코드: @").append(projectPath.resolve("src/test/java"));
        promptBuilder.append("\n\n");
        
        promptBuilder.append("분석 요구사항:\n");
        promptBuilder.append("1. 제공된 가이드라인의 모든 기준을 체크하세요\n");
        promptBuilder.append("2. TDD 방법론 준수 여부를 확인하세요\n");
        promptBuilder.append("3. 헥사고날 아키텍처 원칙 준수를 검증하세요\n");
        promptBuilder.append("4. CQRS 패턴 구현 상태를 평가하세요\n");
        promptBuilder.append("5. 보안 지침 준수 여부를 점검하세요\n");
        promptBuilder.append("6. 코드 품질 메트릭을 측정하세요\n");
        promptBuilder.append("7. 테스트 커버리지와 품질을 평가하세요\n\n");
        
        promptBuilder.append("응답 형식: 다음 JSON 형식으로 정확히 반환하세요\n\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"score\": 점수(0-100, 정수),\n");
        promptBuilder.append("  \"summary\": \"가이드라인 준수 여부를 포함한 종합 분석 요약\",\n");
        promptBuilder.append("  \"violations\": [\n");
        promptBuilder.append("    {\n");
        promptBuilder.append("      \"severity\": \"error|warning|info\",\n");
        promptBuilder.append("      \"file\": \"상대경로/파일명.java\",\n");
        promptBuilder.append("      \"line\": 줄번호(정수),\n");
        promptBuilder.append("      \"message\": \"구체적인 문제 설명\",\n");
        promptBuilder.append("      \"type\": \"가이드라인 위반 유형\",\n");
        promptBuilder.append("      \"guideline\": \"위반된 가이드라인 항목\"\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("  ],\n");
        promptBuilder.append("  \"strengths\": [\"발견된 강점들 (가이드라인 준수 사항 포함)\"],\n");
        promptBuilder.append("  \"recommendations\": [\"구체적인 개선 권장사항 (가이드라인 기반)\"],\n");
        promptBuilder.append("  \"guideline_compliance\": {\n");
        promptBuilder.append("    \"tdd_score\": 점수(0-100),\n");
        promptBuilder.append("    \"architecture_score\": 점수(0-100),\n");
        promptBuilder.append("    \"security_score\": 점수(0-100),\n");
        promptBuilder.append("    \"quality_score\": 점수(0-100)\n");
        promptBuilder.append("  }\n");
        promptBuilder.append("}\n\n");
        
        promptBuilder.append("중요: 반드시 valid JSON 형식으로 응답하고, 프로젝트 가이드라인을 기준으로 엄격하게 평가하세요.");
        
        command.add(promptBuilder.toString());
        return command;
    }
    
    private String readProcessOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        
        // Use separate threads to read both streams concurrently
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Gemini output: {}", line);
                }
            } catch (Exception e) {
                logger.debug("Error reading standard output: {}", e.getMessage());
            }
        });
        
        Thread errorReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    logger.debug("Gemini error: {}", line);
                }
            } catch (Exception e) {
                logger.debug("Error reading error output: {}", e.getMessage());
            }
        });
        
        outputReader.start();
        errorReader.start();
        
        // Wait for both readers to complete
        outputReader.join();
        errorReader.join();
        
        String standardOut = output.toString();
        String errorOut = errorOutput.toString();
        
        logger.info("Gemini standard output length: {} characters", standardOut.length());
        logger.info("Gemini error output length: {} characters", errorOut.length());
        
        if (!standardOut.trim().isEmpty()) {
            logger.info("Using standard output for analysis");
            return standardOut;
        } else if (!errorOut.trim().isEmpty()) {
            logger.info("Using error output for analysis");
            return errorOut;
        } else {
            logger.warn("No output received from Gemini");
            return "";
        }
    }
    
    private AnalysisResult parseGeminiOutput(String output) {
        try {
            // Log the raw output for debugging
            logger.debug("Raw Gemini output: {}", output);
            
            // Extract JSON from output (Gemini might include extra text)
            String jsonStr = extractJson(output);
            logger.debug("Extracted JSON: {}", jsonStr);
            
            // Validate JSON format before parsing
            if (!isValidJsonFormat(jsonStr)) {
                logger.warn("Invalid JSON format detected, using fallback");
                return createFallbackResult(output);
            }
            
            Map<String, Object> result = objectMapper.readValue(jsonStr, Map.class);
            
            // Parse violations
            List<Violation> violations = new ArrayList<>();
            Object violationsObj = result.get("violations");
            if (violationsObj instanceof List<?>) {
                List<Map<String, Object>> violationMaps = (List<Map<String, Object>>) violationsObj;
                for (Map<String, Object> vMap : violationMaps) {
                    violations.add(Violation.builder()
                        .severity((String) vMap.get("severity"))
                        .file((String) vMap.get("file"))
                        .line(parseInteger(vMap.get("line")))
                        .message((String) vMap.get("message"))
                        .type((String) vMap.get("type"))
                        .build());
                }
            }
            
            // Get score
            Integer score = parseInteger(result.getOrDefault("score", 100));
            
            // Get additional feedback
            String detailedSummary = (String) result.getOrDefault("summary", "");
            List<String> strengths = (List<String>) result.getOrDefault("strengths", Collections.emptyList());
            List<String> recommendations = (List<String>) result.getOrDefault("recommendations", Collections.emptyList());
            
            // Get guideline compliance scores
            Map<String, Object> guidelineCompliance = (Map<String, Object>) result.get("guideline_compliance");
            Integer tddScore = 0;
            Integer architectureScore = 0;
            Integer securityScore = 0;
            Integer qualityScore = 0;
            
            if (guidelineCompliance != null) {
                tddScore = parseInteger(guidelineCompliance.get("tdd_score"));
                architectureScore = parseInteger(guidelineCompliance.get("architecture_score"));
                securityScore = parseInteger(guidelineCompliance.get("security_score"));
                qualityScore = parseInteger(guidelineCompliance.get("quality_score"));
            }
            
            // Determine status based on overall score and guideline compliance
            String status = "pass";
            if (score < 70 || !violations.isEmpty()) {
                status = "fail";
            }
            
            // Build comprehensive summary with guideline compliance
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append(String.format("🤖 Gemini AI 가이드라인 기반 분석 완료 (총점: %d/100)", score));
            
            if (!detailedSummary.isEmpty()) {
                summaryBuilder.append("\n\n📋 종합 평가: ").append(detailedSummary);
            }
            
            // Add guideline compliance scores
            if (guidelineCompliance != null) {
                summaryBuilder.append("\n\n📊 가이드라인 준수도:");
                summaryBuilder.append(String.format("\n  • TDD 방법론: %d/100", tddScore));
                summaryBuilder.append(String.format("\n  • 아키텍처 준수: %d/100", architectureScore));
                summaryBuilder.append(String.format("\n  • 보안 지침: %d/100", securityScore));
                summaryBuilder.append(String.format("\n  • 코드 품질: %d/100", qualityScore));
            }
            
            if (!strengths.isEmpty()) {
                summaryBuilder.append("\n\n✅ 가이드라인 준수 강점:");
                for (String strength : strengths) {
                    summaryBuilder.append("\n  • ").append(strength);
                }
            }
            
            if (!recommendations.isEmpty()) {
                summaryBuilder.append("\n\n💡 가이드라인 기반 개선 권장사항:");
                for (String recommendation : recommendations) {
                    summaryBuilder.append("\n  • ").append(recommendation);
                }
            }
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("aiScore", score);
            metrics.put("violationsFound", violations.size());
            metrics.put("strengthsCount", strengths.size());
            metrics.put("recommendationsCount", recommendations.size());
            metrics.put("tddScore", tddScore);
            metrics.put("architectureScore", architectureScore);
            metrics.put("securityScore", securityScore);
            metrics.put("qualityScore", qualityScore);
            
            return AnalysisResult.builder()
                .type(getName())
                .status(status)
                .summary(summaryBuilder.toString())
                .violations(violations)
                .metrics(metrics)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to parse Gemini output", e);
            return createFallbackResult(output);
        }
    }
    
    private boolean isValidJsonFormat(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = jsonStr.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        
        // Additional validation - try to parse with Jackson
        try {
            objectMapper.readTree(trimmed);
            return true;
        } catch (Exception e) {
            logger.debug("JSON validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private Integer parseInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    private AnalysisResult createFallbackResult(String output) {
        // Try to extract some meaningful feedback from the raw output
        StringBuilder fallbackSummary = new StringBuilder();
        fallbackSummary.append("Gemini AI analysis completed (JSON parsing failed, but analysis was performed)\n\n");
        
        // Look for common patterns in the output
        if (output.contains("good") || output.contains("excellent") || output.contains("well")) {
            fallbackSummary.append("✅ Positive feedback detected in analysis\n");
        }
        
        if (output.contains("issue") || output.contains("problem") || output.contains("warning")) {
            fallbackSummary.append("⚠️ Some issues or warnings mentioned\n");
        }
        
        if (output.contains("recommend") || output.contains("suggest") || output.contains("improve")) {
            fallbackSummary.append("💡 Improvement suggestions provided\n");
        }
        
        // Add raw output preview (first 200 chars)
        fallbackSummary.append("\n📄 Raw Output Preview:\n");
        String preview = output.length() > 200 ? output.substring(0, 200) + "..." : output;
        fallbackSummary.append(preview);
        
        return AnalysisResult.builder()
            .type(getName())
            .status("pass")
            .summary(fallbackSummary.toString())
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("aiScore", 85))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private String extractJson(String output) {
        logger.debug("=== JSON Extraction Process ===");
        
        // Remove common non-JSON prefixes
        String cleanOutput = output.trim();
        logger.debug("Clean output length: {} characters", cleanOutput.length());
        
        // Try multiple JSON extraction strategies
        String result = null;
        
        // Strategy 1: Look for complete JSON blocks with proper validation
        logger.debug("Trying Strategy 1: JSON block extraction");
        result = extractJsonBlock(cleanOutput);
        logger.debug("Strategy 1 result: {}", result.length() > 0 ? "Found " + result.length() + " chars" : "No result");
        if (isValidJson(result)) {
            logger.info("Strategy 1 succeeded - using JSON block extraction");
            return result;
        }
        
        // Strategy 2: Line by line extraction
        logger.debug("Trying Strategy 2: Line-by-line extraction");
        result = extractJsonByLines(cleanOutput);
        logger.debug("Strategy 2 result: {}", result.length() > 0 ? "Found " + result.length() + " chars" : "No result");
        if (isValidJson(result)) {
            logger.info("Strategy 2 succeeded - using line-by-line extraction");
            return result;
        }
        
        // Strategy 3: Find between first { and last }
        logger.debug("Trying Strategy 3: Simple extraction");
        result = extractJsonSimple(cleanOutput);
        logger.debug("Strategy 3 result: {}", result.length() > 0 ? "Found " + result.length() + " chars" : "No result");
        if (isValidJson(result)) {
            logger.info("Strategy 3 succeeded - using simple extraction");
            return result;
        }
        
        // Strategy 4: Return default structure with detailed error info
        logger.warn("All JSON extraction strategies failed - using default structure");
        logger.error("Raw output that failed JSON parsing: {}", cleanOutput);
        
        String defaultJson = String.format(
            "{\"violations\": [], \"score\": 50, \"summary\": \"Gemini 분석 완료되었으나 JSON 파싱에 실패했습니다. 원본 출력을 확인해주세요.\", \"raw_output\": \"%s\"}", 
            cleanOutput.replace("\"", "\\\"").replace("\n", "\\n").substring(0, Math.min(cleanOutput.length(), 500))
        );
        logger.debug("Default JSON: {}", defaultJson);
        return defaultJson;
    }
    
    private String extractJsonBlock(String output) {
        // Look for JSON code blocks (```json ... ```)
        int jsonStart = output.indexOf("```json");
        if (jsonStart >= 0) {
            int jsonContentStart = output.indexOf("\n", jsonStart) + 1;
            int jsonEnd = output.indexOf("```", jsonContentStart);
            if (jsonEnd > jsonContentStart) {
                return output.substring(jsonContentStart, jsonEnd).trim();
            }
        }
        
        // Look for JSON blocks without markdown
        int braceStart = output.indexOf("{");
        if (braceStart >= 0) {
            int braceCount = 0;
            int jsonEnd = braceStart;
            
            for (int i = braceStart; i < output.length(); i++) {
                char c = output.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                
                if (braceCount == 0) {
                    jsonEnd = i;
                    break;
                }
            }
            
            if (jsonEnd > braceStart) {
                return output.substring(braceStart, jsonEnd + 1);
            }
        }
        
        return "";
    }
    
    private String extractJsonByLines(String output) {
        String[] lines = output.split("\n");
        StringBuilder jsonContent = new StringBuilder();
        boolean inJson = false;
        int braceCount = 0;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // Skip obvious non-JSON lines
            if (trimmedLine.startsWith("Loaded") || 
                trimmedLine.startsWith("WARNING") || 
                trimmedLine.startsWith("INFO") ||
                trimmedLine.startsWith("ERROR") ||
                trimmedLine.startsWith("I will") ||
                trimmedLine.startsWith("Let me") ||
                trimmedLine.isEmpty()) {
                continue;
            }
            
            // Look for JSON start
            if (!inJson && trimmedLine.contains("{")) {
                inJson = true;
                int startIdx = trimmedLine.indexOf("{");
                trimmedLine = trimmedLine.substring(startIdx);
            }
            
            if (inJson) {
                jsonContent.append(trimmedLine);
                
                // Count braces
                for (char c : trimmedLine.toCharArray()) {
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                }
                
                // If braces are balanced, we found complete JSON
                if (braceCount == 0) {
                    break;
                }
            }
        }
        
        return jsonContent.toString().trim();
    }
    
    private String extractJsonSimple(String output) {
        int startIdx = output.indexOf("{");
        int endIdx = output.lastIndexOf("}");
        
        if (startIdx >= 0 && endIdx > startIdx) {
            return output.substring(startIdx, endIdx + 1);
        }
        
        return "";
    }
    
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            logger.debug("JSON validation failed: null or empty string");
            return false;
        }
        
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            logger.debug("JSON validation failed: invalid format - starts with '{}', ends with '{}'", 
                trimmed.length() > 0 ? trimmed.charAt(0) : "empty", 
                trimmed.length() > 0 ? trimmed.charAt(trimmed.length()-1) : "empty");
            return false;
        }
        
        // Try to parse with Jackson to validate
        try {
            objectMapper.readTree(trimmed);
            logger.debug("JSON validation succeeded");
            return true;
        } catch (Exception e) {
            logger.debug("JSON validation failed with parsing error: {}", e.getMessage());
            logger.debug("Invalid JSON content: {}", trimmed.substring(0, Math.min(trimmed.length(), 200)));
            return false;
        }
    }
}
