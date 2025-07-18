package com.identitybridge.qa.analyzer;

import com.identitybridge.qa.config.QaConfiguration;
import com.identitybridge.qa.model.AnalysisResult;
import com.identitybridge.qa.model.Violation;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checkstyle analyzer implementation
 */
public class CheckstyleAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(CheckstyleAnalyzer.class);
    
    private final QaConfiguration config;
    
    public CheckstyleAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "checkstyle";
    }
    
    @Override
    public String getType() {
        return "static";
    }
    
    @Override
    public boolean isAvailable() {
        // Checkstyle is always available as it's a dependency
        return true;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running Checkstyle analysis on: {}", projectPath);
        
        try {
            // Load configuration
            Configuration configuration = loadConfiguration(projectPath);
            
            // Create checker
            Checker checker = new Checker();
            checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
            checker.configure(configuration);
            
            // Set up result collector
            ResultCollector collector = new ResultCollector();
            checker.addListener(collector);
            
            // Find Java files
            List<File> files = findJavaFiles(projectPath);
            logger.info("Found {} Java files to analyze", files.size());
            
            // Run analysis
            int errorCount = checker.process(files);
            
            // Build result
            return buildResult(collector, files.size(), errorCount);
            
        } catch (CheckstyleException e) {
            throw new AnalysisException("Checkstyle analysis failed", e);
        }
    }
    
    private Configuration loadConfiguration(Path projectPath) throws CheckstyleException {
        File configFile = projectPath.resolve(config.getCheckstyleConfigPath()).toFile();
        
        if (!configFile.exists()) {
            // Use default configuration from resources
            configFile = new File(getClass().getClassLoader()
                .getResource("default-configs/checkstyle.xml").getFile());
        }
        
        return ConfigurationLoader.loadConfiguration(
            configFile.getAbsolutePath(),
            new PropertiesExpander(System.getProperties())
        );
    }
    
    private List<File> findJavaFiles(Path projectPath) {
        List<File> files = new ArrayList<>();
        Path srcPath = projectPath.resolve("src/main/java");
        
        if (srcPath.toFile().exists()) {
            findJavaFilesRecursive(srcPath.toFile(), files);
        }
        
        return files;
    }
    
    private void findJavaFilesRecursive(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    findJavaFilesRecursive(child, files);
                } else if (child.getName().endsWith(".java")) {
                    files.add(child);
                }
            }
        }
    }
    
    private AnalysisResult buildResult(ResultCollector collector, int fileCount, int errorCount) {
        List<Violation> violations = collector.getViolations();
        String status = violations.stream()
            .anyMatch(v -> "error".equals(v.getSeverity())) ? "fail" : "pass";
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("filesAnalyzed", fileCount);
        metrics.put("violationsFound", violations.size());
        metrics.put("errors", errorCount);
        
        return AnalysisResult.builder()
            .type(getName())
            .status(status)
            .summary(String.format("Checkstyle found %d violations in %d files", 
                violations.size(), fileCount))
            .violations(violations)
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Listener to collect Checkstyle results
     */
    private static class ResultCollector implements AuditListener {
        private final List<Violation> violations = new ArrayList<>();
        
        @Override
        public void auditStarted(AuditEvent event) {
            // No action needed
        }
        
        @Override
        public void auditFinished(AuditEvent event) {
            // No action needed
        }
        
        @Override
        public void fileStarted(AuditEvent event) {
            // No action needed
        }
        
        @Override
        public void fileFinished(AuditEvent event) {
            // No action needed
        }
        
        @Override
        public void addError(AuditEvent event) {
            String severity = getSeverity(event.getSeverityLevel());
            
            violations.add(Violation.builder()
                .severity(severity)
                .file(event.getFileName())
                .line(event.getLine())
                .message(event.getMessage())
                .rule(event.getSourceName())
                .build());
        }
        
        @Override
        public void addException(AuditEvent event, Throwable throwable) {
            violations.add(Violation.builder()
                .severity("error")
                .file(event.getFileName())
                .line(0)
                .message("Exception: " + throwable.getMessage())
                .build());
        }
        
        private String getSeverity(SeverityLevel level) {
            switch (level) {
                case ERROR:
                    return "error";
                case WARNING:
                    return "warning";
                case INFO:
                    return "info";
                default:
                    return "info";
            }
        }
        
        public List<Violation> getViolations() {
            return violations;
        }
    }
}
