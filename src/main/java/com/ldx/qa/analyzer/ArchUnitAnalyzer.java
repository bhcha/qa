package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import com.ldx.qa.model.CategorizedArchUnitViolation;
import com.ldx.qa.model.ArchUnitViolationCategory;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.dependencies.Slice;
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
            
            String status = actualViolations > config.getArchunitFailureThreshold() ? "fail" : "pass";
            
            // Build detailed summary
            StringBuilder summary = new StringBuilder();
            summary.append("🏗️ ArchUnit 헥사고날 아키텍처 분석 완료\n");
            summary.append("=".repeat(50)).append("\n\n");
            
            // 전체 분석 정보
            summary.append("📊 분석 개요\n");
            summary.append(String.format("• 분석 점수: %d/100점\n", score));
            summary.append(String.format("• 분석 클래스 수: %d개\n", javaClasses.size()));
            summary.append(String.format("• 전체 아키텍처 규칙: %d개\n", totalRules));
            summary.append(String.format("• 통과한 규칙: %d개\n", passedRules));
            summary.append(String.format("• 실패한 규칙: %d개\n", totalRules - passedRules));
            summary.append(String.format("• 총 위반 사항: %d개 (실제: %d개, 정보: %d개)\n", 
                violations.size(), actualViolations, violations.size() - actualViolations));
            summary.append("\n");
            
            // 적용된 규칙들 설명
            summary.append("🎯 적용된 아키텍처 규칙\n");
            
            // 설정에 따라 활성화된 규칙들만 표시
            if (config.isArchunitHexagonalEnabled()) {
                summary.append("✅ 헥사고날 아키텍처 규칙 (활성화)\n");
                summary.append("   1. 레이어 의존성 규칙 - 올바른 의존성 방향 강제\n");
                summary.append("      • Adapter → Application, Infrastructure 허용\n");  
                summary.append("      • Application → Domain, Infrastructure 허용\n");
                summary.append("      • Domain → 외부 의존성 완전 금지 (순수성 보장)\n");
                summary.append("   2. 도메인별 독립성 - 각 도메인은 서로 의존하지 않아야 함\n");
                summary.append("   3. 어댑터별 독립성 - 각 어댑터는 서로 의존하지 않아야 함\n");
                summary.append("   4. 애플리케이션 서비스별 독립성 검증\n");
                summary.append("   5. Application → Adapter 의존성 금지\n");
                summary.append("   6. Domain 순수성 - domain, java 표준 라이브러리만 의존 가능\n");
                summary.append("\n");
                summary.append("📋 레이어별 역할 설명\n");
                summary.append("• Domain: 비즈니스 로직의 핵심, 외부 기술과 완전 분리\n");
                summary.append("• Application: 유스케이스 조정, 포트 인터페이스 정의\n");
                summary.append("• Adapter: 외부 시스템과의 연결, 포트 구현\n");
                summary.append("• Infrastructure: 구현체 제공 및 의존성 주입 구조\n\n");
            } else {
                summary.append("❌ 헥사고날 아키텍처 규칙 (비활성화)\n\n");
            }
            
            if (config.isArchunitLayeredEnabled()) {
                summary.append("✅ 레이어드 아키텍처 규칙 (활성화)\n");
                summary.append("   - 향후 구현 예정\n\n");
            }
            
            if (config.isArchunitOnionEnabled()) {
                summary.append("✅ 어니언 아키텍처 규칙 (활성화)\n");
                summary.append("   - 향후 구현 예정\n\n");
            }
            
            if (config.isArchunitCqrsEnabled()) {
                summary.append("✅ CQRS 패턴 규칙 (활성화)\n");
                summary.append("   - 향후 구현 예정\n\n");
            }
            
            if (config.isArchunitDomainDrivenEnabled()) {
                summary.append("✅ DDD 패턴 규칙 (활성화)\n");
                summary.append("   - 향후 구현 예정\n\n");
            }
            
            // 위반 카테고리별 통계 - 헥사고날 아키텍처 규칙 기준
            if (!violations.isEmpty()) {
                Map<String, Long> categoryStats = violations.stream()
                    .filter(v -> !"info".equals(v.getSeverity()))
                    .collect(Collectors.groupingBy(
                        v -> categorizeHexagonalViolation(v.getMessage()),
                        Collectors.counting()
                    ));
                
                summary.append("📈 헥사고날 아키텍처 위반 카테고리별 통계\n");
                for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                    summary.append(String.format("• %s: %d건\n", entry.getKey(), entry.getValue()));
                }
                summary.append("\n");
                
                // 위반된 규칙에 대한 상세 설명 제공
                summary.append("🎯 위반된 규칙 해결 가이드\n");
                summary.append("규칙 1: 레이어 의존성 - 올바른 의존성 방향 확인 필요\n");
                summary.append("규칙 2-4: 모듈 독립성 - 각 모듈 간 직접 의존성 제거\n");
                summary.append("규칙 5: Application → Adapter 금지 - 포트/어댑터 패턴 적용\n");
                summary.append("규칙 6: Domain 순수성 - 외부 프레임워크/라이브러리 의존성 제거\n");
                summary.append("\n");
                
                summary.append("🔍 주요 위반 사항 (상위 5개)\n");
                violations.stream()
                    .filter(v -> !"info".equals(v.getSeverity()))
                    .limit(5)
                    .forEach(v -> summary.append("• ").append(v.getMessage()).append("\n"));
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
    
    
    private List<ArchRule> defineArchitectureRules() {
        List<ArchRule> rules = new ArrayList<>();
        
        // ArchUnit 규칙들을 설정에 따라 조건적으로 추가
        if (config.isArchunitHexagonalEnabled()) {
            logger.info("Adding hexagonal architecture rules");
            addHexagonalArchitectureRules(rules);
        } else {
            logger.info("Hexagonal architecture rules disabled");
        }
        
        if (config.isArchunitLayeredEnabled()) {
            logger.info("Adding layered architecture rules");
            addLayeredArchitectureRules(rules);
        }
        
        if (config.isArchunitOnionEnabled()) {
            logger.info("Adding onion architecture rules");
            addOnionArchitectureRules(rules);
        }
        
        if (config.isArchunitCqrsEnabled()) {
            logger.info("Adding CQRS pattern rules");
            addCqrsPatternRules(rules);
        }
        
        if (config.isArchunitDomainDrivenEnabled()) {
            logger.info("Adding domain-driven design rules");
            addDomainDrivenDesignRules(rules);
        }
        
        // 아무 규칙도 활성화되지 않은 경우 기본 헥사고날 규칙 적용
        if (rules.isEmpty()) {
            logger.warn("No architecture rules enabled, applying default hexagonal rules");
            addHexagonalArchitectureRules(rules);
        }
        
        logger.info("Created {} architecture rules total", rules.size());
        return rules;
    }

    private void addHexagonalArchitectureRules(List<ArchRule> rules) {
        // 1. 엄격한 헥사고날 아키텍처 레이어 규칙
        try {
            rules.add(layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("adapter").definedBy("..adapter..")
                    .layer("application").definedBy("..application..")
                    .layer("domain").definedBy("..domain..")
                    .layer("infrastructure").definedBy("..infrastructure..", "..config..")

                    // 더 엄격한 의존성 규칙
                    .whereLayer("adapter").mayOnlyAccessLayers("application", "domain")  // infrastructure 제거
                    .whereLayer("application").mayOnlyAccessLayers("domain")  // infrastructure 제거
                    .whereLayer("domain").mayNotAccessAnyLayer()    // 도메인 순수성 유지
                    .whereLayer("infrastructure").mayOnlyAccessLayers("domain", "application")  // 설정에서 필요

                    .as("헥사고날 아키텍처 레이어 의존성 규칙"));
        } catch (Exception e) {
            logger.warn("Failed to create layered architecture rule: {}", e.getMessage());
        }

        // 2. 도메인별 독립성 검증 (Bounded Context)
        try {
            rules.add(slices()
                    .matching("..domain.(*)..")
                    .that(new DescribedPredicate<Slice>("도메인 슬라이스 (shared/common 제외)") {
                        @Override
                        public boolean test(Slice slice) {
                            String desc = slice.getDescription();
                            return !desc.contains("shared") && !desc.contains("common");
                        }
                    })
                    .should().notDependOnEachOther()
                    .as("도메인별 독립성 - 각 Bounded Context는 서로 의존하지 않아야 합니다"));
        } catch (Exception e) {
            logger.warn("Failed to create domain independence rule: {}", e.getMessage());
        }

        // 3. 어댑터별 독립성 검증
        try {
            rules.add(slices()
                    .matching("..adapter.(*)..")
                    .that(new DescribedPredicate<Slice>("어댑터 슬라이스 (shared/common 제외)") {
                        @Override
                        public boolean test(Slice slice) {
                            String desc = slice.getDescription();
                            return !desc.contains("shared") && !desc.contains("common");
                        }
                    })
                    .should().notDependOnEachOther()
                    .as("어댑터별 독립성 - 각 어댑터는 서로 의존하지 않아야 합니다"));
        } catch (Exception e) {
            logger.warn("Failed to create adapter independence rule: {}", e.getMessage());
        }

        // 4. 애플리케이션 서비스별 독립성 검증
        try {
            rules.add(slices()
                    .matching("..application.(*)..")
                    .that(new DescribedPredicate<Slice>("애플리케이션 슬라이스 (shared/common/port 제외)") {
                        @Override
                        public boolean test(Slice slice) {
                            String desc = slice.getDescription();
                            return !desc.contains("shared") && !desc.contains("common") && !desc.contains("port");
                        }
                    })
                    .should().notDependOnEachOther()
                    .as("애플리케이션 서비스별 독립성 - 각 Use Case는 독립적이어야 합니다"));
        } catch (Exception e) {
            logger.warn("Failed to create application independence rule: {}", e.getMessage());
        }

        // 5. Application → Adapter 의존성 금지
        try {
            rules.add(noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..")
                    .as("Application은 Adapter를 의존하면 안 됩니다 (의존성 역전 원칙)"));
        } catch (Exception e) {
            logger.warn("Failed to create application-adapter dependency rule: {}", e.getMessage());
        }

        // 6. Domain 순수성 - 더 엄격한 규칙
        try {
            rules.add(classes()
                    .that().resideInAPackage("..domain..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "..domain..",
                            "java.lang..",
                            "java.util..",
                            "java.time..",
                            "java.math..",
                            "java.util.function..",
                            "java.util.stream..",
                            "java.util.concurrent.."
                    )
                    .as("Domain은 순수 Java 클래스만 사용해야 합니다"));
        } catch (Exception e) {
            logger.warn("Failed to create domain dependency rule: {}", e.getMessage());
        }

        // 7. Domain이 외부 프레임워크에 의존하지 않도록
        try {
            rules.add(noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..springframework..",
                            "..jakarta..",
                            "..javax..",
                            "..hibernate..",
                            "..jackson..",
                            "..lombok..",  // Lombok도 제외
                            "..slf4j..",   // 로깅도 제외
                            "..apache..",
                            "..google..",
                            "..fasterxml.."
                    )
                    .as("Domain은 외부 프레임워크/라이브러리에 의존하면 안 됩니다"));
        } catch (Exception e) {
            logger.warn("Failed to create domain framework dependency rule: {}", e.getMessage());
        }

        // 8. Application이 구체적인 Adapter 구현체를 직접 의존하지 않도록
        try {
            rules.add(noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..adapter.in..",
                            "..adapter.out..",
                            "..adapter.persistence..",
                            "..adapter.web..",
                            "..adapter.rest..",
                            "..adapter.messaging.."
                    )
                    .as("Application은 구체적인 Adapter 구현체를 직접 의존하면 안 됩니다"));
        } catch (Exception e) {
            logger.warn("Failed to create application-adapter implementation rule: {}", e.getMessage());
        }


        // 10. Infrastructure가 비즈니스 로직을 포함하지 않도록
        try {
            rules.add(noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.service..")
                    .as("Infrastructure는 Application Service를 직접 참조하면 안 됩니다"));
        } catch (Exception e) {
            logger.warn("Failed to create infrastructure business logic rule: {}", e.getMessage());
        }

        // 11. 순환 의존성 방지
        try {
            rules.add(slices()
                    .matching("com.(*)..")
                    .should().beFreeOfCycles()
                    .as("패키지 간 순환 의존성이 없어야 합니다"));
        } catch (Exception e) {
            logger.warn("Failed to create cycle detection rule: {}", e.getMessage());
        }


        logger.info("Added {} hexagonal architecture rules", rules.size());
    }
    
    /**
     * 레이어드 아키텍처 규칙들을 추가합니다.
     */
    private void addLayeredArchitectureRules(List<ArchRule> rules) {
        // 향후 구현 예정
        logger.info("Layered architecture rules not yet implemented");
    }
    
    /**
     * 어니언 아키텍처 규칙들을 추가합니다.
     */
    private void addOnionArchitectureRules(List<ArchRule> rules) {
        // 향후 구현 예정
        logger.info("Onion architecture rules not yet implemented");
    }
    
    /**
     * CQRS 패턴 규칙들을 추가합니다.
     */
    private void addCqrsPatternRules(List<ArchRule> rules) {
        // 향후 구현 예정
        logger.info("CQRS pattern rules not yet implemented");
    }
    
    /**
     * DDD 패턴 규칙들을 추가합니다.
     */
    private void addDomainDrivenDesignRules(List<ArchRule> rules) {
        // 향후 구현 예정
        logger.info("Domain-driven design rules not yet implemented");
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
        Map<ArchUnitViolationCategory, List<CategorizedArchUnitViolation>> categorizedViolations = new HashMap<>();
        
        // 위반 사항들을 카테고리별로 분류
        result.getFailureReport().getDetails().forEach(detail -> {
            CategorizedArchUnitViolation categorizedViolation = CategorizedArchUnitViolation.fromMessage(detail.trim());
            
            categorizedViolations.computeIfAbsent(categorizedViolation.getCategory(), k -> new ArrayList<>())
                .add(categorizedViolation);
        });
        
        // 카테고리별로 정리된 위반 사항들을 Violation 객체로 변환
        for (Map.Entry<ArchUnitViolationCategory, List<CategorizedArchUnitViolation>> entry : categorizedViolations.entrySet()) {
            ArchUnitViolationCategory category = entry.getKey();
            List<CategorizedArchUnitViolation> categoryViolations = entry.getValue();
            
            // 카테고리 헤더 추가
            violations.add(Violation.builder()
                .severity("info")
                .message(String.format("=== %s (%d건) ===", category.getFormattedName(), categoryViolations.size()))
                .type("category_header")
                .build());
            
            // 카테고리 설명 추가
            violations.add(Violation.builder()
                .severity("info")
                .message(category.getDetailedDescription())
                .type("category_description")
                .build());
            
            // 각 위반 사항을 간결하게 정리하여 추가
            categoryViolations.forEach(violation -> {
                String formattedMessage = formatViolationMessage(violation);
                violations.add(Violation.builder()
                    .severity("error")
                    .message(formattedMessage)
                    .type("architecture_violation")
                    .build());
            });
            
            // 카테고리 구분선 추가
            violations.add(Violation.builder()
                .severity("info")
                .message("") // 빈 줄로 구분
                .type("category_separator")
                .build());
        }
        
        return violations;
    }
    
    /**
     * 위반 메시지를 더 읽기 쉽게 포맷팅합니다.
     */
    private String formatViolationMessage(CategorizedArchUnitViolation violation) {
        StringBuilder formatted = new StringBuilder();
        
        // 위반하는 클래스 정보
        if (violation.getViolatingClass() != null) {
            String simpleClassName = getSimpleClassName(violation.getViolatingClass());
            formatted.append("📍 ").append(simpleClassName);
            
            if (violation.getViolatingMethod() != null) {
                formatted.append(".").append(violation.getViolatingMethod()).append("()");
            }
        }
        
        // 대상 클래스 정보
        if (violation.getTargetClass() != null) {
            String simpleTargetClassName = getSimpleClassName(violation.getTargetClass());
            formatted.append(" → ").append(simpleTargetClassName);
        }
        
        // 소스 위치 정보
        if (violation.getSourceLocation() != null) {
            formatted.append(" (").append(violation.getSourceLocation()).append(")");
        }
        
        // 포맷팅된 메시지가 너무 간단한 경우 원본 메시지 사용
        if (formatted.length() < 10) {
            return violation.getOriginalMessage();
        }
        
        return formatted.toString();
    }
    
    /**
     * 풀 클래스명에서 간단한 클래스명을 추출합니다.
     */
    private String getSimpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return fullClassName;
        }
        
        // 메서드 시그니처가 포함된 경우 제거
        if (fullClassName.contains("(")) {
            fullClassName = fullClassName.substring(0, fullClassName.indexOf("("));
        }
        
        // 패키지명 제거
        if (fullClassName.contains(".")) {
            return fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        }
        
        return fullClassName;
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
    
    /**
     * 헥사고날 아키텍처 규칙 기준으로 위반 사항을 분류합니다.
     */
    private String categorizeHexagonalViolation(String violationMessage) {
        if (violationMessage == null || violationMessage.isEmpty()) {
            return "기타 위반";
        }
        
        String msg = violationMessage.toLowerCase();
        
        // 1. Domain 순수성 위반 (규칙 6)
        if (msg.contains("domain") && (msg.contains("depend") || msg.contains("access"))) {
            if (!msg.contains("java.")) {
                return "Domain 순수성 위반 (규칙 6) - 외부 기술 의존성 금지";
            }
        }
        
        // 5. Application → Adapter 의존성 금지
        if (msg.contains("application") && msg.contains("adapter") && 
            (msg.contains("depend") || msg.contains("access"))) {
            return "Application → Adapter 의존성 위반 (규칙 5) - 의존성 역전 원칙";
        }
        
        // 1. 레이어 의존성 규칙 위반
        if (isLayerDependencyViolation(msg)) {
            if (msg.contains("domain") && msg.contains("infrastructure")) {
                return "레이어 의존성 위반 (규칙 1) - Domain → Infrastructure 금지";
            }
            if (msg.contains("adapter") && !msg.contains("application") && !msg.contains("infrastructure")) {
                return "레이어 의존성 위반 (규칙 1) - Adapter 의존성 제한";
            }
            return "레이어 의존성 위반 (규칙 1) - 잘못된 의존성 방향";
        }
        
        // 2. 도메인별 독립성 위반
        if (msg.contains("domain") && msg.contains("depend on each other")) {
            return "도메인별 독립성 위반 (규칙 2) - 도메인 간 의존성 금지";
        }
        
        // 3. 어댑터별 독립성 위반
        if (msg.contains("adapter") && msg.contains("depend on each other")) {
            return "어댑터별 독립성 위반 (규칙 3) - 어댑터 간 의존성 금지";
        }
        
        // 4. 애플리케이션 서비스별 독립성 위반
        if (msg.contains("application") && msg.contains("depend on each other")) {
            return "애플리케이션 서비스별 독립성 위반 (규칙 4) - 서비스 간 의존성 금지";
        }
        
        // 일반적인 아키텍처 위반
        if (msg.contains("calls method") || msg.contains("accesses method")) {
            return "메서드 호출 위반 - 허용되지 않은 레이어 간 호출";
        }
        
        if (msg.contains("constructor") && msg.contains("parameter")) {
            return "생성자 파라미터 위반 - 잘못된 의존성 주입";
        }
        
        if (msg.contains("implements") || msg.contains("extends")) {
            return "상속/구현 위반 - 잘못된 인터페이스/클래스 관계";
        }
        
        return "기타 아키텍처 위반";
    }
    
    private boolean isLayerDependencyViolation(String message) {
        String[] layers = {"adapter", "application", "domain", "infrastructure"};
        
        boolean hasLayerReference = false;
        for (String layer : layers) {
            if (message.contains(layer)) {
                hasLayerReference = true;
                break;
            }
        }

        return hasLayerReference && (message.contains("may only access") || 
                                   message.contains("should not depend on") ||
                                   message.contains("may not access") ||
                                   message.contains("layered architecture"));
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