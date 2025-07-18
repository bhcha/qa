import com.identitybridge.qa.analyzer.GeminiAnalyzer;
import com.identitybridge.qa.config.QaConfiguration;
import com.identitybridge.qa.model.AnalysisResult;
import java.nio.file.Paths;

public class TestGemini {
    public static void main(String[] args) {
        try {
            // Create test configuration
            QaConfiguration config = QaConfiguration.defaultConfig();
            
            // Create analyzer
            GeminiAnalyzer analyzer = new GeminiAnalyzer(config);
            
            // Check if available
            System.out.println("Gemini available: " + analyzer.isAvailable());
            
            // Run analysis
            AnalysisResult result = analyzer.analyze(Paths.get("../"));
            
            // Print results
            System.out.println("Analysis Status: " + result.getStatus());
            System.out.println("Summary: " + result.getSummary());
            System.out.println("Violations: " + result.getViolations().size());
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}