package com.ldx.qa.analyzer;

import com.ldx.qa.config.QaConfiguration;
import com.ldx.qa.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.LinkedHashMap;

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
            // Try Gradle first, then fallback to direct execution
            boolean gradleSuccess = runGradleCheckStyle(projectPath);
            
            if (!gradleSuccess) {
                logger.info("Gradle CheckStyle failed, running direct CheckStyle");
                runDirectCheckStyle(projectPath);
            }
            
            // Parse XML for violation count from both main and test
            Path buildReportsDir = projectPath.resolve("build/reports/checkstyle");
            Path mainXmlPath = buildReportsDir.resolve("main.xml");
            Path testXmlPath = buildReportsDir.resolve("test.xml");
            
            int mainViolations = countViolationsFromXml(mainXmlPath);
            int testViolations = countViolationsFromXml(testXmlPath);
            int totalViolations = mainViolations + testViolations;
            
            // Build result
            return buildSimpleResult(totalViolations);
            
        } catch (Exception e) {
            throw new AnalysisException("Checkstyle analysis failed", e);
        }
    }
    
    
    private boolean runGradleCheckStyle(Path projectPath) {
        List<String> command = new ArrayList<>();
        command.add("./gradlew");
        command.add("checkstyleMain");
        command.add("checkstyleTest");
        command.add("--stacktrace");
        
        logger.info("Running Gradle CheckStyle: {}", String.join(" ", command));
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder output = new StringBuilder();
                boolean taskNotFound = false;
                boolean checkstyleExecuted = false;
                
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("checkstyle") || line.contains("violation")) {
                        logger.info("Gradle CheckStyle: {}", line);
                    }
                    
                    // Check if checkstyle tasks were not found
                    if (line.contains("Task 'checkstyleMain' not found") || 
                        line.contains("Task 'checkstyleTest' not found")) {
                        taskNotFound = true;
                    }
                    
                    // Check if checkstyle actually executed
                    if (line.contains("checkstyleMain") && line.contains("SUCCESS")) {
                        checkstyleExecuted = true;
                    }
                }
                
                int exitCode = process.waitFor();
                logger.info("Gradle CheckStyle completed with exit code: {}", exitCode);
                
                // If tasks were not found, return false to trigger fallback
                if (taskNotFound) {
                    logger.info("CheckStyle Gradle tasks not found, will use fallback execution");
                    return false;
                }
                
                // Don't fail on CheckStyle violations (exit code 1) if checkstyle actually ran
                if (exitCode != 0 && exitCode != 1) {
                    logger.warn("Gradle CheckStyle had issues: {}", output.toString());
                    return false;
                }
                
                return checkstyleExecuted || exitCode == 0;
            }
        } catch (Exception e) {
            logger.warn("Failed to run Gradle CheckStyle: {}", e.getMessage());
            return false;
        }
    }
    
    private void runDirectCheckStyle(Path projectPath) throws Exception {
        // Create reports directory
        Path reportsDir = projectPath.resolve("build/reports/checkstyle");
        reportsDir.toFile().mkdirs();
        
        // Find the appropriate config file
        Path configFile = findCheckstyleConfigFile(projectPath);
        
        // Run CheckStyle for main sources - XML only, then generate standard HTML
        runCheckStyleOnSources(projectPath, projectPath.resolve("src/main/java"), 
                              reportsDir.resolve("main.xml"), configFile, "xml");
        generateStandardHtmlReport(reportsDir.resolve("main.xml"), reportsDir.resolve("main.html"));
        
        // Run CheckStyle for test sources - XML only, then generate standard HTML
        runCheckStyleOnSources(projectPath, projectPath.resolve("src/test/java"),
                              reportsDir.resolve("test.xml"), configFile, "xml");
        generateStandardHtmlReport(reportsDir.resolve("test.xml"), reportsDir.resolve("test.html"));
    }
    
    private Path findCheckstyleConfigFile(Path projectPath) {
        // Always use QA module default config for consistency
        logger.info("Using QA module default CheckStyle config for consistent analysis");
        return null; // Will use embedded default
    }
    
    private void runCheckStyleOnSources(Path projectPath, Path sourcePath, Path outputFile, Path configFile, String format) throws Exception {
        if (!sourcePath.toFile().exists()) {
            logger.info("Source path does not exist: {}", sourcePath);
            return;
        }
        
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.puppycrawl.tools.checkstyle.Main");
        command.add("-c");
        
        if (configFile != null) {
            command.add(configFile.toString());
        } else {
            // Use embedded default config
            command.add(getClass().getClassLoader().getResource("default-configs/checkstyle.xml").toString());
        }
        
        command.add("-f");
        command.add(format);
        command.add("-o");
        command.add(outputFile.toString());
        command.add(sourcePath.toString());
        
        logger.info("Running direct CheckStyle ({}): {}", format, String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR") || line.contains("WARN")) {
                    logger.debug("CheckStyle: {}", line);
                }
            }
        }
        
        int exitCode = process.waitFor();
        logger.info("Direct CheckStyle completed with exit code: {} for {}", exitCode, sourcePath.getFileName());
    }
    
    
    
    private void generateStandardHtmlReport(Path xmlPath, Path htmlPath) throws Exception {
        if (!xmlPath.toFile().exists()) {
            logger.warn("XML report not found: {}", xmlPath);
            return;
        }
        
        try {
            String xmlContent = java.nio.file.Files.readString(xmlPath);
            String htmlContent = transformXmlToStandardHtml(xmlContent);
            java.nio.file.Files.writeString(htmlPath, htmlContent);
            logger.info("Generated standard HTML report: {}", htmlPath);
        } catch (Exception e) {
            logger.warn("Failed to generate HTML report: {}", e.getMessage());
            throw e;
        }
    }
    
    private String transformXmlToStandardHtml(String xmlContent) {
        StringBuilder html = new StringBuilder();
        
        // Parse violations from XML
        int totalViolations = xmlContent.split("<error").length - 1;
        Map<String, List<String>> fileViolations = parseViolationsFromXml(xmlContent);
        int filesWithViolations = fileViolations.size();
        int totalFiles = xmlContent.split("<file").length - 1;
        
        // Generate standard Checkstyle HTML format
        html.append("<html>\n");
        html.append("    <head>\n");
        html.append("        <META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
        html.append("        <title>Checkstyle Violations</title>\n");
        html.append("        <style type=\"text/css\">\n");
        html.append("                    body {\n");
        html.append("                        background-color: #fff;\n");
        html.append("                        color: #02303A;\n");
        html.append("                    }\n");
        html.append("                    a {\n");
        html.append("                        color: #1DA2BD;\n");
        html.append("                    }\n");
        html.append("                    a.link {\n");
        html.append("                        color: #02303A;\n");
        html.append("                    }\n");
        html.append("                    tr:nth-child(even) {\n");
        html.append("                        background: white;\n");
        html.append("                    }\n");
        html.append("                    th {\n");
        html.append("                        font-weight:bold;\n");
        html.append("                    }\n");
        html.append("                    tr {\n");
        html.append("                        background: #efefef;\n");
        html.append("                    }\n");
        html.append("                    table th, td, tr {\n");
        html.append("                        font-size:100%;\n");
        html.append("                        border: none;\n");
        html.append("                        text-align: left;\n");
        html.append("                        vertical-align: top;\n");
        html.append("                    }\n");
        html.append("                </style>\n");
        html.append("    </head>\n");
        html.append("    <body>\n");
        html.append("        <p>\n");
        html.append("            <a name=\"top\">\n");
        html.append("                <h1>Checkstyle Results</h1>\n");
        html.append("            </a>\n");
        html.append("        </p>\n");
        html.append("        <hr align=\"left\" width=\"95%\" size=\"1\">\n");
        html.append("        <h2>Summary</h2>\n");
        html.append("        <table class=\"summary\" width=\"95%\">\n");
        html.append("            <tr>\n");
        html.append("                <th>Total files checked</th><th>Total violations</th><th>Files with violations</th>\n");
        html.append("            </tr>\n");
        html.append("            <tr>\n");
        html.append("                <td>").append(totalFiles).append("</td><td>").append(totalViolations).append("</td><td>").append(filesWithViolations).append("</td>\n");
        html.append("            </tr>\n");
        html.append("        </table>\n");
        html.append("        <hr align=\"left\" width=\"95%\" size=\"1\">\n");
        
        if (!fileViolations.isEmpty()) {
            html.append("        <div class=\"violations\">\n");
            html.append("            <h2>Violations</h2>\n");
            html.append("            <p>\n");
            html.append("                <table class=\"filelist\" width=\"95%\">\n");
            html.append("                    <tr>\n");
            html.append("                        <th>File</th><th>Total violations</th>\n");
            html.append("                    </tr>\n");
            
            // File list
            for (Map.Entry<String, List<String>> entry : fileViolations.entrySet()) {
                String fileName = entry.getKey();
                int violationCount = entry.getValue().size();
                String anchor = "N" + Math.abs(fileName.hashCode());
                html.append("                    <tr>\n");
                html.append("                        <td><a href=\"#").append(anchor).append("\">").append(fileName).append("</a></td><td>").append(violationCount).append("</td>\n");
                html.append("                    </tr>\n");
            }
            
            html.append("                </table>\n");
            html.append("                <p></p>\n");
            
            // Detailed violations
            for (Map.Entry<String, List<String>> entry : fileViolations.entrySet()) {
                String fileName = entry.getKey();
                List<String> violations = entry.getValue();
                String anchor = "N" + Math.abs(fileName.hashCode());
                
                html.append("                <div class=\"file-violation\">\n");
                html.append("                    <h3>\n");
                html.append("                        <a class=\"link\" name=\"").append(anchor).append("\">").append(fileName).append("</a>\n");
                html.append("                    </h3>\n");
                html.append("                    <table class=\"violationlist\" width=\"95%\">\n");
                html.append("                        <tr>\n");
                html.append("                            <th>Severity</th><th>Description</th><th>Line Number</th>\n");
                html.append("                        </tr>\n");
                
                for (String violation : violations) {
                    html.append("                        ").append(violation).append("\n");
                }
                
                html.append("                    </table>\n");
                html.append("                    <p></p>\n");
                html.append("                    <a href=\"#top\">Back to top</a>\n");
                html.append("                    <p></p>\n");
                html.append("                </div>\n");
            }
            
            html.append("            </p>\n");
            html.append("        </div>\n");
        }
        
        html.append("        <hr align=\"left\" width=\"95%\" size=\"1\">\n");
        html.append("        <p>\n");
        html.append("            Generated by <a href=\"https://github.com/checkstyle/checkstyle\">Checkstyle</a> with QA Module.\n");
        html.append("        </p>\n");
        html.append("    </body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private Map<String, List<String>> parseViolationsFromXml(String xmlContent) {
        Map<String, List<String>> fileViolations = new LinkedHashMap<>();
        
        String[] fileSections = xmlContent.split("<file name=\"");
        for (int i = 1; i < fileSections.length; i++) {
            String section = fileSections[i];
            int nameEnd = section.indexOf("\">");
            if (nameEnd == -1) continue;
            
            String fileName = section.substring(0, nameEnd);
            String errors = section.substring(nameEnd + 2);
            
            List<String> violations = new ArrayList<>();
            String[] errorTags = errors.split("<error ");
            for (int j = 1; j < errorTags.length; j++) {
                String errorTag = errorTags[j];
                int tagEnd = errorTag.indexOf("/>");
                if (tagEnd == -1) continue;
                
                String attributes = errorTag.substring(0, tagEnd);
                String line = extractAttribute(attributes, "line");
                String severity = extractAttribute(attributes, "severity");
                String message = extractAttribute(attributes, "message");
                
                if (line.isEmpty()) line = "0";
                if (severity.isEmpty()) severity = "error";
                
                String violationRow = String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>", 
                    severity, escapeHtml(message), line);
                violations.add(violationRow);
            }
            
            if (!violations.isEmpty()) {
                fileViolations.put(fileName, violations);
            }
        }
        
        return fileViolations;
    }
    
    private String extractAttribute(String attributes, String attributeName) {
        String pattern = attributeName + "=\"";
        int start = attributes.indexOf(pattern);
        if (start == -1) return "";
        
        start += pattern.length();
        int end = attributes.indexOf("\"", start);
        if (end == -1) return "";
        
        return attributes.substring(start, end);
    }
    
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    private int countViolationsFromXml(Path xmlPath) {
        if (!xmlPath.toFile().exists()) {
            return 0;
        }
        
        try {
            String content = java.nio.file.Files.readString(xmlPath);
            // Simple count of <error> tags
            return content.split("<error").length - 1;
        } catch (Exception e) {
            logger.warn("Failed to count violations from XML: {}", e.getMessage());
            return 0;
        }
    }
    
    private AnalysisResult buildSimpleResult(int violationCount) {
        // Checkstyle only has 'error' severity level
        int errorCount = violationCount; // All violations are 'error' level
        
        // Check threshold and build failure reason
        boolean failed = errorCount > config.getCheckstyleErrorThreshold();
        String status = failed ? "fail" : "pass";
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("violationsFound", violationCount);
        metrics.put("errorCount", errorCount);
        
        // Build detailed summary
        String summary = String.format("Checkstyle found %d violations (Error:%d)", violationCount, errorCount);
        
        if (failed) {
            summary += String.format(" - FAILED: Error: %d > %d", errorCount, config.getCheckstyleErrorThreshold());
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
    
}
