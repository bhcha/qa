package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JaCoCo coverage analyzer implementation
 */
public class JaCoCoAnalyzer implements com.ldx.qa.analyzer.Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(JaCoCoAnalyzer.class);
    
    private final QaConfiguration config;
    
    public JaCoCoAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "jacoco";
    }
    
    @Override
    public String getType() {
        return "coverage";
    }
    
    @Override
    public boolean isAvailable() {
        // Check if JaCoCo data exists
        return true;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running JaCoCo coverage analysis on: {}", projectPath);
        
        try {
            // Look for JaCoCo execution data in various possible locations
            Path execFile = findJaCoCoExecutionData(projectPath);
            Path classesDir = projectPath.resolve("build/classes/java/main");
            Path reportsDir = projectPath.resolve("build/reports/jacoco/test");
            
            if (execFile == null) {
                return AnalysisResult.builder()
                    .type(getName())
                    .status("skipped")
                    .summary("No JaCoCo execution data found. JaCoCo plugin may not be enabled. Add 'jacoco' plugin to build.gradle.")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            logger.info("Found JaCoCo execution data: {}", execFile);
            
            if (!classesDir.toFile().exists()) {
                return AnalysisResult.builder()
                    .type(getName())
                    .status("skipped")
                    .summary("No compiled classes found. Compile project first.")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            // Create reports directory
            reportsDir.toFile().mkdirs();
            
            // Load execution data
            ExecutionDataStore executionData = loadExecutionData(execFile.toFile());
            SessionInfoStore sessionInfos = new SessionInfoStore();
            
            // Analyze coverage
            CoverageBuilder coverageBuilder = analyzeStructure(executionData, classesDir.toFile());
            IBundleCoverage bundleCoverage = coverageBuilder.getBundle("Coverage Analysis");
            
            // Generate HTML report
            generateHtmlReport(bundleCoverage, executionData, sessionInfos, 
                             classesDir.toFile(), projectPath.resolve("src/main/java").toFile(), 
                             reportsDir.toFile());
            
            // Calculate metrics from both bundle and HTML parsing
            double instructionCoverage = getInstructionCoverage(bundleCoverage);
            double branchCoverage = getBranchCoverage(bundleCoverage);
            double lineCoverage = getLineCoverage(bundleCoverage);
            
            // Parse HTML report for verification
            parseHtmlForMetrics(reportsDir.resolve("index.html").toFile());
            
            // Create violations for low coverage
            List<Violation> violations = createCoverageViolations(bundleCoverage, instructionCoverage, branchCoverage, lineCoverage);
            
            String status = violations.isEmpty() ? "pass" : "fail";
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("instructionCoverage", instructionCoverage);
            metrics.put("branchCoverage", branchCoverage);
            metrics.put("lineCoverage", lineCoverage);
            metrics.put("classCount", bundleCoverage.getClassCounter().getTotalCount());
            metrics.put("methodCount", bundleCoverage.getMethodCounter().getTotalCount());
            
            return AnalysisResult.builder()
                .type(getName())
                .status(status)
                .summary(String.format("Instruction Coverage: %.1f%%, Branch Coverage: %.1f%%, Line Coverage: %.1f%%", 
                                     instructionCoverage, branchCoverage, lineCoverage))
                .violations(violations)
                .metrics(metrics)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            throw new AnalysisException("JaCoCo analysis failed", e);
        }
    }
    
    private ExecutionDataStore loadExecutionData(File execFile) throws IOException {
        FileInputStream fis = new FileInputStream(execFile);
        ExecutionDataStore executionData = new ExecutionDataStore();
        SessionInfoStore sessionInfos = new SessionInfoStore();
        
        try {
            org.jacoco.core.data.ExecutionDataReader reader = 
                new org.jacoco.core.data.ExecutionDataReader(fis);
            reader.setExecutionDataVisitor(executionData);
            reader.setSessionInfoVisitor(sessionInfos);
            reader.read();
        } finally {
            fis.close();
        }
        
        return executionData;
    }
    
    private CoverageBuilder analyzeStructure(ExecutionDataStore executionData, File classesDir) throws IOException {
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        org.jacoco.core.analysis.Analyzer analyzer = new org.jacoco.core.analysis.Analyzer(executionData, coverageBuilder);
        analyzer.analyzeAll(classesDir);
        return coverageBuilder;
    }
    
    private void generateHtmlReport(IBundleCoverage bundleCoverage, ExecutionDataStore executionData,
                                  SessionInfoStore sessionInfos, File classesDir, File sourceDir, File reportDir) {
        try {
            HTMLFormatter htmlFormatter = new HTMLFormatter();
            IReportVisitor visitor = htmlFormatter.createVisitor(new FileMultiReportOutput(reportDir));
            
            visitor.visitInfo(sessionInfos.getInfos(), executionData.getContents());
            visitor.visitBundle(bundleCoverage, new DirectorySourceFileLocator(sourceDir, "utf-8", 4));
            visitor.visitEnd();
            
            logger.info("JaCoCo HTML report generated at: {}", reportDir.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("Failed to generate JaCoCo HTML report: {}", e.getMessage());
        }
    }
    
    private double getInstructionCoverage(IBundleCoverage bundleCoverage) {
        return getPercentage(bundleCoverage.getInstructionCounter());
    }
    
    private double getBranchCoverage(IBundleCoverage bundleCoverage) {
        return getPercentage(bundleCoverage.getBranchCounter());
    }
    
    private double getLineCoverage(IBundleCoverage bundleCoverage) {
        return getPercentage(bundleCoverage.getLineCounter());
    }
    
    private double getPercentage(org.jacoco.core.analysis.ICounter counter) {
        if (counter.getTotalCount() == 0) return 0.0;
        return (double) counter.getCoveredCount() / counter.getTotalCount() * 100.0;
    }
    
    private List<Violation> createCoverageViolations(IBundleCoverage bundleCoverage, 
                                                   double instructionCoverage, 
                                                   double branchCoverage, 
                                                   double lineCoverage) {
        List<Violation> violations = new ArrayList<>();
        
        // Check minimum coverage requirements from configuration
        double minInstructionCoverage = config.getJacocoInstructionThreshold();
        double minBranchCoverage = config.getJaccocoBranchThreshold();
        double minLineCoverage = config.getJacocoLineThreshold();
        
        if (instructionCoverage < minInstructionCoverage) {
            violations.add(Violation.builder()
                .severity("warning")
                .file("Overall Coverage")
                .line(0)
                .message(String.format("Instruction coverage %.1f%% is below minimum %.1f%%", 
                                     instructionCoverage, minInstructionCoverage))
                .build());
        }
        
        if (branchCoverage < minBranchCoverage) {
            violations.add(Violation.builder()
                .severity("warning")
                .file("Overall Coverage")
                .line(0)
                .message(String.format("Branch coverage %.1f%% is below minimum %.1f%%", 
                                     branchCoverage, minBranchCoverage))
                .build());
        }
        
        if (lineCoverage < minLineCoverage) {
            violations.add(Violation.builder()
                .severity("warning")
                .file("Overall Coverage")
                .line(0)
                .message(String.format("Line coverage %.1f%% is below minimum %.1f%%", 
                                     lineCoverage, minLineCoverage))
                .build());
        }
        
        // Note: Individual class coverage violations can be added here
        // when needed by iterating through coverage data
        
        return violations;
    }
    
    private void parseHtmlForMetrics(File htmlFile) {
        if (!htmlFile.exists()) {
            logger.warn("JaCoCo HTML report not found: {}", htmlFile.getPath());
            return;
        }
        
        try {
            String htmlContent = java.nio.file.Files.readString(htmlFile.toPath());
            
            // Parse the <tfoot> section for total coverage data
            if (htmlContent.contains("<tfoot>")) {
                String tfootSection = htmlContent.substring(
                    htmlContent.indexOf("<tfoot>"),
                    htmlContent.indexOf("</tfoot>") + 8
                );
                
                // Extract total metrics from the footer row
                extractTotalMetrics(tfootSection);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to parse JaCoCo HTML report: {}", e.getMessage());
        }
    }
    
    private void extractTotalMetrics(String tfootSection) {
        try {
            // Find the total row - contains "Total" and coverage data
            if (tfootSection.contains("Total")) {
                // Extract instruction coverage: "7,654 of 7,654" format
                String instructionPattern = "(\\d[\\d,]*) of (\\d[\\d,]*)";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(instructionPattern);
                java.util.regex.Matcher matcher = pattern.matcher(tfootSection);
                
                if (matcher.find()) {
                    String missedInstr = matcher.group(1).replace(",", "");
                    String totalInstr = matcher.group(2).replace(",", "");
                    
                    int missed = Integer.parseInt(missedInstr);
                    int total = Integer.parseInt(totalInstr);
                    double coverage = total > 0 ? ((double)(total - missed) / total) * 100.0 : 0.0;
                    
                    logger.info("JaCoCo HTML Parsing - Instructions: {}/{} = {:.1f}% coverage", 
                              (total - missed), total, coverage);
                }
                
                // Extract percentage values from class="ctr2" elements
                extractPercentageValues(tfootSection);
            }
        } catch (Exception e) {
            logger.warn("Failed to extract metrics from HTML: {}", e.getMessage());
        }
    }
    
    private void extractPercentageValues(String tfootSection) {
        // Find all percentage values in the footer
        String percentPattern = "class=\"ctr2\"[^>]*>(\\d+)%";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(percentPattern);
        java.util.regex.Matcher matcher = pattern.matcher(tfootSection);
        
        int count = 0;
        while (matcher.find() && count < 3) { // First 3 percentages: instruction, branch, complexity
            String percentage = matcher.group(1);
            String type = count == 0 ? "Instruction" : count == 1 ? "Branch" : "Complexity";
            logger.info("JaCoCo HTML Parsing - {} Coverage: {}%", type, percentage);
            count++;
        }
    }
    
    /**
     * Find JaCoCo execution data file in various possible locations
     */
    private Path findJaCoCoExecutionData(Path projectPath) {
        // List of possible JaCoCo execution data locations
        String[] possiblePaths = {
            "build/jacoco/test.exec",           // Standard Gradle location
            "build/jacoco/test/jacoco.exec",    // Alternative Gradle location
            "target/jacoco.exec",               // Maven location
            "target/site/jacoco/jacoco.exec",   // Maven site location
            "jacoco.exec",                      // Root project location
            "build/jacoco.exec",                // Build directory
            "build/test-results/jacoco.exec"    // Test results directory
        };
        
        for (String pathStr : possiblePaths) {
            Path execFile = projectPath.resolve(pathStr);
            if (execFile.toFile().exists()) {
                logger.info("Found JaCoCo execution data at: {}", execFile);
                return execFile;
            }
        }
        
        // Also try to find any .exec file in build directory
        try {
            Path buildDir = projectPath.resolve("build");
            if (buildDir.toFile().exists()) {
                return java.nio.file.Files.walk(buildDir)
                    .filter(path -> path.toString().endsWith(".exec"))
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            logger.debug("Error searching for .exec files: {}", e.getMessage());
        }
        
        logger.warn("No JaCoCo execution data found in any standard location");
        return null;
    }
}
