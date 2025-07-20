# QA Module 구현 분석 문서

## 프로젝트 개요

이 프로젝트는 Java 애플리케이션의 코드 품질을 분석하는 통합 모듈로, 여러 정적 분석 도구와 AI 기반 분석을 제공하는 라이브러리입니다.

## 아키텍처 구조

### 패키지 구조
```
com.ldx.qa
├── QualityAnalyzer        # 메인 진입점
├── QaInitializer          # 초기화 담당
├── analyzer/              # 각종 분석기 구현
│   ├── Analyzer           # 분석기 인터페이스
│   ├── AnalysisException  # 분석 예외
│   ├── CheckstyleAnalyzer # Checkstyle 분석기
│   ├── PmdAnalyzer        # PMD 분석기
│   ├── SpotBugsAnalyzer   # SpotBugs 분석기
│   ├── JaCoCoAnalyzer     # JaCoCo 분석기
│   ├── ArchUnitAnalyzer   # ArchUnit 분석기
│   └── GeminiAnalyzer     # Gemini AI 분석기
├── config/
│   └── QaConfiguration    # 설정 관리
├── model/                 # 데이터 모델
│   ├── AnalysisResult     # 분석 결과
│   ├── QualityReport      # 품질 보고서
│   └── Violation          # 위반 사항
└── report/                # 리포트 생성
    ├── HtmlReportGenerator # HTML 리포트
    └── JsonReportGenerator # JSON 리포트
```

## 핵심 구현 내용

### 1. QualityAnalyzer (메인 진입점)

**위치**: `src/main/java/com/ldx/qa/QualityAnalyzer.java`

**주요 기능**:
- 정적 메서드 `analyze()` 제공으로 간편한 API
- 베이스 패키지 자동 감지 기능 (build.gradle, pom.xml, 메인 클래스 분석)
- 설정에 따른 분석기 초기화
- 분석 결과 취합 및 리포트 생성
- 명령줄 실행 지원

**베이스 패키지 자동 감지 로직**:
1. build.gradle에서 `group` 속성 추출
2. pom.xml에서 `<groupId>` 추출
3. `@SpringBootApplication` 또는 `main` 메서드 포함 클래스에서 패키지 추출

### 2. 분석기 구현

#### CheckstyleAnalyzer
- 명령줄 도구 실행 방식
- XML 리포트 생성 후 간단한 HTML 변환
- 위반 개수 카운트 및 결과 빌드

#### ArchUnitAnalyzer  
- 아키텍처 규칙 정의 (레이어 간 의존성, 패키지 구조)
- 클래스 임포트 전략 (컴파일된 클래스 → 패키지 → 클래스패스)
- 예외 처리 강화 (AssertionError, Throwable 포함)
- 점수 기반 평가 시스템

#### 기타 분석기들
- PmdAnalyzer, SpotBugsAnalyzer, JaCoCoAnalyzer, GeminiAnalyzer 구현
- 각각의 분석 도구에 특화된 실행 로직

### 3. 설정 시스템 (QaConfiguration)

**주요 특징**:
- Properties 파일 기반 설정
- 기본값 제공 및 동적 설정 오버라이드
- 각 분석기별 개별 활성화/비활성화 옵션
- 설정 파일 경로 커스터마이징

**설정 항목**:
- 일반 설정: `ignoreFailures`, `skipUnavailableAnalyzers`
- 정적 분석: 각 도구별 활성화 여부
- AI 분석: Gemini 설정
- 리포트: HTML/JSON 출력 설정
- 경로: 각종 설정 파일 경로

### 4. 모델 클래스

**AnalysisResult**: 개별 분석기 결과
- type, status, summary, violations, metrics, timestamp

**QualityReport**: 전체 품질 보고서
- timestamp, projectPath, overallStatus, results
- Builder 패턴 구현

**Violation**: 위반 사항 정보
- severity, message, type, file, line

### 5. 리포트 생성

- **HtmlReportGenerator**: HTML 형식 종합 리포트
- **JsonReportGenerator**: JSON 형식 데이터 출력

## 빌드 설정 (build.gradle)

### 주요 의존성
- **정적 분석 도구**: Checkstyle, PMD, SpotBugs, ArchUnit
- **코드 커버리지**: JaCoCo
- **JSON 처리**: Jackson
- **HTML 템플릿**: FreeMarker
- **로깅**: SLF4J (API만, 구현체는 제외)

### Shadow JAR 설정
- `shadowJar` 태스크로 all-in-one JAR 생성
- 로깅 구현체 제외 (호출 프로젝트에서 제공)
- PMD Saxon 충돌 해결
- Main-Class 설정으로 독립 실행 가능

### 충돌 해결
- PMD와 Saxon 라이브러리 충돌 완전 제거
- ASM 라이브러리 버전 통일

## 설정 시스템

### 기본 설정 (qa.properties)
- 모든 정적 분석 도구 활성화
- AI 분석 활성화
- HTML/JSON 리포트 모두 생성
- 실패 시 무시 모드 (개발용)

### 설정 커스터마이징
- Properties 파일을 통한 세밀한 제어
- 각 분석기별 설정 파일 경로 지정
- 품질 기준 임계값 설정

## 사용 방식

### 1. 라이브러리 통합
```java
QaConfiguration config = QaConfiguration.defaultConfig();
QualityReport report = QualityAnalyzer.analyze(
    projectDir, 
    outputDir, 
    config
);
```

### 2. Gradle 태스크
```gradle
task qualityCheck {
    doLast {
        def config = QaConfiguration.defaultConfig()
        QualityAnalyzer.analyze(projectDir, outputDir, config)
    }
}
```

### 3. 명령줄 실행
```bash
java -jar qa-1.0.0-all.jar <projectDir> <outputDir> [configFile]
```

## 확장성 및 특징

### 장점
1. **모듈화된 설계**: 각 분석기 독립적 구현
2. **유연한 설정**: Properties 기반 동적 설정
3. **에러 복원력**: 개별 분석기 실패 시에도 다른 분석 계속
4. **자동 감지**: 프로젝트 구조 자동 분석
5. **다양한 출력**: HTML, JSON 리포트 지원

### 개선 가능 영역
1. **성능 최적화**: 분석기 병렬 실행
2. **리포트 개선**: 더 상세한 시각화
3. **규칙 커스터마이징**: 사용자 정의 규칙 지원
4. **캐싱**: 분석 결과 캐싱으로 재분석 시간 단축

## 설정 우선순위 시스템

### 설정 로딩 순서
1. **Properties 파일 설정이 최우선**
   - `qa.properties` 파일에 명시적으로 설정된 값
   - 사용자가 직접 지정한 설정이 가장 높은 우선순위

2. **프로젝트 정보 자동 감지**
   - Properties에 설정되지 않은 경우 프로젝트 구조에서 자동 추출
   - build.gradle의 `group` 속성
   - pom.xml의 `<groupId>` 태그
   - 메인 클래스(@SpringBootApplication, main 메서드)의 패키지 정보

3. **기본값 적용**
   - 위 두 방법으로 정보를 얻을 수 없는 경우 내장 기본값 사용

### 자동 감지 메커니즘

**베이스 패키지 감지 예시**:
```java
// 1순위: Properties 파일
qa.static.archunit.basePackage=com.example.myproject

// 2순위: build.gradle에서 자동 감지
group = 'com.example.myproject'

// 3순위: 메인 클래스에서 감지
package com.example.myproject;
@SpringBootApplication
public class MyProjectApplication { ... }
```

이러한 계층적 설정 시스템으로 사용자 편의성과 설정 유연성을 동시에 제공합니다.

## 개발 및 테스트 프로세스

### 라이브러리 빌드
1. **shadowJar 사용**: 라이브러리 생성 시 반드시 shadowJar 태스크를 사용
   ```bash
   ./gradlew shadowJar
   ```

2. **빌드 결과물**: `build/libs/qa-1.0.0-all.jar` 파일이 생성됨

### 테스트 환경 설정
- **테스트 대상 프로젝트**: `~/workspace/identitybridge`
- **라이브러리 배포 위치**: 테스트 대상 프로젝트의 `libs` 폴더

### 테스트 프로세스
1. **라이브러리 빌드 및 복사**:
   ```bash
   ./gradlew shadowJar
   cp build/libs/qa-1.0.0-all.jar ~/workspace/identitybridge/libs/
   ```

2. **테스트 대상에서 검증**:
   ```bash
   cd ~/workspace/identitybridge
   ./gradlew qualityCheck
   ```

3. **반드시 테스트 대상의 Gradle을 통해 검증**해야 함

### 배포 계획
- **목표**: Maven Central 배포
- **현재**: 로컬 라이브러리 파일로 테스트 진행

## 배포 전략

현재는 shadowJar로 fat JAR를 생성하여 로컬 테스트 진행 중이며, 향후 Maven Central 배포를 목표로 하고 있습니다.