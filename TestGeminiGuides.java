import com.identitybridge.qa.analyzer.GeminiAnalyzer;
import com.identitybridge.qa.config.QaConfiguration;
import java.nio.file.Paths;
import java.lang.reflect.Method;

public class TestGeminiGuides {
    public static void main(String[] args) {
        try {
            // Create test configuration
            QaConfiguration config = QaConfiguration.defaultConfig();
            
            // Create analyzer
            GeminiAnalyzer analyzer = new GeminiAnalyzer(config);
            
            // Test loadGuideFile method using reflection
            Method loadGuideMethod = GeminiAnalyzer.class.getDeclaredMethod("loadGuideFile", java.nio.file.Path.class);
            loadGuideMethod.setAccessible(true);
            
            String guideContent = (String) loadGuideMethod.invoke(analyzer, Paths.get("../"));
            
            System.out.println("=== 로드된 가이드 내용 ===");
            System.out.println("길이: " + guideContent.length() + " 문자");
            System.out.println("첫 500자:\n" + guideContent.substring(0, Math.min(500, guideContent.length())));
            System.out.println("\n...");
            
            // Test buildGeminiCommand method
            Method buildCommandMethod = GeminiAnalyzer.class.getDeclaredMethod("buildGeminiCommand", 
                java.nio.file.Path.class, String.class);
            buildCommandMethod.setAccessible(true);
            
            java.util.List<String> command = (java.util.List<String>) buildCommandMethod.invoke(
                analyzer, Paths.get("../"), guideContent);
            
            System.out.println("\n=== 생성된 Gemini 명령어 ===");
            System.out.println("명령어 부분 수: " + command.size());
            System.out.println("첫 번째 부분: " + command.get(0));
            System.out.println("두 번째 부분 (프롬프트) 길이: " + command.get(1).length() + " 문자");
            System.out.println("프롬프트 첫 300자:\n" + command.get(1).substring(0, Math.min(300, command.get(1).length())));
            
        } catch (Exception e) {
            System.err.println("에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}