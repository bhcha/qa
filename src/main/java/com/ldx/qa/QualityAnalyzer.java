package com.ldx.qa;

import com.ldx.qa.analyzer.*;
import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.report.HtmlReportGenerator;
import com.ldx.qa.report.JsonReportGenerator;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.QualityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Main entry point for quality analysis
 * Usage: QualityAnalyzer.analyze(projectDir, outputDir, configuration)
 */
public class QualityAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(QualityAnalyzer.class);
    
    private final List<Analyzer> analyzers;
    private final QaConfiguration config;
    
    public QualityAnalyzer(QaConfiguration config) {
        this.config = config;
        this.analyzers = initializeAnalyzers();
    }
    
    /**
     * Static method for easy integration with Gradle tasks (with automatic config detection)
     */
    public static QualityReport analyze(File projectDir, File outputDir) {
        QaConfiguration config = loadConfiguration(projectDir.toPath());
        return analyze(projectDir, outputDir, config);
    }
    
    /**
     * Static method for easy integration with Gradle tasks (with explicit config)
     */
    public static QualityReport analyze(File projectDir, File outputDir, QaConfiguration config) {
        // Auto-detect base package only if not explicitly configured
        if (shouldAutoDetectBasePackage(config, projectDir)) {
            String detectedPackage = autoDetectBasePackage(projectDir.toPath());
            if (detectedPackage != null) {
                config.setArchunitBasePackage(detectedPackage);
                logger.info("Auto-detected base package: {}", detectedPackage);
            } else {
                logger.warn("Could not auto-detect base package, using default: {}", config.getArchunitBasePackage());
            }
        } else {
            logger.info("Using configured base package: {}", config.getArchunitBasePackage());
        }
        
        QualityAnalyzer analyzer = new QualityAnalyzer(config);
        return analyzer.runAnalysis(projectDir.toPath(), outputDir.toPath());
    }
    
    /**
     * Automatically load configuration from project or parent directories
     */
    private static QaConfiguration loadConfiguration(Path projectDir) {
        // 1. 프로젝트 설정 확인
        Path projectConfig = projectDir.resolve("config/qa.properties");
        if (Files.exists(projectConfig)) {
            logger.info("Loading configuration from project: {}", projectConfig);
            return QaConfiguration.fromFile(projectConfig.toFile());
        }
        
        // 2. 의존성 프로젝트에서 설정 찾기
        Path parentConfig = findParentProjectConfig(projectDir);
        if (parentConfig != null) {
            logger.info("Loading configuration from parent project: {}", parentConfig);
            return QaConfiguration.fromFile(parentConfig.toFile());
        }
        
        // 3. 기본 설정 사용
        logger.info("Using default configuration");
        return QaConfiguration.defaultConfig();
    }
    
    /**
     * Find configuration file in parent project directories
     */
    private static Path findParentProjectConfig(Path projectDir) {
        Path current = projectDir.getParent();
        
        while (current != null && !current.equals(current.getParent())) {
            // Check for qa.properties in config directory
            Path configFile = current.resolve("config/qa.properties");
            if (Files.exists(configFile)) {
                return configFile;
            }
            
            // Check if this directory looks like a project root
            // (has build.gradle, pom.xml, or .git)
            boolean isProjectRoot = Files.exists(current.resolve("build.gradle")) ||
                                   Files.exists(current.resolve("pom.xml")) ||
                                   Files.exists(current.resolve(".git"));
            
            if (isProjectRoot) {
                // Look for qa.properties in this project root
                Path rootConfig = current.resolve("qa.properties");
                if (Files.exists(rootConfig)) {
                    return rootConfig;
                }
            }
            
            current = current.getParent();
        }
        
        return null;
    }
    
    private List<Analyzer> initializeAnalyzers() {
        List<Analyzer> analyzerList = new ArrayList<>();
        
        // Static analyzers
        if (config.isStaticAnalysisEnabled()) {
            if (config.isCheckstyleEnabled()) {
                analyzerList.add(new CheckstyleAnalyzer(config));
            }
            if (config.isPmdEnabled()) {
                analyzerList.add(new PmdAnalyzer(config));
            }
            if (config.isSpotbugsEnabled()) {
                analyzerList.add(new SpotBugsAnalyzer(config));
            }
            if (config.isJacocoEnabled()) {
                analyzerList.add(new JaCoCoAnalyzer(config));
            }
            if (config.isArchunitEnabled()) {
                analyzerList.add(new ArchUnitAnalyzer(config));
            }
            // Kingfisher 추가
            if (config.isKingfisherEnabled()) {
                KingfisherAnalyzer kingfisherAnalyzer = new KingfisherAnalyzer(config);
                if (kingfisherAnalyzer.isAvailable()) {
                    analyzerList.add(kingfisherAnalyzer);
                    logger.info("Kingfisher secret scanner enabled");
                } else {
                    if (!config.isSkipUnavailableAnalyzers()) {
                        logger.warn("Kingfisher is enabled but not available. Install Kingfisher binary to use secret scanning.");
                    } else {
                        logger.info("Kingfisher not available, skipping secret scanning");
                    }
                }
            }
        }
        
        // AI analyzers
        if (config.isAiAnalysisEnabled()) {
            GeminiAnalyzer geminiAnalyzer = new GeminiAnalyzer(config);
            if (geminiAnalyzer.isAvailable() || !config.isSkipUnavailableAnalyzers()) {
                analyzerList.add(geminiAnalyzer);
            } else {
                logger.info("Gemini AI not available, skipping AI analysis");
            }
        }
        
        return analyzerList;
    }
    
    public QualityReport runAnalysis(Path projectDir, Path outputDir) {
        logger.info("Starting quality analysis for project: {}", projectDir);
        
        List<AnalysisResult> results = new ArrayList<>();
        String overallStatus = "pass";
        
        // Run each analyzer
        for (Analyzer analyzer : analyzers) {
            try {
                logger.info("Running {} analysis...", analyzer.getName());
                AnalysisResult result = analyzer.analyze(projectDir);
                results.add(result);
                
                if ("fail".equals(result.getStatus()) && !config.isIgnoreFailures()) {
                    overallStatus = "fail";
                }
                
                logger.info("{} analysis completed with status: {}", 
                    analyzer.getName(), result.getStatus());
            } catch (Exception e) {
                logger.error("Error during {} analysis", analyzer.getName(), e);
                results.add(createErrorResult(analyzer.getName(), e));
                if (!config.isIgnoreFailures()) {
                    overallStatus = "fail";
                }
            }
        }
        
        // Create quality report
        QualityReport report = QualityReport.builder()
            .timestamp(LocalDateTime.now())
            .projectPath(projectDir.toString())
            .overallStatus(overallStatus)
            .results(results)
            .build();
        
        // Generate reports
        generateReports(report, outputDir);
        
        return report;
    }
    
    private void generateReports(QualityReport report, Path outputDir) {
        outputDir.toFile().mkdirs();
        
        // Generate HTML report
        if (config.isHtmlReportEnabled()) {
            try {
                Path htmlPath = outputDir.resolve("quality-report.html");
                new HtmlReportGenerator().generate(report, htmlPath);
                logger.info("HTML report generated: {}", htmlPath);
            } catch (Exception e) {
                logger.error("Failed to generate HTML report", e);
            }
        }
        
        // Generate JSON report
        if (config.isJsonReportEnabled()) {
            try {
                Path jsonPath = outputDir.resolve("quality-report.json");
                new JsonReportGenerator().generate(report, jsonPath);
                logger.info("JSON report generated: {}", jsonPath);
            } catch (Exception e) {
                logger.error("Failed to generate JSON report", e);
            }
        }
    }
    
    private AnalysisResult createErrorResult(String analyzerName, Exception e) {
        return AnalysisResult.builder()
            .type(analyzerName)
            .status("error")
            .summary("Analysis failed: " + e.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Main method for command-line execution
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: QualityAnalyzer <projectDir> <outputDir> [configFile]");
            System.exit(1);
        }
        
        File projectDir = new File(args[0]);
        File outputDir = new File(args[1]);
        
        // Load configuration
        QaConfiguration config;
        if (args.length >= 3) {
            config = QaConfiguration.fromFile(new File(args[2]));
        } else {
            config = loadConfiguration(projectDir.toPath());
        }
        
        // Auto-detect base package only if not explicitly configured
        if (shouldAutoDetectBasePackage(config, projectDir)) {
            String detectedPackage = autoDetectBasePackage(projectDir.toPath());
            if (detectedPackage != null) {
                config.setArchunitBasePackage(detectedPackage);
                logger.info("Auto-detected base package: {}", detectedPackage);
            } else {
                logger.warn("Could not auto-detect base package, using default: {}", config.getArchunitBasePackage());
            }
        } else {
            logger.info("Using configured base package: {}", config.getArchunitBasePackage());
        }
        
        // Run analysis
        QualityReport report = analyze(projectDir, outputDir, config);
        
        // Exit with appropriate code
        System.exit("pass".equals(report.getOverallStatus()) ? 0 : 1);
    }
    
    private static String autoDetectBasePackage(Path projectPath) {
        try {
            // 1. Try to read from build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            if (Files.exists(buildGradle)) {
                String content = Files.readString(buildGradle);
                if (content.contains("group = '")) {
                    int start = content.indexOf("group = '") + 9;
                    int end = content.indexOf("'", start);
                    if (end > start) {
                        return content.substring(start, end);
                    }
                }
            }
            
            // 2. Try to read from pom.xml
            Path pomXml = projectPath.resolve("pom.xml");
            if (Files.exists(pomXml)) {
                String content = Files.readString(pomXml);
                if (content.contains("<groupId>")) {
                    int start = content.indexOf("<groupId>") + 9;
                    int end = content.indexOf("</groupId>", start);
                    if (end > start) {
                        return content.substring(start, end);
                    }
                }
            }
            
            // 3. Try to find main class
            Path srcMainJava = projectPath.resolve("src/main/java");
            if (Files.exists(srcMainJava)) {
                try (var stream = Files.walk(srcMainJava)) {
                    return stream
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> {
                            try {
                                String content = Files.readString(path);
                                return content.contains("@SpringBootApplication") || 
                                       content.contains("public static void main");
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .findFirst()
                        .map(mainClassPath -> {
                            try {
                                String content = Files.readString(mainClassPath);
                                if (content.contains("package ")) {
                                    int start = content.indexOf("package ") + 8;
                                    int end = content.indexOf(";", start);
                                    if (end > start) {
                                        return content.substring(start, end).trim();
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                            return null;
                        })
                        .orElse(null);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Failed to auto-detect base package: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Determines if base package should be auto-detected
     */
    private static boolean shouldAutoDetectBasePackage(QaConfiguration config, File projectDir) {
        // Don't auto-detect if explicitly configured in properties file
        if (config.isBasePackageExplicitlyConfigured()) {
            logger.debug("Base package explicitly configured: {}", config.getArchunitBasePackage());
            return false;
        }
        
        String configuredPackage = config.getArchunitBasePackage();
        
        // Auto-detect if using default package name
        if ("com.ldx.qa".equals(configuredPackage)) {
            logger.debug("Using default base package, will auto-detect");
            return true;
        }
        
        // Auto-detect if configured package doesn't seem to match the project
        if (configuredPackage != null && !configuredPackage.isEmpty()) {
            try {
                // Check if the configured package exists in the project
                Path srcMainJava = projectDir.toPath().resolve("src/main/java");
                if (Files.exists(srcMainJava)) {
                    String packagePath = configuredPackage.replace('.', '/');
                    Path packageDir = srcMainJava.resolve(packagePath);
                    
                    // If package directory doesn't exist, auto-detect
                    if (!Files.exists(packageDir)) {
                        logger.info("Configured package {} not found in project structure, will auto-detect", configuredPackage);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error checking configured package: {}", e.getMessage());
                return true;
            }
        }
        
        return false;
    }
}
