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
            String basePackage = config.getArchunitBasePackage();
            logger.info("Analyzing packages starting with: {}", basePackage);
            
            JavaClasses javaClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
            
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
                } catch (Exception e) {
                    logger.warn("Error evaluating rule: {}", rule.getDescription(), e);
                    violations.add(Violation.builder()
                        .severity("error")
                        .message("Rule evaluation failed: " + e.getMessage())
                        .type("rule_evaluation_error")
                        .build());
                }
            }
            
            // Calculate score
            int score = totalRules > 0 ? (passedRules * 100 / totalRules) : 100;
            String status = violations.isEmpty() ? "pass" : "fail";
            
            // Build summary
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("ArchUnit 아키텍처 분석 완료 (점수: %d/100)", score));
            summary.append(String.format("\n- 전체 규칙: %d개", totalRules));
            summary.append(String.format("\n- 통과한 규칙: %d개", passedRules));
            summary.append(String.format("\n- 위반 사항: %d개", violations.size()));
            
            if (!violations.isEmpty()) {
                summary.append("\n\n주요 아키텍처 위반 사항:");
                violations.stream()
                    .limit(5)
                    .forEach(v -> summary.append("\n- ").append(v.getMessage()));
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
        
        // Hexagonal Architecture Rules
        rules.add(noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..adapter..")
            .as("도메인은 애플리케이션이나 어댑터에 의존하면 안됩니다"));
            
        rules.add(noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .as("애플리케이션은 어댑터에 의존하면 안됩니다"));
            
        // Domain purity
        rules.add(classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "jakarta.persistence..")
            .as("도메인은 순수해야 하며 외부 의존성을 최소화해야 합니다"));
        
        // UseCase interfaces naming
        rules.add(classes()
            .that().resideInAPackage("..application..provided..")
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("UseCase")
            .as("제공 포트는 UseCase로 끝나는 인터페이스여야 합니다"));
            
        // Port interfaces naming
        rules.add(classes()
            .that().resideInAPackage("..application..required..")
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("Provider")
            .orShould().haveSimpleNameEndingWith("Repository")
            .as("필요 포트는 Provider나 Repository로 끝나는 인터페이스여야 합니다"));
        
        // Controller naming and location
        rules.add(classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..adapter.webapi..")
            .as("컨트롤러는 webapi 패키지에 위치해야 합니다"));
            
        // Adapter naming
        rules.add(classes()
            .that().resideInAPackage("..adapter.integration..")
            .and().areNotInnerClasses()
            .should().haveSimpleNameEndingWith("Adapter")
            .as("통합 어댑터는 Adapter로 끝나야 합니다"));
            
        rules.add(classes()
            .that().resideInAPackage("..adapter.persistence..")
            .and().areNotInnerClasses()
            .should().haveSimpleNameEndingWith("Adapter")
            .as("영속성 어댑터는 Adapter로 끝나야 합니다"));
        
        // No cycles
        rules.add(slices()
            .matching("com.ldx.qa.(*)..")
            .should().beFreeOfCycles()
            .as("패키지 간 순환 의존성이 없어야 합니다"));
            
        // Layered architecture with CQRS
        try {
            rules.add(layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Command").definedBy("..application..command..")
                .layer("Query").definedBy("..application..query..")
                .layer("Provided").definedBy("..application..provided..")
                .layer("Required").definedBy("..application..required..")
                .layer("Adapter").definedBy("..adapter..")
                .layer("Config").definedBy("..config..")
                
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Command", "Query", "Provided", "Required", "Adapter", "Config")
                .whereLayer("Command").mayOnlyBeAccessedByLayers("Application", "Config")
                .whereLayer("Query").mayOnlyBeAccessedByLayers("Application", "Config")
                .whereLayer("Provided").mayOnlyBeAccessedByLayers("Application", "Command", "Query", "Adapter", "Config")
                .whereLayer("Required").mayOnlyBeAccessedByLayers("Application", "Command", "Query", "Adapter", "Config")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
                .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
                .as("헥사고날 아키텍처 계층 규칙을 준수해야 합니다"));
        } catch (Exception e) {
            logger.debug("Layered architecture rule creation failed: {}", e.getMessage());
        }
        
        return rules;
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