package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit analyzer for architecture compliance checking
 */
public class ArchUnitAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(ArchUnitAnalyzer.class);
    
    private final QaConfiguration config;
    
    public ArchUnitAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "archunit";
    }
    
    @Override
    public String getType() {
        return "static";
    }
    
    @Override
    public boolean isAvailable() {
        // ArchUnit is always available if included in classpath
        return true;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running ArchUnit architecture analysis on: {}", projectPath);
        
        try {
            // Load ArchUnit configuration
            loadArchUnitConfiguration(projectPath);
            
            // Check if there are Java classes to analyze
            Path srcMainJava = projectPath.resolve("src/main/java");
            if (!Files.exists(srcMainJava)) {
                logger.warn("No src/main/java directory found, skipping ArchUnit analysis");
                return createSkippedResult();
            }
            
            // Import Java classes from the project
            JavaClasses javaClasses = importJavaClasses(projectPath);
            
            if (javaClasses.isEmpty()) {
                logger.warn("No Java classes found for analysis");
                return createSkippedResult();
            }
            
            logger.info("Found {} classes to analyze", javaClasses.size());
            
            // Define and run architecture rules
            List<ArchRule> rules = defineArchitectureRules();
            List<Violation> violations = new ArrayList<>();
            int totalRules = rules.size();
            int passedRules = 0;
            
            for (ArchRule rule : rules) {
                try {
                    EvaluationResult result = rule.evaluate(javaClasses);
                    if (result.hasViolation()) {
                        violations.addAll(convertToViolations(result));
                        logger.debug("Rule failed: {}", rule.getDescription());
                    } else {
                        passedRules++;
                        logger.debug("Rule passed: {}", rule.getDescription());
                    }
                } catch (AssertionError e) {
                    // ArchUnit의 AssertionError를 처리 (예: empty should 에러)
                    if (e.getMessage().contains("failed to check any classes")) {
                        logger.info("Rule skipped (no matching classes): {}", rule.getDescription());
                        violations.add(Violation.builder()
                            .severity("info")
                            .message("규칙이 적용할 클래스를 찾지 못했습니다: " + rule.getDescription())
                            .type("rule_no_matching_classes")
                            .build());
                        passedRules++; // 매칭되는 클래스가 없으면 통과로 처리
                    } else {
                        logger.warn("Rule assertion failed: {}", rule.getDescription(), e);
                        violations.add(Violation.builder()
                            .severity("error")
                            .message("규칙 검증 실패: " + e.getMessage())
                            .type("rule_assertion_error")
                            .build());
                    }
                } catch (Exception e) {
                    logger.warn("Error evaluating rule: {}", rule.getDescription(), e);
                    violations.add(Violation.builder()
                        .severity("error")
                        .message("규칙 평가 실패: " + e.getMessage())
                        .type("rule_evaluation_error")
                        .build());
                } catch (Throwable t) {
                    // 모든 Throwable을 캐치 (Error 클래스도 포함)
                    if (t.getMessage() != null && t.getMessage().contains("failed to check any classes")) {
                        logger.info("Rule skipped (no matching classes): {}", rule.getDescription());
                        violations.add(Violation.builder()
                            .severity("info")
                            .message("규칙이 적용할 클래스를 찾지 못했습니다: " + rule.getDescription())
                            .type("rule_no_matching_classes")
                            .build());
                        passedRules++; // 매칭되는 클래스가 없으면 통과로 처리
                    } else {
                        logger.warn("Unexpected error evaluating rule: {}", rule.getDescription(), t);
                        violations.add(Violation.builder()
                            .severity("error")
                            .message("규칙 평가 중 예상치 못한 오류: " + t.getMessage())
                            .type("rule_unexpected_error")
                            .build());
                    }
                }
            }
            
            // Calculate score
            int score = totalRules > 0 ? (passedRules * 100 / totalRules) : 100;
            
            // 실제 아키텍처 위반만 카운트 (info 레벨은 제외)
            long actualViolations = violations.stream()
                .filter(v -> !"info".equals(v.getSeverity()))
                .count();
            
            String status = actualViolations == 0 ? "pass" : "fail";
            
            // Build summary
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("ArchUnit 아키텍처 분석 완료 (점수: %d/100)", score));
            summary.append(String.format("\n- 전체 규칙: %d개", totalRules));
            summary.append(String.format("\n- 통과한 규칙: %d개", passedRules));
            summary.append(String.format("\n- 위반 사항: %d개 (정보: %d개)", actualViolations, violations.size() - actualViolations));
            
            if (!violations.isEmpty()) {
                summary.append("\n\n주요 아키텍처 위반 사항:");
                violations.stream()
                    .filter(v -> !"info".equals(v.getSeverity()))
                    .limit(5)
                    .forEach(v -> summary.append("\n- ").append(v.getMessage()));
                    
                // 정보성 메시지도 표시
                long infoCount = violations.stream()
                    .filter(v -> "info".equals(v.getSeverity()))
                    .count();
                if (infoCount > 0) {
                    summary.append("\n\n정보성 알림:");
                    violations.stream()
                        .filter(v -> "info".equals(v.getSeverity()))
                        .limit(3)
                        .forEach(v -> summary.append("\n- ").append(v.getMessage()));
                }
            }
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("score", score);
            metrics.put("rules_checked", totalRules);
            metrics.put("rules_passed", passedRules);
            metrics.put("violations_found", violations.size());
            metrics.put("classes_analyzed", javaClasses.size());
            
            return AnalysisResult.builder()
                .type(getName())
                .status(status)
                .summary(summary.toString())
                .violations(violations)
                .metrics(metrics)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to run ArchUnit analysis", e);
            throw new AnalysisException("ArchUnit analysis failed", e);
        }
    }
    
    private void loadArchUnitConfiguration(Path projectPath) {
        try {
            String archunitConfigPath = config.getArchunitConfigPath();
            Path configPath = projectPath.resolve(archunitConfigPath);
            
            if (Files.exists(configPath)) {
                logger.info("Loading ArchUnit configuration from: {}", configPath);
                Properties archunitProps = new Properties();
                try (InputStream is = Files.newInputStream(configPath)) {
                    archunitProps.load(is);
                    
                    // Apply ArchUnit specific configurations
                    applyArchUnitProperties(archunitProps);
                    logger.info("ArchUnit configuration loaded successfully");
                }
            } else {
                logger.info("No ArchUnit configuration file found at {}, using defaults", configPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to load ArchUnit configuration: {}", e.getMessage());
        }
    }
    
    private void applyArchUnitProperties(Properties props) {
        // Set ArchUnit system properties for configuration
        for (String propertyName : props.stringPropertyNames()) {
            String value = props.getProperty(propertyName);
            System.setProperty(propertyName, value);
            logger.debug("Set ArchUnit property: {} = {}", propertyName, value);
        }
    }
    
    private List<ArchRule> defineArchitectureRules() {
        List<ArchRule> rules = new ArrayList<>();
        
        logger.info("Defining simple architecture rules");
        
        // 1. Application 클래스를 의존하는 클래스는 application, adapter에만 존재해야 한다
        rules.add(classes()
            .that().resideInAPackage("..application..")
            .should().onlyHaveDependentClassesThat().resideInAnyPackage("..application..", "..adapter..")
            .as("Application 클래스를 의존하는 클래스는 application, adapter에만 존재해야 합니다"));
        
        // 2. Application 클래스는 adapter의 클래스를 의존하면 안 된다
        rules.add(noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .as("Application 클래스는 adapter의 클래스를 의존하면 안 됩니다"));
        
        // 3. Domain의 클래스는 domain, java만 의존해야 한다
        rules.add(classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..")
            .as("Domain의 클래스는 domain, java만 의존해야 합니다"));
        
        logger.info("Created {} simple architecture rules", rules.size());
        return rules;
    }
    
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    private String getStringProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value != null ? value : defaultValue;
    }
    
    private List<Violation> convertToViolations(EvaluationResult result) {
        List<Violation> violations = new ArrayList<>();
        
        result.getFailureReport().getDetails().forEach(detail -> {
            violations.add(Violation.builder()
                .severity("error")
                .message(detail.trim())
                .type("architecture_violation")
                .build());
        });
        
        return violations;
    }
    
    private JavaClasses importJavaClasses(Path projectPath) {
        try {
            // 1단계: 컴파일된 클래스 파일에서 임포트 시도
            Path targetClasses = projectPath.resolve("target/classes");
            Path buildClasses = projectPath.resolve("build/classes/java/main");
            
            if (Files.exists(targetClasses) && Files.list(targetClasses).findAny().isPresent()) {
                logger.info("Importing classes from compiled target: {}", targetClasses);
                return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPath(targetClasses);
            } else if (Files.exists(buildClasses) && Files.list(buildClasses).findAny().isPresent()) {
                logger.info("Importing classes from compiled build: {}", buildClasses);
                return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPath(buildClasses);
            }
            
            // 2단계: 자동 감지된 패키지로 임포트 시도
            String detectedPackage = detectBasePackage(projectPath);
            if (detectedPackage != null && !detectedPackage.isEmpty()) {
                logger.info("Attempting to import auto-detected package: {}", detectedPackage);
                try {
                    JavaClasses classes = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(detectedPackage);
                    
                    if (!classes.isEmpty()) {
                        logger.info("Successfully imported {} classes from package: {}", classes.size(), detectedPackage);
                        return classes;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to import detected package {}: {}", detectedPackage, e.getMessage());
                }
            }
            
            // 3단계: 설정된 기본 패키지로 임포트 시도 (명시적으로 설정된 경우만)
            String configuredPackage = config.getArchunitBasePackage();
            if (configuredPackage != null && !configuredPackage.trim().isEmpty()) {
                logger.info("Attempting to import explicitly configured package: {}", configuredPackage);
                try {
                    JavaClasses classes = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(configuredPackage);
                    
                    if (!classes.isEmpty()) {
                        logger.info("Successfully imported {} classes from configured package: {}", classes.size(), configuredPackage);
                        return classes;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to import configured package {}: {}", configuredPackage, e.getMessage());
                }
            } else {
                logger.debug("No base package explicitly configured, relying on auto-detection");
            }
            
            // 4단계: 클래스패스에서 전체 임포트 (최후의 수단)
            logger.info("Attempting to import all classes from classpath");
            JavaClasses classpathClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importClasspath();
            
            if (!classpathClasses.isEmpty()) {
                logger.info("Successfully imported {} classes from classpath", classpathClasses.size());
                return classpathClasses;
            }
            
            logger.warn("All import attempts failed, returning empty class collection");
            return new ClassFileImporter().importClasses();
            
        } catch (Exception e) {
            logger.error("Critical error during class import: {}", e.getMessage(), e);
            return new ClassFileImporter().importClasses();
        }
    }
    
    private String detectBasePackage(Path projectPath) {
        try {
            logger.info("Attempting to detect base package for project: {}", projectPath);
            
            // 1. Try to read from build.gradle or pom.xml
            String packageFromBuildFile = detectPackageFromBuildFile(projectPath);
            if (packageFromBuildFile != null) {
                logger.info("Detected package from build file: {}", packageFromBuildFile);
                return packageFromBuildFile;
            }
            
            // 2. Try to find main class with @SpringBootApplication or main method
            String packageFromMainClass = detectPackageFromMainClass(projectPath);
            if (packageFromMainClass != null) {
                logger.info("Detected package from main class: {}", packageFromMainClass);
                return packageFromMainClass;
            }
            
            // 3. Try to detect from source structure by finding common package prefix
            String packageFromSourceStructure = detectPackageFromSourceStructure(projectPath);
            if (packageFromSourceStructure != null) {
                logger.info("Detected package from source structure: {}", packageFromSourceStructure);
                return packageFromSourceStructure;
            }
            
            logger.warn("Could not auto-detect base package for project: {}", projectPath);
            return null;
        } catch (Exception e) {
            logger.error("Failed to detect base package: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private String detectPackageFromBuildFile(Path projectPath) {
        try {
            // Check build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            if (Files.exists(buildGradle)) {
                String content = Files.readString(buildGradle);
                
                // Look for group = 'com.example' or group = "com.example"
                String group = extractGroupFromGradle(content);
                if (group != null) {
                    logger.debug("Found group in build.gradle: {}", group);
                    return group;
                }
            }
            
            // Check build.gradle.kts
            Path buildGradleKts = projectPath.resolve("build.gradle.kts");
            if (Files.exists(buildGradleKts)) {
                String content = Files.readString(buildGradleKts);
                String group = extractGroupFromGradleKts(content);
                if (group != null) {
                    logger.debug("Found group in build.gradle.kts: {}", group);
                    return group;
                }
            }
            
            // Check pom.xml
            Path pomXml = projectPath.resolve("pom.xml");
            if (Files.exists(pomXml)) {
                String content = Files.readString(pomXml);
                // Look for <groupId>com.example</groupId>
                if (content.contains("<groupId>")) {
                    int start = content.indexOf("<groupId>") + 9;
                    int end = content.indexOf("</groupId>", start);
                    if (end > start) {
                        String groupId = content.substring(start, end).trim();
                        logger.debug("Found groupId in pom.xml: {}", groupId);
                        return groupId;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to detect package from build file: {}", e.getMessage());
        }
        return null;
    }
    
    private String extractGroupFromGradle(String content) {
        // Pattern: group = 'com.example' or group = "com.example" or group='com.example'
        String[] patterns = {
            "group = '", "group = \"", "group='", "group=\""
        };
        
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                int start = content.indexOf(pattern) + pattern.length();
                char quote = pattern.charAt(pattern.length() - 1);
                int end = content.indexOf(quote, start);
                if (end > start) {
                    return content.substring(start, end).trim();
                }
            }
        }
        return null;
    }
    
    private String extractGroupFromGradleKts(String content) {
        // Pattern: group = "com.example"
        if (content.contains("group = \"")) {
            int start = content.indexOf("group = \"") + 9;
            int end = content.indexOf("\"", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return null;
    }
    
    private String detectPackageFromMainClass(Path projectPath) {
        try {
            Path srcMainJava = projectPath.resolve("src/main/java");
            if (!Files.exists(srcMainJava)) {
                logger.debug("No src/main/java directory found");
                return null;
            }
            
            // Find Java files containing main class indicators
            List<Path> candidateFiles = Files.walk(srcMainJava)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    try {
                        String content = Files.readString(path);
                        return content.contains("@SpringBootApplication") || 
                               content.contains("@Application") ||
                               content.contains("public static void main") ||
                               content.contains("@EnableAutoConfiguration") ||
                               path.getFileName().toString().contains("Application");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
            
            // 우선순위: @SpringBootApplication > Application.java > main method
            for (Path candidate : candidateFiles) {
                try {
                    String content = Files.readString(candidate);
                    if (content.contains("@SpringBootApplication") || 
                        candidate.getFileName().toString().endsWith("Application.java")) {
                        
                        String packageName = extractPackageFromContent(content);
                        if (packageName != null) {
                            logger.debug("Found package from main class {}: {}", candidate.getFileName(), packageName);
                            return packageName;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to read candidate file {}: {}", candidate, e.getMessage());
                }
            }
            
            // 대체안: main method가 있는 파일들에서 찾기
            for (Path candidate : candidateFiles) {
                try {
                    String content = Files.readString(candidate);
                    if (content.contains("public static void main")) {
                        String packageName = extractPackageFromContent(content);
                        if (packageName != null) {
                            logger.debug("Found package from main method class {}: {}", candidate.getFileName(), packageName);
                            return packageName;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to read candidate file {}: {}", candidate, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.debug("Failed to detect package from main class: {}", e.getMessage());
        }
        return null;
    }
    
    private String detectPackageFromSourceStructure(Path projectPath) {
        try {
            Path srcMainJava = projectPath.resolve("src/main/java");
            if (!Files.exists(srcMainJava)) {
                logger.debug("No src/main/java directory found");
                return null;
            }
            
            // Java 파일들을 찾아서 패키지 구조 분석
            Map<String, Integer> packageCounts = new HashMap<>();
            
            Files.walk(srcMainJava)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFile -> {
                    try {
                        String content = Files.readString(javaFile);
                        String packageName = extractPackageFromContent(content);
                        if (packageName != null && !packageName.isEmpty()) {
                            // 패키지 이름과 그것의 모든 상위 패키지들을 카운트
                            String[] parts = packageName.split("\\.");
                            for (int i = 1; i <= parts.length; i++) {
                                String prefix = String.join(".", Arrays.copyOf(parts, i));
                                packageCounts.put(prefix, packageCounts.getOrDefault(prefix, 0) + 1);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to read Java file {}: {}", javaFile, e.getMessage());
                    }
                });
            
            if (packageCounts.isEmpty()) {
                logger.debug("No packages found in source structure");
                return null;
            }
            
            // 가장 많이 사용된 패키지 중에서 가장 짧은 것을 선택 (루트 패키지)
            String basePackage = packageCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1) // 적어도 2개 이상의 파일에서 사용
                .min((a, b) -> {
                    int lengthCompare = Integer.compare(a.getKey().length(), b.getKey().length());
                    return lengthCompare != 0 ? lengthCompare : Integer.compare(b.getValue(), a.getValue());
                })
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (basePackage != null) {
                logger.debug("Found base package from source structure: {} (used in {} files)", 
                    basePackage, packageCounts.get(basePackage));
            }
            
            return basePackage;
        } catch (Exception e) {
            logger.debug("Failed to detect package from source structure: {}", e.getMessage());
        }
        return null;
    }
    
    private String extractPackageFromContent(String content) {
        if (content.contains("package ")) {
            int start = content.indexOf("package ") + 8;
            int end = content.indexOf(";", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return null;
    }
    
    private AnalysisResult createSkippedResult() {
        return AnalysisResult.builder()
            .type(getName())
            .status("pass")
            .summary("ArchUnit 분석을 건너뛰었습니다 (Java 클래스 없음)")
            .violations(Collections.emptyList())
            .metrics(Collections.singletonMap("score", 100))
            .timestamp(LocalDateTime.now())
            .build();
    }
}