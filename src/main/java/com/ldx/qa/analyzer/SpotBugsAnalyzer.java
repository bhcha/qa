package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpotBugs analyzer implementation
 */
public class SpotBugsAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(SpotBugsAnalyzer.class);
    
    private final QaConfiguration config;
    
    public SpotBugsAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "spotbugs";
    }
    
    @Override
    public String getType() {
        return "static";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running SpotBugs analysis on: {}", projectPath);
        
        try {
            // Create reports directory
            Path reportsDir = projectPath.resolve("build/reports/spotbugs");
            reportsDir.toFile().mkdirs();
            
            // Run SpotBugs using command line to generate both XML and HTML
            runSpotBugsCommand(projectPath, reportsDir);
            
            // Count violations by priority from XML
            Path xmlReportPath = reportsDir.resolve("main.xml");
            Map<Integer, Integer> priorityCounts = countViolationsByPriority(xmlReportPath);
            
            // Build result
            return buildPriorityBasedResult(priorityCounts);
                
        } catch (Exception e) {
            throw new AnalysisException("SpotBugs analysis failed", e);
        }
    }
    
    private void runSpotBugsCommand(Path projectPath, Path reportsDir) throws Exception {
        Path classesDir = projectPath.resolve("build/classes/java/main");
        Path excludeFile = projectPath.resolve(config.getSpotbugsExcludePath());
        
        // Check if classes directory exists
        if (!classesDir.toFile().exists()) {
            logger.warn("Classes directory not found: {}", classesDir);
            createEmptyReports(reportsDir);
            return;
        }
        
        // Generate XML report
        List<String> xmlCommand = buildSpotBugsCommand(classesDir, excludeFile,
            reportsDir.resolve("main.xml"), "xml");
        executeCommand(xmlCommand, projectPath);
        
        // Generate HTML report using SpotBugs native HTML format
        List<String> htmlCommand = buildSpotBugsCommand(classesDir, excludeFile,
            reportsDir.resolve("main.html"), "html");
        executeCommand(htmlCommand, projectPath);
        
        logger.info("SpotBugs reports generated successfully");
    }
    
    private List<String> buildSpotBugsCommand(Path classesDir, Path excludeFile,
                                            Path outputPath, String format) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        // Use full classpath to ensure all dependencies are available
        command.add(getSpotBugsClasspath());
        command.add("edu.umd.cs.findbugs.FindBugs2");
        command.add("-" + format);
        command.add("-effort:min"); // Use minimal effort for speed
        // Note: -timeout option is not supported in SpotBugs 4.7.3
        
        if (excludeFile.toFile().exists()) {
            command.add("-exclude");
            command.add(excludeFile.toString());
        }
        
        command.add("-output");
        command.add(outputPath.toString());
        command.add(classesDir.toString());
        
        return command;
    }
    
    private String getSpotBugsClasspath() {
        // Use full classpath to ensure all dependencies (including SLF4J) are available
        String fullClasspath = System.getProperty("java.class.path");
        
        logger.debug("Full classpath: {}", fullClasspath);
        
        // Check if SpotBugs is available in the classpath
        boolean hasSpotBugs = false;
        for (String path : fullClasspath.split(System.getProperty("path.separator"))) {
            if (path.contains("spotbugs") || path.contains("findbugs")) {
                logger.debug("Found SpotBugs JAR: {}", path);
                hasSpotBugs = true;
                break;
            }
        }
        
        if (!hasSpotBugs) {
            logger.warn("No SpotBugs JARs found in classpath, but proceeding with full classpath");
        }
        
        logger.info("Using full classpath for SpotBugs execution to ensure all dependencies are available");
        return fullClasspath;
    }
    
    private void executeCommand(List<String> command, Path workingDir) throws Exception {
        logger.info("Executing SpotBugs command: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output from the process
        StringBuilder output = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            logger.warn("SpotBugs process completed with exit code: {}", exitCode);
            logger.warn("SpotBugs output: {}", output.toString());
        } else {
            logger.info("SpotBugs executed successfully");
            if (output.length() > 0) {
                logger.debug("SpotBugs output: {}", output.toString());
            }
        }
    }
    
    private void createEmptyReports(Path reportsDir) throws Exception {
        // Create empty XML report
        String emptyXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<BugCollection version=\"4.7.3\" timestamp=\"" + 
                         LocalDateTime.now() + "\">\n" +
                         "</BugCollection>\n";
        java.nio.file.Files.write(reportsDir.resolve("main.xml"), emptyXml.getBytes());
        
        // Create empty HTML report
        String emptyHtml = "<!DOCTYPE html>\n<html>\n<head>\n<title>SpotBugs Report</title>\n</head>\n" +
                          "<body>\n<h1>SpotBugs Report</h1>\n<p>No classes found for analysis.</p>\n</body>\n</html>\n";
        java.nio.file.Files.write(reportsDir.resolve("main.html"), emptyHtml.getBytes());
    }
    
    private int countViolationsFromXml(Path xmlPath) {
        if (!xmlPath.toFile().exists()) {
            return 0;
        }
        
        try {
            String content = java.nio.file.Files.readString(xmlPath);
            // Simple count of <BugInstance> tags
            return content.split("<BugInstance").length - 1;
        } catch (Exception e) {
            logger.warn("Failed to count violations from XML: {}", e.getMessage());
            return 0;
        }
    }
    
    
    private AnalysisResult buildSimpleResult(int violationCount) {
        String status = violationCount > config.getSpotbugsFailureThreshold() ? "fail" : "pass";
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("violationsFound", violationCount);
        
        return AnalysisResult.builder()
            .type(getName())
            .status(status)
            .summary(String.format("SpotBugs found %d violations", violationCount))
            .violations(new ArrayList<>()) // Empty list - we just show count
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private AnalysisResult buildPriorityBasedResult(Map<Integer, Integer> priorityCounts) {
        // Calculate total violations
        int totalViolations = priorityCounts.values().stream().mapToInt(Integer::intValue).sum();
        
        // Check each priority threshold
        boolean failed = false;
        StringBuilder failureReason = new StringBuilder();
        
        int p1Count = priorityCounts.getOrDefault(1, 0);
        int p2Count = priorityCounts.getOrDefault(2, 0);
        int p3Count = priorityCounts.getOrDefault(3, 0);
        int p4Count = priorityCounts.getOrDefault(4, 0);
        
        if (p1Count > config.getSpotbugsPriority1Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 1: %d > %d; ", p1Count, config.getSpotbugsPriority1Threshold()));
        }
        if (p2Count > config.getSpotbugsPriority2Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 2: %d > %d; ", p2Count, config.getSpotbugsPriority2Threshold()));
        }
        if (p3Count > config.getSpotbugsPriority3Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 3: %d > %d; ", p3Count, config.getSpotbugsPriority3Threshold()));
        }
        if (p4Count > config.getSpotbugsPriority4Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 4: %d > %d; ", p4Count, config.getSpotbugsPriority4Threshold()));
        }
        
        String status = failed ? "fail" : "pass";
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("violationsFound", totalViolations);
        metrics.put("priority1Count", p1Count);
        metrics.put("priority2Count", p2Count);
        metrics.put("priority3Count", p3Count);
        metrics.put("priority4Count", p4Count);
        
        String summary = String.format("SpotBugs found %d violations (P1:%d, P2:%d, P3:%d, P4:%d)",
                totalViolations, p1Count, p2Count, p3Count, p4Count);
        
        if (failed) {
            summary += " - FAILED: " + failureReason.toString();
        }
        
        return AnalysisResult.builder()
            .type(getName())
            .status(status)
            .summary(summary)
            .violations(new ArrayList<>()) // Empty list - we just show count
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private Map<Integer, Integer> countViolationsByPriority(Path xmlReportPath) {
        Map<Integer, Integer> priorityCounts = new HashMap<>();
        
        if (!xmlReportPath.toFile().exists()) {
            logger.warn("SpotBugs XML report not found: {}", xmlReportPath);
            return priorityCounts;
        }
        
        try {
            String content = java.nio.file.Files.readString(xmlReportPath);
            
            // Parse bugs from XML and count by priority
            // SpotBugs XML format: <BugInstance type="XX" priority="1" rank="XX" ...>
            String[] bugs = content.split("<BugInstance ");
            
            for (int i = 1; i < bugs.length; i++) {
                String bug = bugs[i];
                int priorityStart = bug.indexOf("priority=\"");
                if (priorityStart != -1) {
                    priorityStart += 10; // length of 'priority="'
                    int priorityEnd = bug.indexOf("\"", priorityStart);
                    if (priorityEnd != -1) {
                        try {
                            int priority = Integer.parseInt(bug.substring(priorityStart, priorityEnd));
                            priorityCounts.put(priority, priorityCounts.getOrDefault(priority, 0) + 1);
                        } catch (NumberFormatException e) {
                            logger.warn("Failed to parse priority from SpotBugs violation: {}", bug.substring(0, Math.min(100, bug.length())));
                        }
                    }
                }
            }
            
            logger.info("SpotBugs priority distribution: {}", priorityCounts);
            
        } catch (Exception e) {
            logger.warn("Failed to parse SpotBugs XML report: {}", e.getMessage());
        }
        
        return priorityCounts;
    }
}
