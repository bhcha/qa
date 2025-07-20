import com.ldx.qa.model.AnalysisResult;
import com.ldx.qa.model.QualityReport;
import com.ldx.qa.report.HtmlReportGenerator;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HtmlReportTest {
    public static void main(String[] args) throws Exception {
        // Create test analysis results
        AnalysisResult staticResult = AnalysisResult.builder()
            .type("checkstyle")
            .status("pass")
            .summary("Checkstyle found 10 violations")
            .timestamp(LocalDateTime.now())
            .build();
            
        AnalysisResult aiResult = AnalysisResult.builder()
            .type("sequential-gemini")
            .status("pass")
            .summary("## AI Analysis Results\n\n### Security Guide\n✅ Analysis completed successfully")
            .timestamp(LocalDateTime.now())
            .build();
            
        // Add metrics to AI result
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalGuides", 2);
        metrics.put("successfulGuides", 2);
        metrics.put("failedGuides", 0);
        metrics.put("totalExecutionTimeSeconds", 45.3);
        aiResult.setMetrics(metrics);
        
        // Create quality report
        QualityReport report = QualityReport.builder()
            .timestamp(LocalDateTime.now())
            .projectPath("/test/project")
            .overallStatus("pass")
            .results(Arrays.asList(staticResult, aiResult))
            .build();
            
        // Generate HTML report
        HtmlReportGenerator generator = new HtmlReportGenerator();
        generator.generate(report, Paths.get("/tmp/test-quality-report.html"));
        
        System.out.println("HTML report generated at: /tmp/test-quality-report.html");
        System.out.println("Open the file to verify tab functionality");
    }
}