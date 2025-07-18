package com.ldx.qa.report;

import com.ldx.qa.model.QualityReport;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * HTML report generator using FreeMarker
 */
public class HtmlReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportGenerator.class);
    
    private final Configuration cfg;
    
    public HtmlReportGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
    }
    
    public void generate(QualityReport report, Path outputPath) throws IOException {
        try {
            // For now, generate a simple HTML without template
            String html = generateSimpleHtml(report);
            
            try (FileWriter writer = new FileWriter(outputPath.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(html);
            }
            
            logger.info("HTML report generated at: {}", outputPath);
        } catch (Exception e) {
            throw new IOException("Failed to generate HTML report", e);
        }
    }
    
    private String generateSimpleHtml(QualityReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>Quality Analysis Report</title>\n");
        html.append("  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\">\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container mt-5\">\n");
        html.append("    <h1>Quality Analysis Report</h1>\n");
        html.append("    <p class=\"text-muted\">Generated: ").append(report.getTimestamp()).append("</p>\n");
        html.append("    <p>Project: ").append(report.getProjectPath()).append("</p>\n");
        html.append("    <div class=\"alert ").append(getStatusClass(report.getOverallStatus())).append("\">\n");
        html.append("      Overall Status: <strong>").append(report.getOverallStatus().toUpperCase()).append("</strong>\n");
        html.append("    </div>\n");
        
        // Analysis Results
        html.append("    <h2 class=\"mt-4\">Analysis Results</h2>\n");
        for (AnalysisResult result : report.getResults()) {
            html.append("    <div class=\"card mb-3\">\n");
            html.append("      <div class=\"card-header\">\n");
            html.append("        <h5>").append(result.getType().toUpperCase()).append("</h5>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"card-body\">\n");
            html.append("        <p>Status: <span class=\"badge ").append(getBadgeClass(result.getStatus())).append("\">")
                .append(result.getStatus()).append("</span></p>\n");
            
            // Convert line breaks to HTML for better display
            String formattedSummary = result.getSummary().replace("\n", "<br>");
            html.append("        <div>").append(formattedSummary).append("</div>\n");
            
            if (result.getViolations() != null && !result.getViolations().isEmpty()) {
                // 에러와 경고 분류
                boolean hasErrors = result.getViolations().stream()
                    .anyMatch(v -> "error".equalsIgnoreCase(v.getSeverity()));
                boolean hasWarnings = result.getViolations().stream()
                    .anyMatch(v -> "warning".equalsIgnoreCase(v.getSeverity()));
                
                // 에러들은 인라인으로 표시
                if (hasErrors) {
                    html.append("        <h6>Errors:</h6>\n");
                    html.append("        <ul>\n");
                    for (Violation v : result.getViolations()) {
                        if ("error".equalsIgnoreCase(v.getSeverity())) {
                            html.append("          <li><strong>").append(v.getSeverity()).append("</strong>: ");
                            html.append(v.getMessage()).append(" (").append(v.getFile()).append(":").append(v.getLine()).append(")</li>\n");
                        }
                    }
                    html.append("        </ul>\n");
                }
                
                // 경고들은 HTML 리포트 버튼으로 통합됨 (아래에서 처리)
            }
            
            // Add HTML report link for all static analysis tools
            if (hasDetailedReport(result.getType())) {
                html.append("        <p class=\"mt-2\">");
                html.append("          <a href=\"").append(getOriginalReportPath(result.getType())).append("\" target=\"_blank\" class=\"btn btn-outline-primary btn-sm\">");
                html.append("            📄 View ").append(result.getType().toUpperCase()).append(" HTML Report");
                html.append("          </a>");
                html.append("        </p>\n");
            }
            
            html.append("      </div>\n");
            html.append("    </div>\n");
        }        
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String getStatusClass(String status) {
        switch (status.toLowerCase()) {
            case "pass":
                return "alert-success";
            case "fail":
                return "alert-danger";
            case "warning":
                return "alert-warning";
            default:
                return "alert-secondary";
        }
    }
    
    private String getBadgeClass(String status) {
        switch (status.toLowerCase()) {
            case "pass":
                return "bg-success";
            case "fail":
                return "bg-danger";
            case "skipped":
                return "bg-secondary";
            case "error":
                return "bg-danger";
            default:
                return "bg-primary";
        }
    }
    
    private boolean hasDetailedReport(String type) {
        // All static analysis tools have detailed HTML reports
        return "checkstyle".equalsIgnoreCase(type) || 
               "pmd".equalsIgnoreCase(type) || 
               "spotbugs".equalsIgnoreCase(type) || 
               "jacoco".equalsIgnoreCase(type) ||
               "archunit".equalsIgnoreCase(type);
    }
    
    private String getOriginalReportPath(String type) {
        // Get the absolute path to the reports directory
        String reportsDir = System.getProperty("user.dir") + "/build/reports";
        
        switch (type.toLowerCase()) {
            case "checkstyle":
                return "file://" + reportsDir + "/checkstyle/main.html";
            case "pmd":
                return "file://" + reportsDir + "/pmd/main.html";
            case "spotbugs":
                return "file://" + reportsDir + "/spotbugs/main.html";
            case "jacoco":
                return "file://" + reportsDir + "/jacoco/test/html/index.html";
            case "archunit":
                return "file://" + reportsDir + "/tests/archunitTest/index.html";
            default:
                return "#";
        }
    }
}
