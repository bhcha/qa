package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PMD analyzer implementation
 */
public class PmdAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(PmdAnalyzer.class);

    private final QaConfiguration config;

    public PmdAnalyzer(QaConfiguration config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "pmd";
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
        logger.info("Running PMD analysis on: {}", projectPath);

        try {
            // Create reports directory
            Path reportsDir = projectPath.resolve("build/reports/pmd");
            reportsDir.toFile().mkdirs();

            // Run PMD using command line to generate both XML and HTML
            runPmdCommand(projectPath, reportsDir);

            // Count violations by priority from XML
            Path xmlReportPath = reportsDir.resolve("main.xml");
            Map<Integer, Integer> priorityCounts = countViolationsByPriority(xmlReportPath);

            // Build result
            return buildPriorityBasedResult(priorityCounts);

        } catch (Exception e) {
            throw new AnalysisException("PMD analysis failed", e);
        }
    }

    private void runPmdCommand(Path projectPath, Path reportsDir) throws Exception {
        Path rulesetPath = projectPath.resolve(config.getPmdRulesetPath());
        Path sourceDir = projectPath.resolve("src/main/java");

        // Generate XML report
        List<String> xmlCommand = buildPmdCommand(rulesetPath, sourceDir,
            reportsDir.resolve("main.xml"), "xml");
        executeCommand(xmlCommand, projectPath);

        // Generate HTML report using PMD native HTML format
        List<String> htmlCommand = buildPmdCommand(rulesetPath, sourceDir,
            reportsDir.resolve("main.html"), "html");
        executeCommand(htmlCommand, projectPath);

        logger.info("PMD reports generated successfully");
    }

    private List<String> buildPmdCommand(Path rulesetPath, Path sourceDir,
                                        Path outputPath, String format) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("net.sourceforge.pmd.PMD");
        command.add("-d");
        command.add(sourceDir.toString());
        command.add("-f");
        command.add(format);
        command.add("-R");

        if (rulesetPath.toFile().exists()) {
            command.add(rulesetPath.toString());
        } else {
            // Use rules without XPath to avoid Saxon issues
            command.add("category/java/bestpractices.xml,category/java/codestyle.xml,category/java/design.xml,category/java/errorprone.xml,category/java/performance.xml");
        }

        command.add("-reportfile");
        command.add(outputPath.toString());
        // Verbose mode for debugging (use --verbose for PMD 6.x)
        command.add("--verbose");

        return command;
    }

    private void executeCommand(List<String> command, Path workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        logger.info("Executing PMD command: {}", String.join(" ", command));

        Process process = pb.start();

        // Capture output for debugging
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
            logger.warn("PMD process completed with exit code: {}", exitCode);
            logger.warn("PMD output: {}", output.toString());
        } else {
            logger.info("PMD completed successfully");
        }
    }

    private int countViolationsFromXml(Path xmlPath) {
        try {
            if (!xmlPath.toFile().exists()) {
                return 0;
            }

            String content = Files.readString(xmlPath);
            // Count <violation> tags
            int count = 0;
            int index = content.indexOf("<violation");
            while (index != -1) {
                count++;
                index = content.indexOf("<violation", index + 1);
            }
            return count;

        } catch (Exception e) {
            logger.warn("Failed to count violations from XML: {}", e.getMessage());
            return 0;
        }
    }


    private AnalysisResult buildSimpleResult(int violationCount) {
        String status = violationCount > config.getPmdFailureThreshold() ? "fail" : "pass";

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("violationsFound", violationCount);

        return AnalysisResult.builder()
            .type(getName())
            .status(status)
            .summary(String.format("PMD found %d violations", violationCount))
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
        int p5Count = priorityCounts.getOrDefault(5, 0);
        
        if (p1Count > config.getPmdPriority1Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 1: %d > %d; ", p1Count, config.getPmdPriority1Threshold()));
        }
        if (p2Count > config.getPmdPriority2Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 2: %d > %d; ", p2Count, config.getPmdPriority2Threshold()));
        }
        if (p3Count > config.getPmdPriority3Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 3: %d > %d; ", p3Count, config.getPmdPriority3Threshold()));
        }
        if (p4Count > config.getPmdPriority4Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 4: %d > %d; ", p4Count, config.getPmdPriority4Threshold()));
        }
        if (p5Count > config.getPmdPriority5Threshold()) {
            failed = true;
            failureReason.append(String.format("Priority 5: %d > %d; ", p5Count, config.getPmdPriority5Threshold()));
        }
        
        String status = failed ? "fail" : "pass";
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("violationsFound", totalViolations);
        metrics.put("priority1Count", p1Count);
        metrics.put("priority2Count", p2Count);
        metrics.put("priority3Count", p3Count);
        metrics.put("priority4Count", p4Count);
        metrics.put("priority5Count", p5Count);
        
        String summary = String.format("PMD found %d violations (P1:%d, P2:%d, P3:%d, P4:%d, P5:%d)",
                totalViolations, p1Count, p2Count, p3Count, p4Count, p5Count);
        
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
            logger.warn("PMD XML report not found: {}", xmlReportPath);
            return priorityCounts;
        }
        
        try {
            String content = Files.readString(xmlReportPath);
            
            // Parse violations from XML and count by priority
            // PMD XML format: <violation beginline="XX" endline="XX" begincolumn="XX" endcolumn="XX" rule="RuleName" ruleset="RuleSet" priority="1" ...>
            String[] violations = content.split("<violation ");
            
            for (int i = 1; i < violations.length; i++) {
                String violation = violations[i];
                int priorityStart = violation.indexOf("priority=\"");
                if (priorityStart != -1) {
                    priorityStart += 10; // length of 'priority="'
                    int priorityEnd = violation.indexOf("\"", priorityStart);
                    if (priorityEnd != -1) {
                        try {
                            int priority = Integer.parseInt(violation.substring(priorityStart, priorityEnd));
                            priorityCounts.put(priority, priorityCounts.getOrDefault(priority, 0) + 1);
                        } catch (NumberFormatException e) {
                            logger.warn("Failed to parse priority from PMD violation: {}", violation.substring(0, Math.min(100, violation.length())));
                        }
                    }
                }
            }
            
            logger.info("PMD priority distribution: {}", priorityCounts);
            
        } catch (Exception e) {
            logger.warn("Failed to parse PMD XML report: {}", e.getMessage());
        }
        
        return priorityCounts;
    }

}
