package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Kingfisher secret scanner analyzer implementation
 * Detects hardcoded secrets, API keys, and credentials in source code
 */
public class KingfisherAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(KingfisherAnalyzer.class);
    
    private final QaConfiguration config;
    private static final String KINGFISHER_BINARY = "kingfisher";
    
    
    public KingfisherAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "kingfisher";
    }
    
    @Override
    public String getType() {
        return "kingfisher";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if Kingfisher binary is available
            Process process = new ProcessBuilder(KINGFISHER_BINARY, "--version")
                .redirectErrorStream(true)
                .start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    logger.info("Kingfisher available: {}", version);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Kingfisher not available: {}", e.getMessage());
        }
        return false;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running Kingfisher secret scanning on: {}", projectPath);
        
        try {
            // Create reports directory
            Path reportsDir = projectPath.resolve("build/reports/kingfisher");
            Files.createDirectories(reportsDir);
            
            // Run Kingfisher scan
            List<Violation> violations = runKingfisherScan(projectPath, reportsDir);
            
            // Generate reports
            generateReports(projectPath, reportsDir, violations);
            
            // Build result
            return buildResult(violations);
            
        } catch (Exception e) {
            throw new AnalysisException("Kingfisher analysis failed", e);
        }
    }
    
    private List<Violation> runKingfisherScan(Path projectPath, Path reportsDir) throws Exception {
        List<Violation> violations = new ArrayList<>();
        
        // Build Kingfisher command
        List<String> command = buildKingfisherCommand(projectPath, reportsDir);
        
        logger.debug("Executing Kingfisher command: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Collect all output for JSON parsing
        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Kingfisher output: {}", line);
                outputBuilder.append(line).append("\n");
            }
        }
        
        // Parse JSON output
        String output = outputBuilder.toString().trim();
        logger.debug("Complete Kingfisher output: {}", output);
        if (!output.isEmpty()) {
            violations.addAll(parseJsonOutput(output, projectPath));
        } else {
            logger.warn("Kingfisher produced no output");
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0 && violations.isEmpty()) {
            logger.warn("Kingfisher exited with code {} but no findings detected", exitCode);
        }
        
        return violations;
    }
    
    private List<String> buildKingfisherCommand(Path projectPath, Path reportsDir) {
        List<String> command = new ArrayList<>();
        command.add(KINGFISHER_BINARY);
        command.add("scan");
        command.add(projectPath.toString());
        
        // Add configuration options (temporarily disabled due to parsing issues)
        // Path configPath = projectPath.resolve(config.getKingfisherConfigPath());
        // if (Files.exists(configPath)) {
        //     command.add("--rules-path");
        //     command.add(configPath.toString());
        // }
        
        // Add baseline file if exists (temporarily disabled)
        // Path baselinePath = projectPath.resolve("config/kingfisher/baseline.yaml");
        // if (Files.exists(baselinePath)) {
        //     command.add("--baseline-file");
        //     command.add(baselinePath.toString());
        // }
        
        // Output format options
        command.add("--confidence");
        command.add(config.getKingfisherConfidenceLevel());
        
        // Exclude patterns
        command.add("--exclude");
        command.add("build/**");
        command.add("--exclude");
        command.add(".git/**");
        command.add("--exclude");
        command.add("**/*.class");
        
        // Disable validation if configured (note: Kingfisher uses --no-validate, not --validate)
        if (!config.isKingfisherValidationEnabled()) {
            command.add("--no-validate");
        }
        
        // Use JSON output for easier parsing
        command.add("--format");
        command.add("json");
        
        return command;
    }
    
    private List<Violation> parseJsonOutput(String output, Path projectPath) {
        List<Violation> violations = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Extract JSON from the mixed output (remove INFO lines and join)
            StringBuilder jsonBuilder = new StringBuilder();
            String[] lines = output.split("\n");
            boolean insideJson = false;
            int braceDepth = 0;
            int bracketDepth = 0;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("INFO") || line.startsWith("WARN") || line.startsWith("ERROR")) {
                    continue;
                }
                
                if (line.startsWith("[")) {
                    insideJson = true;
                }
                
                if (insideJson) {
                    jsonBuilder.append(line);
                    
                    // Count brackets and braces to know when JSON is complete
                    for (char c : line.toCharArray()) {
                        if (c == '[') bracketDepth++;
                        else if (c == ']') bracketDepth--;
                        else if (c == '{') braceDepth++;
                        else if (c == '}') braceDepth--;
                    }
                    
                    // If we're back to zero depth and line ends with ], JSON is complete
                    if (bracketDepth == 0 && braceDepth == 0 && line.endsWith("]")) {
                        break;
                    }
                }
            }
            
            String jsonString = jsonBuilder.toString();
            logger.debug("Extracted JSON string length: {}", jsonString.length());
            
            // If JSON is incomplete, try to close it properly
            if (!jsonString.isEmpty() && !jsonString.trim().endsWith("]")) {
                // Count how many objects need to be closed
                while (braceDepth > 0) {
                    jsonBuilder.append("}");
                    braceDepth--;
                }
                while (bracketDepth > 0) {
                    jsonBuilder.append("]");
                    bracketDepth--;
                }
                jsonString = jsonBuilder.toString();
                logger.debug("Auto-completed JSON string length: {}", jsonString.length());
            }
            
            if (!jsonString.isEmpty()) {
                try {
                    JsonNode rootNode = mapper.readTree(jsonString);
                    
                    // Handle the main findings array
                    if (rootNode.isArray()) {
                        for (JsonNode ruleResult : rootNode) {
                            if (ruleResult.has("matches")) {
                                JsonNode matches = ruleResult.get("matches");
                                for (JsonNode match : matches) {
                                    Violation violation = parseKingfisherMatch(match, projectPath);
                                    if (violation != null) {
                                        violations.add(violation);
                                    }
                                }
                            }
                        }
                        logger.debug("Successfully parsed {} violations from Kingfisher output", violations.size());
                    }
                } catch (Exception parseException) {
                    logger.warn("JSON parsing failed even after auto-completion: {}", parseException.getMessage());
                    // Try line-by-line parsing as fallback
                    violations.addAll(parseLineByLine(output, projectPath, mapper));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse Kingfisher JSON output: {}", e.getMessage());
            logger.debug("Raw output: {}", output);
        }
        
        return violations;
    }
    
    private List<Violation> parseLineByLine(String output, Path projectPath, ObjectMapper mapper) {
        List<Violation> violations = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("\"finding\"")) {
                continue;
            }
            
            try {
                // Try to find individual JSON objects that contain findings
                if (line.contains("\"path\"") && line.contains("\"line\"") && line.contains("\"snippet\"")) {
                    // Create a simple violation from extracted information
                    String path = extractJsonValue(line, "path");
                    String lineNum = extractJsonValue(line, "line");
                    String snippet = extractJsonValue(line, "snippet");
                    String ruleName = extractJsonValue(line, "name");
                    
                    if (path != null && !path.isEmpty()) {
                        String relativePath = makeRelativePath(path, projectPath);
                        int lineNumber = 0;
                        try {
                            lineNumber = Integer.parseInt(lineNum);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                        
                        Violation violation = Violation.builder()
                            .file(relativePath)
                            .line(lineNumber)
                            .column(0)
                            .severity("warning")
                            .rule(ruleName != null ? ruleName : "secret-detection")
                            .message("Secret detected: " + (snippet != null && snippet.length() > 50 ? 
                                snippet.substring(0, 50) + "..." : snippet))
                            .type("secret")
                            .category("security")
                            .build();
                        
                        violations.add(violation);
                    }
                }
            } catch (Exception e) {
                // Skip this line
            }
        }
        
        logger.debug("Fallback parsing extracted {} violations", violations.size());
        return violations;
    }
    
    private String extractJsonValue(String line, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*";
        int index = line.indexOf(pattern);
        if (index == -1) return null;
        
        int start = index + pattern.length();
        if (start >= line.length()) return null;
        
        char firstChar = line.charAt(start);
        if (firstChar == '"') {
            // String value
            start++;
            int end = line.indexOf('"', start);
            if (end == -1) return null;
            return line.substring(start, end);
        } else {
            // Number value
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) {
                end++;
            }
            if (end == start) return null;
            return line.substring(start, end);
        }
    }
    
    private String makeRelativePath(String absolutePath, Path projectPath) {
        try {
            Path filePath = Path.of(absolutePath);
            if (filePath.isAbsolute()) {
                return projectPath.relativize(filePath).toString();
            }
            return absolutePath;
        } catch (Exception e) {
            return absolutePath;
        }
    }
    
    private Violation parseKingfisherMatch(JsonNode match, Path projectPath) {
        try {
            if (!match.has("finding")) {
                return null;
            }
            
            JsonNode finding = match.get("finding");
            JsonNode rule = match.has("rule") ? match.get("rule") : null;
            
            String file = finding.has("path") ? finding.get("path").asText() : "unknown";
            int line = finding.has("line") ? finding.get("line").asInt() : 0;
            String ruleId = rule != null && rule.has("id") ? rule.get("id").asText() : "unknown";
            String ruleName = rule != null && rule.has("name") ? rule.get("name").asText() : "Secret detected";
            String confidence = finding.has("confidence") ? finding.get("confidence").asText() : "medium";
            String snippet = finding.has("snippet") ? finding.get("snippet").asText() : "";
            
            // Convert absolute path to relative path if it's absolute
            String relativePath;
            try {
                Path filePath = Path.of(file);
                if (filePath.isAbsolute()) {
                    relativePath = projectPath.relativize(filePath).toString();
                } else {
                    relativePath = file;
                }
            } catch (Exception e) {
                relativePath = file;
            }
            
            String message = String.format("%s: %s", ruleName, snippet.length() > 50 ? 
                snippet.substring(0, 50) + "..." : snippet);
            
            return Violation.builder()
                .file(relativePath)
                .line(line)
                .column(0)
                .severity(mapConfidenceToSeverity(confidence))
                .rule(ruleId)
                .message(message)
                .type("secret")
                .category("security")
                .build();
        } catch (Exception e) {
            logger.warn("Failed to parse Kingfisher match: {}", e.getMessage());
            return null;
        }
    }
    
    private String mapConfidenceToSeverity(String confidence) {
        switch (confidence.toLowerCase()) {
            case "high":
            case "critical":
                return "error";
            case "medium":
                return "warning";
            case "low":
                return "info";
            default:
                return "warning";
        }
    }
    
    
    private void generateReports(Path projectPath, Path reportsDir, List<Violation> violations) 
            throws IOException {
        // Generate HTML report
        Path htmlReport = reportsDir.resolve("main.html");
        generateHtmlReport(violations, htmlReport);
        
        // Generate JSON report
        Path jsonReport = reportsDir.resolve("findings.json");
        generateJsonReport(violations, jsonReport);
        
        // Run Kingfisher again with JSON output for detailed report
        try {
            List<String> jsonCommand = new ArrayList<>();
            jsonCommand.add(KINGFISHER_BINARY);
            jsonCommand.add("scan");
            jsonCommand.add(projectPath.toString());
            jsonCommand.add("--output-format");
            jsonCommand.add("json");
            jsonCommand.add("--output");
            jsonCommand.add(reportsDir.resolve("detailed-report.json").toString());
            jsonCommand.add("--redact");
            
            ProcessBuilder pb = new ProcessBuilder(jsonCommand);
            pb.directory(projectPath.toFile());
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            logger.warn("Failed to generate detailed JSON report: {}", e.getMessage());
        }
    }
    
    private void generateHtmlReport(List<Violation> violations, Path outputPath) 
            throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Kingfisher Secret Scan Report</title>\n");
        html.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\">\n");
        html.append("</head>\n<body>\n");
        html.append("<div class=\"container mt-5\">\n");
        html.append("<h1>Kingfisher Secret Scan Report</h1>\n");
        html.append("<p class=\"text-muted\">Generated: ").append(LocalDateTime.now()).append("</p>\n");
        
        // Summary
        html.append("<div class=\"card mb-4\">\n");
        html.append("<div class=\"card-header\"><h5>Summary</h5></div>\n");
        html.append("<div class=\"card-body\">\n");
        html.append("<p>Total findings: <strong>").append(violations.size()).append("</strong></p>\n");
        
        // Count by severity
        Map<String, Long> severityCount = violations.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Violation::getSeverity, 
                java.util.stream.Collectors.counting()
            ));
        
        html.append("<p>By severity:</p>\n<ul>\n");
        severityCount.forEach((severity, count) -> 
            html.append("<li>").append(severity).append(": ").append(count).append("</li>\n")
        );
        html.append("</ul>\n</div>\n</div>\n");
        
        // Findings table
        if (!violations.isEmpty()) {
            html.append("<h2>Findings</h2>\n");
            html.append("<table class=\"table table-striped\">\n");
            html.append("<thead><tr>\n");
            html.append("<th>File</th><th>Line</th><th>Severity</th><th>Rule</th><th>Message</th>\n");
            html.append("</tr></thead>\n<tbody>\n");
            
            // Sort violations by severity (error first, then warning, then info)
            List<Violation> sortedViolations = violations.stream()
                .sorted((v1, v2) -> {
                    int priority1 = getSeverityPriority(v1.getSeverity());
                    int priority2 = getSeverityPriority(v2.getSeverity());
                    return Integer.compare(priority1, priority2);
                })
                .collect(java.util.stream.Collectors.toList());
            
            for (Violation v : sortedViolations) {
                html.append("<tr>\n");
                html.append("<td>").append(v.getFile()).append("</td>\n");
                html.append("<td>").append(v.getLine()).append("</td>\n");
                html.append("<td><span class=\"badge ")
                    .append(getSeverityBadgeClass(v.getSeverity())).append("\">")
                    .append(v.getSeverity()).append("</span></td>\n");
                html.append("<td>").append(v.getRule()).append("</td>\n");
                html.append("<td>").append(v.getMessage()).append("</td>\n");
                html.append("</tr>\n");
            }
            
            html.append("</tbody>\n</table>\n");
        }
        
        html.append("</div>\n</body>\n</html>");
        
        Files.write(outputPath, html.toString().getBytes());
    }
    
    private String getSeverityBadgeClass(String severity) {
        switch (severity.toLowerCase()) {
            case "error":
                return "bg-danger";
            case "warning":
                return "bg-warning text-dark";
            case "info":
                return "bg-info text-dark";
            default:
                return "bg-secondary";
        }
    }
    
    private int getSeverityPriority(String severity) {
        switch (severity.toLowerCase()) {
            case "error":
                return 1;
            case "warning":
                return 2;
            case "info":
                return 3;
            default:
                return 4;
        }
    }
    
    private void generateJsonReport(List<Violation> violations, Path outputPath) 
            throws IOException {
        // Simple JSON generation
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
        json.append("  \"totalFindings\": ").append(violations.size()).append(",\n");
        json.append("  \"findings\": [\n");
        
        for (int i = 0; i < violations.size(); i++) {
            Violation v = violations.get(i);
            json.append("    {\n");
            json.append("      \"file\": \"").append(v.getFile()).append("\",\n");
            json.append("      \"line\": ").append(v.getLine()).append(",\n");
            json.append("      \"column\": ").append(v.getColumn()).append(",\n");
            json.append("      \"severity\": \"").append(v.getSeverity()).append("\",\n");
            json.append("      \"rule\": \"").append(v.getRule()).append("\",\n");
            json.append("      \"message\": \"").append(v.getMessage().replace("\"", "\\\"")).append("\"\n");
            json.append("    }");
            if (i < violations.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ]\n}\n");
        
        Files.write(outputPath, json.toString().getBytes());
    }
    
    private AnalysisResult buildResult(List<Violation> violations) {
        String status = violations.isEmpty() ? "pass" : "fail";
        
        // Count active vs inactive secrets
        long activeSecrets = violations.stream()
            .filter(v -> "error".equals(v.getSeverity()))
            .count();
        long inactiveSecrets = violations.stream()
            .filter(v -> "warning".equals(v.getSeverity()))
            .count();
        
        StringBuilder summary = new StringBuilder();
        summary.append("Kingfisher Secret Scan Results:\n");
        summary.append("Total findings: ").append(violations.size()).append("\n");
        
        if (activeSecrets > 0) {
            summary.append("⚠️  Active secrets found: ").append(activeSecrets).append("\n");
        }
        if (inactiveSecrets > 0) {
            summary.append("Inactive secrets found: ").append(inactiveSecrets).append("\n");
        }
        
        if (violations.isEmpty()) {
            summary.append("✅ No secrets detected in the codebase.\n");
        } else {
            summary.append("\nTop findings:\n");
            violations.stream()
                .limit(5)
                .forEach(v -> summary.append("- ")
                    .append(v.getFile()).append(":").append(v.getLine())
                    .append(" [").append(v.getSeverity()).append("] ")
                    .append(v.getRule()).append("\n"));
            
            if (violations.size() > 5) {
                summary.append("... and ").append(violations.size() - 5).append(" more findings\n");
            }
        }
        
        return AnalysisResult.builder()
            .type("kingfisher")
            .status(status)
            .summary(summary.toString())
            .violations(violations)
            .metrics(Map.<String, Object>of(
                "totalFindings", violations.size(),
                "activeSecrets", activeSecrets,
                "inactiveSecrets", inactiveSecrets,
                "reportPath", "build/reports/kingfisher/main.html"
            ))
            .timestamp(LocalDateTime.now())
            .build();
    }
}