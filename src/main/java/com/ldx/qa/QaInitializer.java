package com.ldx.qa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * QA 모듈 초기화 기능
 * 프로젝트 루트에 기본 설정 파일들을 복사하여 QA 환경을 구성합니다.
 */
public class QaInitializer {
    private static final Logger logger = LoggerFactory.getLogger(QaInitializer.class);
    
    private static final Map<String, String> CONFIG_FILES = new HashMap<>();
    
    static {
        // 정적 분석 설정 파일들
        CONFIG_FILES.put("config/static/checkstyle/checkstyle-custom.xml", "default-configs/checkstyle.xml");
        CONFIG_FILES.put("config/static/pmd/pmd-custom-rules.xml", "default-configs/pmd-ruleset.xml");
        CONFIG_FILES.put("config/static/spotbugs/spotbugs-exclude.xml", "default-configs/spotbugs-exclude.xml");
        CONFIG_FILES.put("config/archunit/archunit.properties", "default-configs/archunit.properties");
        
        // AI 분석 설정 파일들
        CONFIG_FILES.put("config/ai/gemini-guide.md", "default-configs/gemini-guide.md");
        
        // QA 설정 파일
        CONFIG_FILES.put("config/qa.properties", "default-configs/qa.properties");
        
        // 문서 파일들
        CONFIG_FILES.put("docs/qa-guide.md", "default-configs/qa-guide.md");
        CONFIG_FILES.put("docs/quality-standards.md", "default-configs/quality-standards.md");
    }
    
    /**
     * 프로젝트 루트에 QA 설정 파일들을 초기화합니다.
     */
    public static void initialize(Path projectRoot) {
        initialize(projectRoot, false);
    }
    
    /**
     * 프로젝트 루트에 QA 설정 파일들을 초기화합니다.
     * 
     * @param projectRoot 프로젝트 루트 경로
     * @param overwrite 기존 파일 덮어쓰기 여부
     */
    public static void initialize(Path projectRoot, boolean overwrite) {
        logger.info("Initializing QA configuration in project: {}", projectRoot);
        
        int copiedFiles = 0;
        int skippedFiles = 0;
        
        for (Map.Entry<String, String> entry : CONFIG_FILES.entrySet()) {
            String targetPath = entry.getKey();
            String resourcePath = entry.getValue();
            
            try {
                if (copyConfigFile(projectRoot, targetPath, resourcePath, overwrite)) {
                    copiedFiles++;
                } else {
                    skippedFiles++;
                }
            } catch (IOException e) {
                logger.error("Failed to copy config file {}: {}", targetPath, e.getMessage());
            }
        }
        
        logger.info("QA initialization completed. Copied: {}, Skipped: {}", copiedFiles, skippedFiles);
        printInitializationSummary(projectRoot, copiedFiles, skippedFiles);
    }
    
    /**
     * 단일 설정 파일을 복사합니다.
     */
    private static boolean copyConfigFile(Path projectRoot, String targetPath, String resourcePath, boolean overwrite) throws IOException {
        Path targetFile = projectRoot.resolve(targetPath);
        
        // 이미 존재하는 파일 처리
        if (Files.exists(targetFile) && !overwrite) {
            logger.debug("Skipping existing file: {}", targetPath);
            return false;
        }
        
        // 디렉토리 생성
        Files.createDirectories(targetFile.getParent());
        
        // 리소스에서 파일 복사 (UTF-8 인코딩 명시)
        try (InputStream inputStream = QaInitializer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                logger.warn("Resource not found: {}", resourcePath);
                return false;
            }
            
            // 텍스트 파일의 경우 UTF-8 인코딩으로 처리
            if (isTextFile(resourcePath)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                     BufferedWriter writer = Files.newBufferedWriter(targetFile, java.nio.charset.StandardCharsets.UTF_8)) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } else {
                // 바이너리 파일의 경우 일반 복사
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            logger.info("Copied: {}", targetPath);
            return true;
        }
    }
    
    private static boolean isTextFile(String resourcePath) {
        return resourcePath.endsWith(".properties") || 
               resourcePath.endsWith(".xml") || 
               resourcePath.endsWith(".md") || 
               resourcePath.endsWith(".txt") || 
               resourcePath.endsWith(".yml") || 
               resourcePath.endsWith(".yaml");
    }
    
    /**
     * 초기화 결과를 출력합니다.
     */
    private static void printInitializationSummary(Path projectRoot, int copiedFiles, int skippedFiles) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("QA Module Initialization Summary");
        System.out.println("=".repeat(60));
        System.out.println("Project: " + projectRoot.toAbsolutePath());
        System.out.println("Files copied: " + copiedFiles);
        System.out.println("Files skipped: " + skippedFiles);
        System.out.println("\nConfiguration structure created:");
        System.out.println("├── config/");
        System.out.println("│   ├── static/");
        System.out.println("│   │   ├── checkstyle/checkstyle-custom.xml");
        System.out.println("│   │   ├── pmd/pmd-custom-rules.xml");
        System.out.println("│   │   └── spotbugs/spotbugs-exclude.xml");
        System.out.println("│   ├── archunit/");
        System.out.println("│   │   └── archunit.properties");
        System.out.println("│   └── ai/");
        System.out.println("│       └── gemini-guide.md");
        System.out.println("│   └── qa.properties");
        System.out.println("└── docs/");
        System.out.println("    ├── qa-guide.md");
        System.out.println("    └── quality-standards.md");
        System.out.println("\nNext steps:");
        System.out.println("1. Review and customize configuration files");
        System.out.println("2. Run: ./gradlew qualityCheck");
        System.out.println("3. Check reports in: build/reports/quality/");
        System.out.println("=".repeat(60));
    }
    
    /**
     * 명령줄 실행을 위한 메인 메서드
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: QaInitializer <project-root> [--overwrite]");
            System.exit(1);
        }
        
        Path projectRoot = Paths.get(args[0]);
        boolean overwrite = args.length > 1 && "--overwrite".equals(args[1]);
        
        try {
            initialize(projectRoot, overwrite);
        } catch (Exception e) {
            System.err.println("Initialization failed: " + e.getMessage());
            System.exit(1);
        }
    }
}