package com.ldx.qa.report;

import com.ldx.qa.model.QualityReport;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
import com.ldx.qa.util.MarkdownToHtmlConverter;
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
            // Sequential Gemini 분석 결과는 특별한 처리
            if ("sequential-gemini".equals(result.getType())) {
                html.append(generateSequentialGeminiSection(result));
            } else {
                // 기존 분석 결과 처리
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
               "kingfisher".equalsIgnoreCase(type);
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
                return "file://" + reportsDir + "/jacoco/test/index.html";
            case "kingfisher":
                return "file://" + reportsDir + "/kingfisher/main.html";
            default:
                return "#";
        }
    }
    
    /**
     * Sequential Gemini AI 분석 결과를 위한 특별한 HTML 섹션 생성
     */
    private String generateSequentialGeminiSection(AnalysisResult result) {
        StringBuilder html = new StringBuilder();
        
        // 메인 카드
        html.append("    <div class=\"card mb-3\">\n");
        html.append("      <div class=\"card-header bg-primary text-white\">\n");
        html.append("        <h5><i class=\"bi bi-robot\"></i> ").append(result.getType().toUpperCase()).append(" - 지침별 순차 AI 분석</h5>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card-body\">\n");
        html.append("        <p>Status: <span class=\"badge ").append(getBadgeClass(result.getStatus())).append("\">")
            .append(result.getStatus()).append("</span></p>\n");
        
        // 통계 정보
        if (result.getMetrics() != null) {
            html.append("        <div class=\"row mb-3\">\n");
            html.append("          <div class=\"col-md-3\">\n");
            html.append("            <div class=\"text-center\">\n");
            html.append("              <h6 class=\"text-muted\">총 지침</h6>\n");
            html.append("              <h4 class=\"text-primary\">").append(result.getMetrics().get("totalGuides")).append("</h4>\n");
            html.append("            </div>\n");
            html.append("          </div>\n");
            html.append("          <div class=\"col-md-3\">\n");
            html.append("            <div class=\"text-center\">\n");
            html.append("              <h6 class=\"text-muted\">성공</h6>\n");
            html.append("              <h4 class=\"text-success\">").append(result.getMetrics().get("successfulGuides")).append("</h4>\n");
            html.append("            </div>\n");
            html.append("          </div>\n");
            html.append("          <div class=\"col-md-3\">\n");
            html.append("            <div class=\"text-center\">\n");
            html.append("              <h6 class=\"text-muted\">실패</h6>\n");
            html.append("              <h4 class=\"text-danger\">").append(result.getMetrics().get("failedGuides")).append("</h4>\n");
            html.append("            </div>\n");
            html.append("          </div>\n");
            html.append("          <div class=\"col-md-3\">\n");
            html.append("            <div class=\"text-center\">\n");
            html.append("              <h6 class=\"text-muted\">총 시간</h6>\n");
            html.append("              <h4 class=\"text-info\">").append(String.format("%.1f", result.getMetrics().get("totalExecutionTimeSeconds"))).append("초</h4>\n");
            html.append("            </div>\n");
            html.append("          </div>\n");
            html.append("        </div>\n");
        }
        
        // 분석 결과 요약 (접을 수 있는 아코디언 형태)
        html.append("        <div class=\"accordion\" id=\"geminiAccordion\">\n");
        html.append("          <div class=\"accordion-item\">\n");
        html.append("            <h2 class=\"accordion-header\">\n");
        html.append("              <button class=\"accordion-button\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#geminiDetails\">\n");
        html.append("                📋 지침별 상세 분석 결과 보기\n");
        html.append("              </button>\n");
        html.append("            </h2>\n");
        html.append("            <div id=\"geminiDetails\" class=\"accordion-collapse collapse show\">\n");
        html.append("              <div class=\"accordion-body\">\n");
        
        // Markdown을 HTML로 변환하여 표시
        String formattedSummary = MarkdownToHtmlConverter.convertToHtml(result.getSummary());
        
        // 아이콘에 Bootstrap 색상 클래스 적용
        formattedSummary = formattedSummary
            .replace("🤖", "<span class=\"text-primary\">🤖</span>")
            .replace("🔒", "<span class=\"text-warning\">🔒</span>")
            .replace("🧪", "<span class=\"text-info\">🧪</span>")
            .replace("🎯", "<span class=\"text-success\">🎯</span>")
            .replace("📊", "<span class=\"text-secondary\">📊</span>")
            .replace("✅", "<span class=\"text-success\">✅</span>")
            .replace("❌", "<span class=\"text-danger\">❌</span>")
            .replace("⚠️", "<span class=\"text-warning\">⚠️</span>")
            .replace("📁", "<span class=\"text-info\">📁</span>")
            .replace("📂", "<span class=\"text-info\">📂</span>")
            .replace("⏱️", "<span class=\"text-muted\">⏱️</span>")
            .replace("🔍", "<span class=\"text-primary\">🔍</span>")
            .replace("📋", "<span class=\"text-secondary\">📋</span>")
            .replace("📈", "<span class=\"text-success\">📈</span>")
            .replace("📊", "<span class=\"text-info\">📊</span>");
        
        html.append("                <div class=\"mt-3\">").append(formattedSummary).append("</div>\n");
        html.append("              </div>\n");
        html.append("            </div>\n");
        html.append("          </div>\n");
        html.append("        </div>\n");
        
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        return html.toString();
    }
}
