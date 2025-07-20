package com.ldx.qa.report;

import com.ldx.qa.model.QualityReport;
import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.Violation;
// import com.ldx.qa.util.MarkdownToHtmlConverter; // 더 이상 사용하지 않음
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
import java.util.List;
import java.util.stream.Collectors;

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
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/marked@5.1.1/marked.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    .markdown-content { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; }\n");
        html.append("    .markdown-content h1, .markdown-content h2 { border-bottom: 1px solid #e1e4e8; padding-bottom: 10px; margin-top: 32px; margin-bottom: 16px; }\n");
        html.append("    .markdown-content h3, .markdown-content h4, .markdown-content h5, .markdown-content h6 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; }\n");
        html.append("    .markdown-content ul, .markdown-content ol { padding-left: 2em; margin-bottom: 16px; }\n");
        html.append("    .markdown-content li { margin-bottom: 0.5em; }\n");
        html.append("    .markdown-content p { margin-bottom: 16px; }\n");
        html.append("    .markdown-content code { background-color: #f6f8fa; padding: 2px 4px; border-radius: 3px; font-size: 85%; color: #d73a49; }\n");
        html.append("    .markdown-content pre { background-color: #f6f8fa; padding: 16px; border-radius: 6px; overflow: auto; margin-bottom: 16px; }\n");
        html.append("    .markdown-content blockquote { border-left: 4px solid #dfe2e5; padding-left: 16px; color: #6a737d; margin: 16px 0; }\n");
        html.append("    .markdown-content hr { border: none; height: 1px; background-color: #e1e4e8; margin: 24px 0; }\n");
        html.append("    .markdown-content strong { font-weight: 600; color: #24292e; }\n");
        html.append("    .markdown-content em { font-style: italic; }\n");
        html.append("    .emoji-colored { font-style: normal; }\n");
        html.append("    .markdown-content .emoji-colored { margin-right: 4px; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container mt-5\">\n");
        html.append("    <h1>Quality Analysis Report</h1>\n");
        html.append("    <p class=\"text-muted\">Generated: ").append(report.getTimestamp()).append("</p>\n");
        html.append("    <p>Project: ").append(report.getProjectPath()).append("</p>\n");
        html.append("    <div class=\"alert ").append(getStatusClass(report.getOverallStatus())).append("\">\n");
        html.append("      Overall Status: <strong>").append(report.getOverallStatus().toUpperCase()).append("</strong>\n");
        html.append("    </div>\n");
        
        // Categorize results
        List<AnalysisResult> staticResults = report.getResults().stream()
            .filter(r -> isStaticAnalysis(r.getType()))
            .collect(Collectors.toList());
        
        List<AnalysisResult> aiResults = report.getResults().stream()
            .filter(r -> isAiAnalysis(r.getType()))
            .collect(Collectors.toList());
        
        // Analysis Results with tabs
        html.append("    <h2 class=\"mt-4\">Analysis Results</h2>\n");
        
        // Only show tabs if there are results
        if (!staticResults.isEmpty() || !aiResults.isEmpty()) {
            html.append(generateTabsHtml(staticResults, aiResults));
        } else {
            html.append("    <div class=\"alert alert-info\">No analysis results available.</div>\n");
        }
        
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    document.addEventListener('DOMContentLoaded', function() {\n");
        html.append("      // Configure marked options\n");
        html.append("      marked.setOptions({\n");
        html.append("        breaks: true,\n");
        html.append("        gfm: true\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      // Find all markdown content elements and render them\n");
        html.append("      const markdownElements = document.querySelectorAll('.markdown-content');\n");
        html.append("      markdownElements.forEach(function(element) {\n");
        html.append("        let markdownText = element.textContent || element.innerText;\n");
        html.append("        \n");
        html.append("        // Clean up ugly markdown artifacts\n");
        html.append("        markdownText = markdownText\n");
        html.append("          // Remove long separator lines\n");
        html.append("          .replace(/={50,}/g, '')\n");
        html.append("          .replace(/-{50,}/g, '')\n");
        html.append("          // Remove HTML anchor artifacts like '📋-지침별-상세-분석-피드백\">' \n");
        html.append("          .replace(/[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]-[\\w\\uAC00-\\uD7AF]+-[\\w\\uAC00-\\uD7AF]+-[\\w\\uAC00-\\uD7AF]+[\"'>]+\\s*/g, '')\n");
        html.append("          // Clean up multiple consecutive newlines\n");
        html.append("          .replace(/\\n{3,}/g, '\\n\\n')\n");
        html.append("          // Remove standalone separator patterns\n");
        html.append("          .replace(/^\\s*[-=]{10,}\\s*$/gm, '')\n");
        html.append("          // Clean up leading/trailing whitespace\n");
        html.append("          .trim();\n");
        html.append("        \n");
        html.append("        let htmlContent = marked.parse(markdownText);\n");
        html.append("        \n");
        html.append("        // Apply Bootstrap color classes to emojis\n");
        html.append("        htmlContent = htmlContent\n");
        html.append("          .replace(/🤖/g, '<span class=\"text-primary emoji-colored\">🤖</span>')\n");
        html.append("          .replace(/🔒/g, '<span class=\"text-warning emoji-colored\">🔒</span>')\n");
        html.append("          .replace(/🧪/g, '<span class=\"text-info emoji-colored\">🧪</span>')\n");
        html.append("          .replace(/🎯/g, '<span class=\"text-success emoji-colored\">🎯</span>')\n");
        html.append("          .replace(/📊/g, '<span class=\"text-secondary emoji-colored\">📊</span>')\n");
        html.append("          .replace(/✅/g, '<span class=\"text-success emoji-colored\">✅</span>')\n");
        html.append("          .replace(/❌/g, '<span class=\"text-danger emoji-colored\">❌</span>')\n");
        html.append("          .replace(/⚠️/g, '<span class=\"text-warning emoji-colored\">⚠️</span>')\n");
        html.append("          .replace(/📁/g, '<span class=\"text-info emoji-colored\">📁</span>')\n");
        html.append("          .replace(/📂/g, '<span class=\"text-info emoji-colored\">📂</span>')\n");
        html.append("          .replace(/⏱️/g, '<span class=\"text-muted emoji-colored\">⏱️</span>')\n");
        html.append("          .replace(/🔍/g, '<span class=\"text-primary emoji-colored\">🔍</span>')\n");
        html.append("          .replace(/📋/g, '<span class=\"text-secondary emoji-colored\">📋</span>')\n");
        html.append("          .replace(/📈/g, '<span class=\"text-success emoji-colored\">📈</span>');\n");
        html.append("        \n");
        html.append("        element.innerHTML = htmlContent;\n");
        html.append("      });\n");
        html.append("    });\n");
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private boolean isStaticAnalysis(String type) {
        return "checkstyle".equalsIgnoreCase(type) ||
               "pmd".equalsIgnoreCase(type) ||
               "spotbugs".equalsIgnoreCase(type) ||
               "jacoco".equalsIgnoreCase(type) ||
               "archunit".equalsIgnoreCase(type) ||
               "kingfisher".equalsIgnoreCase(type);
    }
    
    private boolean isAiAnalysis(String type) {
        return "sequential-gemini".equalsIgnoreCase(type) ||
               "gemini".equalsIgnoreCase(type) ||
               type.toLowerCase().contains("ai") ||
               type.toLowerCase().contains("gemini");
    }
    
    private String generateTabsHtml(List<AnalysisResult> staticResults, List<AnalysisResult> aiResults) {
        StringBuilder html = new StringBuilder();
        
        // Generate tab navigation
        html.append("    <ul class=\"nav nav-tabs\" id=\"analysisTab\" role=\"tablist\">\n");
        
        boolean hasStatic = !staticResults.isEmpty();
        boolean hasAi = !aiResults.isEmpty();
        boolean staticFirst = hasStatic; // Show static tab first if available
        
        if (hasStatic) {
            html.append("      <li class=\"nav-item\" role=\"presentation\">\n");
            html.append("        <button class=\"nav-link").append(staticFirst ? " active" : "").append("\" id=\"static-tab\" data-bs-toggle=\"tab\" data-bs-target=\"#static\" type=\"button\" role=\"tab\">\n");
            html.append("          🔧 정적 분석 (").append(staticResults.size()).append(")\n");
            html.append("        </button>\n");
            html.append("      </li>\n");
        }
        
        if (hasAi) {
            html.append("      <li class=\"nav-item\" role=\"presentation\">\n");
            html.append("        <button class=\"nav-link").append(!staticFirst ? " active" : "").append("\" id=\"ai-tab\" data-bs-toggle=\"tab\" data-bs-target=\"#ai\" type=\"button\" role=\"tab\">\n");
            html.append("          🤖 AI 분석 (").append(aiResults.size()).append(")\n");
            html.append("        </button>\n");
            html.append("      </li>\n");
        }
        
        html.append("    </ul>\n");
        
        // Generate tab content
        html.append("    <div class=\"tab-content\" id=\"analysisTabContent\">\n");
        
        if (hasStatic) {
            html.append("      <div class=\"tab-pane fade").append(staticFirst ? " show active" : "").append("\" id=\"static\" role=\"tabpanel\">\n");
            html.append("        <div class=\"mt-3\">\n");
            for (AnalysisResult result : staticResults) {
                html.append(generateStaticAnalysisSection(result));
            }
            html.append("        </div>\n");
            html.append("      </div>\n");
        }
        
        if (hasAi) {
            html.append("      <div class=\"tab-pane fade").append(!staticFirst ? " show active" : "").append("\" id=\"ai\" role=\"tabpanel\">\n");
            html.append("        <div class=\"mt-3\">\n");
            for (AnalysisResult result : aiResults) {
                if ("sequential-gemini".equals(result.getType())) {
                    html.append(generateSequentialGeminiSection(result));
                } else {
                    html.append(generateAiAnalysisSection(result));
                }
            }
            html.append("        </div>\n");
            html.append("      </div>\n");
        }
        
        html.append("    </div>\n");
        
        return html.toString();
    }
    
    private String generateStaticAnalysisSection(AnalysisResult result) {
        StringBuilder html = new StringBuilder();
        
        html.append("        <div class=\"card mb-3\">\n");
        html.append("          <div class=\"card-header\">\n");
        html.append("            <h5>🔧 ").append(result.getType().toUpperCase()).append("</h5>\n");
        html.append("          </div>\n");
        html.append("          <div class=\"card-body\">\n");
        html.append("            <p>Status: <span class=\"badge ").append(getBadgeClass(result.getStatus())).append("\">").
            append(result.getStatus()).append("</span></p>\n");
        
        // Convert line breaks to HTML for better display
        String formattedSummary = result.getSummary().replace("\n", "<br>");
        html.append("            <div>").append(formattedSummary).append("</div>\n");
        
        if (result.getViolations() != null && !result.getViolations().isEmpty()) {
            // 에러와 경고 분류
            boolean hasErrors = result.getViolations().stream()
                .anyMatch(v -> "error".equalsIgnoreCase(v.getSeverity()));
            
            // 에러들은 인라인으로 표시
            if (hasErrors) {
                html.append("            <h6>Errors:</h6>\n");
                html.append("            <ul>\n");
                for (Violation v : result.getViolations()) {
                    if ("error".equalsIgnoreCase(v.getSeverity())) {
                        html.append("              <li><strong>").append(v.getSeverity()).append("</strong>: ");
                        html.append(v.getMessage()).append(" (").append(v.getFile()).append(":").append(v.getLine()).append(")</li>\n");
                    }
                }
                html.append("            </ul>\n");
            }
        }
        
        // Add HTML report link for static analysis tools
        if (hasDetailedReport(result.getType())) {
            html.append("            <p class=\"mt-2\">");
            html.append("              <a href=\"").append(getOriginalReportPath(result.getType())).append("\" target=\"_blank\" class=\"btn btn-outline-primary btn-sm\">");
            html.append("                📄 View ").append(result.getType().toUpperCase()).append(" HTML Report");
            html.append("              </a>");
            html.append("            </p>\n");
        }
        
        html.append("          </div>\n");
        html.append("        </div>\n");
        
        return html.toString();
    }
    
    private String generateAiAnalysisSection(AnalysisResult result) {
        StringBuilder html = new StringBuilder();
        
        html.append("        <div class=\"card mb-3\">\n");
        html.append("          <div class=\"card-header bg-info text-white\">\n");
        html.append("            <h5>🤖 ").append(result.getType().toUpperCase()).append("</h5>\n");
        html.append("          </div>\n");
        html.append("          <div class=\"card-body\">\n");
        html.append("            <p>Status: <span class=\"badge ").append(getBadgeClass(result.getStatus())).append("\">").
            append(result.getStatus()).append("</span></p>\n");
        
        // Convert line breaks to HTML for better display
        String formattedSummary = result.getSummary().replace("\n", "<br>");
        html.append("            <div>").append(formattedSummary).append("</div>\n");
        
        html.append("          </div>\n");
        html.append("        </div>\n");
        
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
        html.append("        <div class=\"card mb-3\">\n");
        html.append("          <div class=\"card-header bg-primary text-white\">\n");
        html.append("            <h5>🤖 ").append(result.getType().toUpperCase()).append(" - 지침별 순차 AI 분석</h5>\n");
        html.append("          </div>\n");
        html.append("          <div class=\"card-body\">\n");
        html.append("            <p>Status: <span class=\"badge ").append(getBadgeClass(result.getStatus())).append("\">").
            append(result.getStatus()).append("</span></p>\n");
        
        // 통계 정보
        if (result.getMetrics() != null) {
            html.append("            <div class=\"row mb-3\">\n");
            html.append("              <div class=\"col-md-3\">\n");
            html.append("                <div class=\"text-center\">\n");
            html.append("                  <h6 class=\"text-muted\">총 지침</h6>\n");
            html.append("                  <h4 class=\"text-primary\">").append(result.getMetrics().get("totalGuides")).append("</h4>\n");
            html.append("                </div>\n");
            html.append("              </div>\n");
            html.append("              <div class=\"col-md-3\">\n");
            html.append("                <div class=\"text-center\">\n");
            html.append("                  <h6 class=\"text-muted\">성공</h6>\n");
            html.append("                  <h4 class=\"text-success\">").append(result.getMetrics().get("successfulGuides")).append("</h4>\n");
            html.append("                </div>\n");
            html.append("              </div>\n");
            html.append("              <div class=\"col-md-3\">\n");
            html.append("                <div class=\"text-center\">\n");
            html.append("                  <h6 class=\"text-muted\">실패</h6>\n");
            html.append("                  <h4 class=\"text-danger\">").append(result.getMetrics().get("failedGuides")).append("</h4>\n");
            html.append("                </div>\n");
            html.append("              </div>\n");
            html.append("              <div class=\"col-md-3\">\n");
            html.append("                <div class=\"text-center\">\n");
            html.append("                  <h6 class=\"text-muted\">총 시간</h6>\n");
            html.append("                  <h4 class=\"text-info\">").append(String.format("%.1f", result.getMetrics().get("totalExecutionTimeSeconds"))).append("초</h4>\n");
            html.append("                </div>\n");
            html.append("              </div>\n");
            html.append("            </div>\n");
        }
        
        // 분석 결과 요약 (접을 수 있는 아코디언 형태)
        html.append("            <div class=\"accordion\" id=\"geminiAccordion\">\n");
        html.append("              <div class=\"accordion-item\">\n");
        html.append("                <h2 class=\"accordion-header\">\n");
        html.append("                  <button class=\"accordion-button\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#geminiDetails\">\n");
        html.append("                    📋 지침별 상세 분석 결과 보기\n");
        html.append("                  </button>\n");
        html.append("                </h2>\n");
        html.append("                <div id=\"geminiDetails\" class=\"accordion-collapse collapse show\">\n");
        html.append("                  <div class=\"accordion-body\">\n");
        
        // Raw markdown content를 클라이언트 사이드에서 렌더링하도록 설정
        String markdownContent = result.getSummary()
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        
        html.append("                    <div class=\"mt-3 markdown-content\">").append(markdownContent).append("</div>\n");
        html.append("                  </div>\n");
        html.append("                </div>\n");
        html.append("              </div>\n");
        html.append("            </div>\n");
        
        html.append("          </div>\n");
        html.append("        </div>\n");
        
        return html.toString();
    }
}