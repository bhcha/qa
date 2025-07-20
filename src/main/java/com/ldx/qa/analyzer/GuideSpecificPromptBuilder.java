package com.ldx.qa.analyzer;

import com.ldx.qa.analyzer.guide.GuideMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 지침별 특화된 프롬프트를 생성하는 클래스
 */
public class GuideSpecificPromptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GuideSpecificPromptBuilder.class);
    
    /**
     * 특정 가이드를 위한 맞춤 프롬프트 생성
     */
    public String buildPromptForGuide(Path projectPath, GuideMetadata guide, String guideContent) {
        logger.debug("가이드별 프롬프트 생성: {} ({})", guide.getDisplayName(), guide.getCategory());
        
        StringBuilder prompt = new StringBuilder();
        
        // 기본 시스템 지시사항
        prompt.append("당신은 전문 Java 코드 분석가입니다.\n\n");
        
        // 지침별 특화 분석 목표
        prompt.append(buildAnalysisObjective(guide));
        
        // 가이드 내용 포함
        prompt.append("=== 분석 지침 ===\n");
        prompt.append("다음 지침에 따라 분석을 수행해주세요:\n\n");
        prompt.append(guideContent);
        prompt.append("\n\n");
        
        // 분석 대상 명시
        prompt.append("=== 분석 대상 ===\n");
        prompt.append("프로젝트 경로: ").append(projectPath).append("\n");
        prompt.append("메인 코드: ").append(projectPath.resolve("src/main/java")).append("\n");
        prompt.append("테스트 코드: ").append(projectPath.resolve("src/test/java")).append("\n\n");
        
        // 카테고리별 특화 요청사항
        prompt.append(buildCategorySpecificRequests(guide.getCategory()));
        
        // 출력 형식 가이드라인
        prompt.append(buildOutputGuidelines(guide));
        
        logger.debug("생성된 프롬프트 길이: {} characters", prompt.length());
        return prompt.toString();
    }
    
    /**
     * 지침별 분석 목표 생성
     */
    private String buildAnalysisObjective(GuideMetadata guide) {
        StringBuilder objective = new StringBuilder();
        objective.append("=== 분석 목표 ===\n");
        
        switch (guide.getCategory()) {
            case "general":
                objective.append("전반적인 코드 품질을 종합적으로 분석하고 평가합니다.\n");
                objective.append("명명규칙, 코드 복잡도, 중복 제거, 가독성 등을 중점적으로 검토합니다.\n\n");
                break;
                
            case "security":
                objective.append("보안 관점에서 코드를 분석하고 취약점을 식별합니다.\n");
                objective.append("OWASP 가이드라인 준수, 입력 검증, 인증/인가, 민감정보 처리를 중점 검토합니다.\n\n");
                break;
                
            case "tdd":
                objective.append("테스트 주도 개발(TDD) 방법론 준수도를 분석합니다.\n");
                objective.append("Red-Green-Refactor 사이클, 테스트 우선 작성, 리팩토링 품질을 중점 검토합니다.\n\n");
                break;
                
            case "testing":
                objective.append("테스트 코드의 품질과 커버리지를 분석합니다.\n");
                objective.append("테스트 구조, 네이밍, 격리성, 신뢰성을 중점적으로 검토합니다.\n\n");
                break;
                
            case "quality":
                objective.append("코드 품질 메트릭과 표준 준수도를 분석합니다.\n");
                objective.append("정량적 품질 지표와 코딩 표준 준수 여부를 중점 검토합니다.\n\n");
                break;
                
            default:
                objective.append("지침에 따른 특화 분석을 수행합니다.\n");
                objective.append("해당 영역의 모범사례와 표준을 기준으로 검토합니다.\n\n");
                break;
        }
        
        return objective.toString();
    }
    
    /**
     * 카테고리별 특화 요청사항 생성
     */
    private String buildCategorySpecificRequests(String category) {
        StringBuilder requests = new StringBuilder();
        requests.append("=== 특화 분석 요청사항 ===\n");
        
        switch (category) {
            case "general":
                requests.append("1. 클래스와 메서드의 명명규칙 일관성 검토\n");
                requests.append("2. 메서드 복잡도와 길이 분석\n");
                requests.append("3. 코드 중복(DRY 원칙) 위반 사례 식별\n");
                requests.append("4. 주석과 문서화 품질 평가\n");
                requests.append("5. SOLID 원칙 준수도 검토\n\n");
                break;
                
            case "security":
                requests.append("1. SQL 인젝션 방지 조치 확인\n");
                requests.append("2. XSS 방어 메커니즘 검토\n");
                requests.append("3. 입력 데이터 검증 로직 분석\n");
                requests.append("4. 인증/인가 구현 방식 평가\n");
                requests.append("5. 민감정보 로깅 및 노출 위험 점검\n");
                requests.append("6. 암호화 및 해시 처리 방식 검토\n\n");
                break;
                
            case "tdd":
                requests.append("1. 테스트 우선 작성 흔적 확인\n");
                requests.append("2. Red-Green-Refactor 사이클 준수도 분석\n");
                requests.append("3. 테스트가 요구사항을 명확히 표현하는지 검토\n");
                requests.append("4. 최소한의 코드로 테스트 통과 여부 확인\n");
                requests.append("5. 리팩토링 단계의 품질 평가\n\n");
                break;
                
            case "testing":
                requests.append("1. 테스트 커버리지 분석 (라인, 브랜치, 메서드)\n");
                requests.append("2. Given-When-Then 패턴 적용도 검토\n");
                requests.append("3. 테스트 네이밍 컨벤션 일관성 확인\n");
                requests.append("4. 테스트 격리성과 독립성 검증\n");
                requests.append("5. Mock과 Stub 사용의 적절성 평가\n");
                requests.append("6. 테스트 유지보수성 분석\n\n");
                break;
                
            case "quality":
                requests.append("1. 순환 복잡도 측정 및 분석\n");
                requests.append("2. 코드 중복률 계산\n");
                requests.append("3. 메서드 및 클래스 크기 분석\n");
                requests.append("4. 응집도와 결합도 평가\n");
                requests.append("5. 코딩 표준 준수도 검토\n\n");
                break;
                
            default:
                requests.append("1. 지침에 명시된 기준에 따른 분석\n");
                requests.append("2. 모범사례 대비 현재 상태 평가\n");
                requests.append("3. 개선 가능 영역 식별\n\n");
                break;
        }
        
        return requests.toString();
    }
    
    /**
     * 출력 형식 가이드라인 생성
     */
    private String buildOutputGuidelines(GuideMetadata guide) {
        StringBuilder guidelines = new StringBuilder();
        guidelines.append("=== 출력 가이드라인 ===\n");
        guidelines.append("다음 형식으로 분석 결과를 작성해주세요:\n\n");
        
        guidelines.append("**분석 제목**: ").append(guide.getDisplayName()).append(" 분석 결과\n\n");
        guidelines.append("**주요 발견사항**:\n");
        guidelines.append("- 긍정적인 측면들\n");
        guidelines.append("- 개선이 필요한 영역들\n");
        guidelines.append("- 심각한 문제점들 (있는 경우)\n\n");
        
        guidelines.append("**구체적인 개선 권장사항**:\n");
        guidelines.append("- 즉시 적용 가능한 개선사항\n");
        guidelines.append("- 중장기적 개선 계획\n");
        guidelines.append("- 우선순위별 정리\n\n");
        
        guidelines.append("**종합 평가**:\n");
        guidelines.append("- 현재 상태에 대한 객관적 평가\n");
        guidelines.append("- 향후 발전 방향 제시\n\n");
        
        guidelines.append("**중요 제약사항**:\n");
        guidelines.append("1. 응답은 2000자 이내로 핵심 내용만 간결하게 작성해주세요\n");
        guidelines.append("2. 점수나 등급 없이 구체적이고 실용적인 피드백을 제공해주세요\n");
        guidelines.append("3. 개발자가 바로 적용할 수 있는 명확한 조언을 포함해주세요\n");
        guidelines.append("4. 마크다운 문법을 사용하여 가독성을 높여주세요 (예: **굵은글씨**, *기울임*, # 제목, - 리스트)\n\n");
        
        return guidelines.toString();
    }
    
    /**
     * 프롬프트 길이 최적화
     */
    private String optimizePromptLength(String prompt, int maxLength) {
        if (prompt.length() <= maxLength) {
            return prompt;
        }
        
        logger.warn("프롬프트가 최대 길이({})를 초과하여 축약합니다: {} -> {}", 
                   maxLength, prompt.length(), maxLength);
        
        // 중요한 섹션은 유지하고 덜 중요한 부분 축약
        return prompt.substring(0, maxLength - 100) + "\n\n[내용이 길어 일부 생략됨]";
    }
}