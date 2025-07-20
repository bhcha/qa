# Gemini AI 지침별 완전 동기 순차 분석 시스템 - 요구사항 명세서

## 📋 프로젝트 분석 결과

### 기존 시스템 분석

#### 1. 현재 GeminiAnalyzer 구조
- **위치**: `com.ldx.qa.analyzer.GeminiAnalyzer`
- **역할**: 단일 통합 프롬프트로 Gemini AI 분석 수행
- **주요 구성요소**:
  - `GeminiPromptBuilder`: 프롬프트 생성
  - `GeminiResponseParser`: 응답 파싱 (순수 텍스트 방식)
  - `GeminiFallbackAnalyzer`: 실패 시 대체 분석
  - `GeminiAnalysisMetrics`: 메트릭 수집

#### 2. QualityAnalyzer 통합 방식
- **위치**: `com.ldx.qa.QualityAnalyzer.initializeAnalyzers()`
- **통합 로직**: 
  ```java
  if (config.isAiAnalysisEnabled()) {
      GeminiAnalyzer geminiAnalyzer = new GeminiAnalyzer(config);
      if (geminiAnalyzer.isAvailable() || !config.isSkipUnavailableAnalyzers()) {
          analyzerList.add(geminiAnalyzer);
      }
  }
  ```
- **실행 방식**: 순차적으로 각 analyzer.analyze() 호출

#### 3. QaConfiguration 설정 시스템
- **AI 관련 설정**:
  - `aiAnalysisEnabled`: AI 분석 전체 활성화
  - `geminiEnabled`: Gemini 개별 활성화
  - `geminiGuidePath`: 단일 가이드 파일 경로 (`config/ai/gemini-guide.md`)

#### 4. identitybridge/config/ai 디렉토리 구조
```
config/ai/
├── gemini-guide.md           # 일반 코드 품질 분석
├── quality-metrics.md        # 품질 메트릭 검증
├── secure-guide.md          # 보안 분석
├── tdd-enhancement-plan.md  # TDD 향상 계획
├── tdd-guide.md            # TDD 지침
├── test-coverage-improvement-plan.md # 테스트 커버리지 개선
└── test-strategy.md        # 테스트 전략
```

---

## 🎯 새로운 시스템 요구사항

### 기능 요구사항

#### FR-1: 지침별 순차 분석
- **설명**: config/ai/*.md 파일들을 하나씩 순차적으로 처리
- **입력**: 프로젝트 경로, config/ai 디렉토리
- **출력**: 각 지침별 AnalysisResult
- **제약사항**: 완전 동기 방식, 이전 지침 100% 완료 후 다음 시작

#### FR-2: 지침별 특화 프롬프트
- **설명**: 각 가이드 파일 내용을 읽어 맞춤 프롬프트 생성
- **입력**: 개별 가이드 파일(.md)
- **출력**: 해당 지침에 특화된 분석 프롬프트
- **예시**: 
  - `secure-guide.md` → 보안 중심 분석 프롬프트
  - `tdd-guide.md` → TDD 준수도 분석 프롬프트

#### FR-3: 개별 리포트 생성
- **설명**: 각 지침별로 독립적인 분석 결과 생성
- **출력 형식**: HTML, JSON
- **파일명 규칙**: 
  - `01-gemini-guide-report.html`
  - `02-secure-guide-report.html`
  - `03-tdd-guide-report.html`

#### FR-4: 통합 리포트
- **설명**: 모든 지침 분석 결과를 종합한 최종 리포트
- **구성**: 지침별 섹션 + 종합 평가
- **파일명**: `00-combined-guide-report.html`

#### FR-5: 설정 기반 제어
- **설명**: 지침별 활성화/비활성화 및 실행 순서 제어
- **설정 예시**:
  ```properties
  qa.ai.gemini.guides.enabled=true
  qa.ai.gemini.guides.directory=config/ai
  qa.ai.gemini.guide.general.enabled=true
  qa.ai.gemini.guide.security.enabled=true
  ```

### 비기능 요구사항

#### NFR-1: 완전 동기 실행
- **설명**: Process.waitFor()를 통한 완전 대기
- **제약사항**: 
  - Thread.sleep() 사용 금지
  - 타이머 기반 대기 금지
  - 병렬 처리 금지

#### NFR-2: 에러 격리
- **설명**: 개별 지침 실패가 전체에 영향 주지 않음
- **처리 방식**: try-catch로 개별 지침 실패 격리
- **복구 전략**: 실패한 지침 건너뛰고 다음 지침 계속

#### NFR-3: 진행 상황 표시
- **형식**: `[1/4] 일반 코드 품질 분석 중... ✓ 완료`
- **정보**: 현재 진행률, 지침명, 완료 상태
- **로그 레벨**: INFO

#### NFR-4: 메모리 효율성
- **설명**: 각 지침 완료 후 메모리 정리
- **방식**: 대용량 문자열 즉시 해제, GC 친화적 구현

### 데이터 요구사항

#### DR-1: 지침 메타데이터
```java
public class GuideMetadata {
    private String fileName;        // "secure-guide.md"
    private String displayName;     // "보안 분석"
    private String category;        // "security"
    private int priority;          // 실행 순서
    private boolean enabled;       // 활성화 여부
}
```

#### DR-2: 지침별 분석 결과
```java
public class GuideAnalysisResult extends AnalysisResult {
    private String guideName;      // 지침 파일명
    private String guideContent;   // 지침 내용 요약
    private Duration executionTime; // 실행 시간
    private String promptUsed;     // 사용된 프롬프트
}
```

### 인터페이스 요구사항

#### IR-1: 기존 Analyzer 인터페이스 호환
- **설명**: 기존 `Analyzer` 인터페이스 구현 유지
- **메서드**: `analyze(Path projectPath)` 구현 필수
- **반환**: `AnalysisResult` 객체

#### IR-2: QualityAnalyzer 통합
- **설명**: 기존 initializeAnalyzers() 로직에 자연스럽게 통합
- **조건부 사용**: 설정에 따라 기존 GeminiAnalyzer 또는 새 분석기 선택

---

## 🏗️ 구현 아키텍처

### 클래스 설계

#### 1. SequentialGuideGeminiAnalyzer (주 분석기)
```java
public class SequentialGuideGeminiAnalyzer implements Analyzer {
    private final QaConfiguration config;
    private final GuideFileLoader guideLoader;
    private final GuideSpecificPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    
    @Override
    public AnalysisResult analyze(Path projectPath) {
        List<GuideAnalysisResult> results = analyzeAllGuides(projectPath);
        return combineResults(results);
    }
    
    private List<GuideAnalysisResult> analyzeAllGuides(Path projectPath) {
        // 완전 동기 순차 실행 로직
    }
}
```

#### 2. GuideSpecificPromptBuilder (지침별 프롬프트)
```java
public class GuideSpecificPromptBuilder {
    public String buildPromptForGuide(Path projectPath, GuideMetadata guide, String guideContent) {
        // 지침 내용을 바탕으로 특화된 프롬프트 생성
    }
}
```

#### 3. GuideFileLoader (가이드 파일 로더)
```java
public class GuideFileLoader {
    public List<GuideMetadata> loadGuideFiles(Path configAiDirectory) {
        // config/ai/*.md 파일들 스캔 및 메타데이터 생성
    }
    
    public String loadGuideContent(Path guideFile) {
        // 가이드 파일 내용 로드
    }
}
```

### 실행 플로우

```
1. 프로젝트 스캔 → config/ai/*.md 파일 탐지
2. GuideMetadata 생성 → 우선순위별 정렬
3. 순차 실행 루프:
   for (GuideMetadata guide : sortedGuides) {
       if (guide.isEnabled()) {
           String prompt = buildPromptForGuide(guide);
           String response = executeGeminiCommand(prompt);  // 완전 대기
           GuideAnalysisResult result = parseResponse(response);
           saveIndividualReport(guide, result);
           results.add(result);
       }
   }
4. 통합 리포트 생성
```

---

## 📊 성공 기준

### 기능 검증
- [ ] config/ai 디렉토리의 모든 .md 파일 처리
- [ ] 각 지침별 특화된 프롬프트 생성 확인
- [ ] 완전 동기 실행 (이전 완료 후 다음 시작) 검증
- [ ] 지침별 개별 리포트 생성 확인
- [ ] 통합 리포트에 모든 지침 결과 포함 확인

### 성능 검증  
- [ ] 메모리 사용량 모니터링
- [ ] 각 지침별 실행 시간 측정
- [ ] 전체 분석 시간 vs 기존 방식 비교

### 안정성 검증
- [ ] 개별 지침 실패 시 다른 지침 정상 실행 확인
- [ ] Gemini CLI 오류 상황 처리 확인
- [ ] 타임아웃 상황 처리 확인

---

## 📋 제약사항 및 가정

### 제약사항
1. **완전 동기 실행**: 병렬 처리 금지, 완전 대기 필수
2. **기존 호환성**: 기존 QualityAnalyzer 통합 방식 유지
3. **설정 확장**: 기존 QaConfiguration 구조 최대한 활용
4. **Gemini CLI 의존**: 외부 Gemini CLI 도구 필요

### 가정
1. config/ai 디렉토리에 .md 파일들이 존재
2. Gemini CLI가 설치되어 있고 정상 작동
3. 각 가이드 파일은 유효한 마크다운 형식
4. 프로젝트 디렉토리 구조는 표준 Java 프로젝트 형태

---

**작성일**: 2025-07-20  
**버전**: 1.0  
**상태**: 최종 승인 대기