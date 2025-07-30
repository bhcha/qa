package com.ldx.qa.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gemini AI 프롬프트 빌더 클래스
 * 단순화되고 효과적인 프롬프트 생성을 담당
 */
public class GeminiPromptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GeminiPromptBuilder.class);
    
    private String corePromptTemplate;
    
    public GeminiPromptBuilder() {
        loadCorePromptTemplate();
    }
    
    /**
     * 핵심 프롬프트 템플릿 로드
     */
    private void loadCorePromptTemplate() {
        try {
            // JAR 내부 리소스 로딩을 위한 InputStream 사용
            try (var inputStream = getClass().getClassLoader().getResourceAsStream("prompts/gemini-core-prompt.md")) {
                if (inputStream != null) {
                    corePromptTemplate = new String(inputStream.readAllBytes());
                    logger.info("Core prompt template loaded successfully");
                } else {
                    logger.warn("Core prompt template not found, using default");
                    corePromptTemplate = createDefaultCorePrompt();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load core prompt template", e);
            // 기본 템플릿 사용
            corePromptTemplate = createDefaultCorePrompt();
        }
    }
    
    /**
     * 단순화된 통합 분석 프롬프트 생성
     */
    public String buildUnifiedAnalysisPrompt(Path projectPath, String projectContext) {
        StringBuilder prompt = new StringBuilder();
        
        // 시스템 지시사항
        prompt.append("당신은 전문 Java 코드 품질 분석가입니다.\n\n");
        
        // 프로젝트 컨텍스트 (간단한 정보만)
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            prompt.append("=== 프로젝트 컨텍스트 ===\n");
            prompt.append(projectContext); // 컨텍스트 제한 제거
            prompt.append("\n\n");
        }
        
        // 분석 요청
        prompt.append("=== 분석 요청 ===\n");
        prompt.append("다음 Java 프로젝트를 종합적으로 분석하고 평가해주세요:\n\n");
        prompt.append("**분석 대상:**\n");
        prompt.append("- 메인 코드: ").append(projectPath.resolve("src/main/java")).append("\n");
        prompt.append("- 테스트 코드: ").append(projectPath.resolve("src/test/java")).append("\n\n");
        
        prompt.append("**평가 영역:**\n");
        prompt.append("1. 코드 품질 (명명규칙, 복잡도, 중복)\n");
        prompt.append("2. 아키텍처 (계층분리, 의존성, 패턴)\n");
        prompt.append("3. 테스트 (커버리지, 품질, TDD)\n");
        prompt.append("4. 보안 (입력검증, 인증, 민감정보)\n\n");
        
        prompt.append("**응답 요청사항:**\n");
        prompt.append("- 각 영역별 평가와 분석\n");
        prompt.append("- 발견된 주요 문제점들\n");
        prompt.append("- 구체적인 개선 권장사항\n");
        prompt.append("- 전반적인 코드 품질에 대한 피드백\n\n");
        
        prompt.append("**출력 형식:**\n");
        prompt.append("점수나 등급 없이 자유로운 텍스트 형식으로 상세하고 유용한 피드백을 작성해주세요.");
        
        return prompt.toString();
    }
    
    /**
     * 코드 품질 전용 분석 프롬프트 생성
     */
    public String buildCodeQualityPrompt(Path projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Java 코드 품질을 전문적으로 분석하세요.\n\n");
        
        prompt.append("**분석 대상:** ").append(projectPath.resolve("src/main/java")).append("\n\n");
        
        prompt.append("**평가 기준:**\n");
        prompt.append("1. 명명규칙: 클래스, 메서드, 변수명의 명확성과 일관성\n");
        prompt.append("2. 메서드 복잡도: 길이, 복잡도, 단일책임 원칙 준수\n");
        prompt.append("3. 코드 중복: DRY 원칙 준수 여부\n");
        prompt.append("4. 코드 구조: 가독성, 주석 품질\n\n");
        
        return prompt.toString() + getSimplifiedJsonSchema("code_quality");
    }
    
    /**
     * 아키텍처 전용 분석 프롬프트 생성
     */
    public String buildArchitecturePrompt(Path projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Java 프로젝트의 아키텍처 패턴 준수를 전문적으로 분석하세요.\n\n");
        
        prompt.append("**분석 대상:** ").append(projectPath.resolve("src/main/java")).append("\n\n");
        
        prompt.append("**평가 기준:**\n");
        prompt.append("1. 계층 분리: 도메인, 애플리케이션, 인프라 계층의 명확한 구분\n");
        prompt.append("2. 의존성 방향: 외부 계층이 내부 계층에만 의존하는지 확인\n");
        prompt.append("3. 인터페이스 활용: 추상화 의존, 구현체 격리 여부\n");
        prompt.append("4. 패키지 구조: 기능별/도메인별 응집도 높은 모듈 구성\n");
        prompt.append("5. 순환 의존성: 패키지 간 순환 참조 여부\n\n");
        
        prompt.append("**특별 고려사항:**\n");
        prompt.append("- 헥사고날 아키텍처: 포트/어댑터 패턴 적용\n");
        prompt.append("- CQRS 패턴: Command와 Query 분리\n");
        prompt.append("- DDD 원칙: 도메인 모델의 무결성\n");
        prompt.append("- Clean Architecture: 의존성 규칙 준수\n\n");
        
        prompt.append("**검증 포인트:**\n");
        prompt.append("- adapter 패키지가 domain을 참조하는가?\n");
        prompt.append("- domain 패키지가 외부 라이브러리에 의존하지 않는가?\n");
        prompt.append("- application 서비스가 도메인 규칙을 조합하는가?\n");
        prompt.append("- 인프라 구현체가 인터페이스를 통해 주입되는가?\n\n");
        
        return prompt.toString() + getSimplifiedJsonSchema("architecture");
    }
    
    /**
     * 보안 평가 전용 분석 프롬프트 생성
     */
    public String buildSecurityPrompt(Path projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Java 프로젝트의 보안 모범사례 준수를 전문적으로 분석하세요.\n\n");
        
        prompt.append("**분석 대상:** ").append(projectPath.resolve("src/main/java")).append("\n\n");
        
        prompt.append("**평가 기준:**\n");
        prompt.append("1. 입력 검증: 모든 외부 입력의 유효성 검증\n");
        prompt.append("2. 인증/인가: 적절한 접근 제어 구현\n");
        prompt.append("3. 민감정보 처리: 비밀번호, 토큰 등의 안전한 처리\n");
        prompt.append("4. 예외 처리: 민감한 정보 노출 방지\n");
        prompt.append("5. 로깅 보안: 민감 정보 로그 출력 금지\n\n");
        
        prompt.append("**보안 체크리스트:**\n");
        prompt.append("- SQL Injection 방지: PreparedStatement 사용\n");
        prompt.append("- XSS 방지: 출력 데이터 이스케이프 처리\n");
        prompt.append("- CSRF 보호: 토큰 기반 보호 메커니즘\n");
        prompt.append("- 패스워드 해싱: BCrypt 등 강력한 해시 함수\n");
        prompt.append("- JWT 보안: 적절한 서명 및 만료 처리\n\n");
        
        prompt.append("**Spring Security 고려사항:**\n");
        prompt.append("- SecurityConfig 설정의 적절성\n");
        prompt.append("- 메서드 레벨 보안 어노테이션 활용\n");
        prompt.append("- HTTPS 강제 설정\n");
        prompt.append("- 세션 관리 정책\n\n");
        
        return prompt.toString() + getSimplifiedJsonSchema("security");
    }
    
    /**
     * 테스트 품질 전용 분석 프롬프트 생성
     */
    public String buildTestingPrompt(Path projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Java 프로젝트의 테스트 품질을 전문적으로 분석하세요.\n\n");
        
        prompt.append("**분석 대상:** ").append(projectPath.resolve("src/test/java")).append("\n\n");
        
        prompt.append("**평가 기준:**\n");
        prompt.append("1. 테스트 커버리지: 중요한 비즈니스 로직의 충분한 테스트\n");
        prompt.append("2. 테스트 품질: 의미있고 신뢰할 수 있는 테스트\n");
        prompt.append("3. 테스트 구조: Given-When-Then 패턴 적용\n");
        prompt.append("4. 테스트 네이밍: 테스트 의도를 명확히 표현하는 이름\n");
        prompt.append("5. 테스트 격리: 각 테스트의 독립성 보장\n\n");
        
        prompt.append("**TDD 방법론 검증:**\n");
        prompt.append("- Red-Green-Refactor 사이클 흔적\n");
        prompt.append("- 테스트가 요구사항을 명확히 표현하는가\n");
        prompt.append("- 실패하는 테스트부터 작성했는지 확인\n");
        prompt.append("- 최소한의 코드로 테스트를 통과시켰는가\n\n");
        
        prompt.append("**테스트 유형별 평가:**\n");
        prompt.append("- 단위 테스트: 개별 메서드/클래스 테스트\n");
        prompt.append("- 통합 테스트: 컴포넌트 간 상호작용 테스트\n");
        prompt.append("- 계약 테스트: API 계약 검증\n");
        prompt.append("- E2E 테스트: 전체 플로우 검증\n\n");
        
        prompt.append("**테스트 안티패턴 검증:**\n");
        prompt.append("- 테스트 코드 중복\n");
        prompt.append("- 하나의 테스트에서 여러 기능 검증\n");
        prompt.append("- 외부 의존성에 의존하는 테스트\n");
        prompt.append("- 실행 순서에 의존하는 테스트\n\n");
        
        return prompt.toString() + getSimplifiedJsonSchema("testing");
    }
    
    /**
     * 간소화된 JSON 스키마 생성
     */
    private String getSimplifiedJsonSchema(String focusCategory) {
        return String.format(
            "**응답 형식:**\n" +
            "```json\n" +
            "{\n" +
            "  \"score\": 0-100,\n" +
            "  \"focus_category\": \"%s\",\n" +
            "  \"violations\": [{\"severity\": \"error|warning|info\", \"file\": \"경로\", \"line\": 숫자, \"message\": \"설명\"}],\n" +
            "  \"recommendations\": [\"개선방안\"]\n" +
            "}\n" +
            "```\n\n" +
            "반드시 위 JSON 형식만 출력하세요.",
            focusCategory
        );
    }
    
    /**
     * 컨텍스트 크기 제한 (완화됨)
     */
    private String truncateContext(String context, int maxLength) {
        // 매우 큰 텍스트만 제한 (10,000자 이상)
        if (context.length() <= 10000) {
            return context;
        }
        
        return context.substring(0, 9500) + "\n\n[컨텍스트가 매우 길어 일부 생략됨]";
    }
    
    /**
     * 기본 코어 프롬프트 생성 (리소스 로드 실패 시 사용)
     */
    private String createDefaultCorePrompt() {
        return "전문 Java 코드 품질 분석가로서 정확하고 구조화된 분석을 수행하세요.\n" +
               "반드시 지정된 JSON 형식으로 응답하고, 근거있는 평가를 제공하세요.";
    }
}