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
     * Static method for easy integration with Gradle tasks
     */
    public static QualityReport analyze(File projectDir, File outputDir, QaConfiguration config) {
        QualityAnalyzer analyzer = new QualityAnalyzer(config);
        return analyzer.runAnalysis(projectDir.toPath(), outputDir.toPath());
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
            if (config.isSonarqubeEnabled()) {
                analyzerList.add(new SonarQubeAnalyzer(config));
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
            config = QaConfiguration.defaultConfig();
        }
        
        // Run analysis
        QualityReport report = analyze(projectDir, outputDir, config);
        
        // Exit with appropriate code
        System.exit("pass".equals(report.getOverallStatus()) ? 0 : 1);
    }
}
