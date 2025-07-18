## ⚠️ 중요 주의사항

**절대 상위 프로젝트 수정 금지**
- 이 프로젝트는 프로젝트의 하위 모듈입니다
- 상위 프로젝트의 파일들을 절대 수정하면 안됩니다
- 오직 현재 디렉토리 내부의 파일만 작업 대상입니다
- 상위 프로젝트의 지침들을 확인 할 필요 없음(CLAUDE.md 등)

# IdentityBridge QA Module

품질 분석을 위한 Java 라이브러리 모듈입니다. 정적 분석 도구들과 AI 기반 분석을 통합하여 종합적인 코드 품질 리포트를 제공합니다.

## 특징

- ✅ **정적 분석 도구 통합**: Checkstyle, PMD, SpotBugs, JaCoCo
- 🤖 **AI 기반 분석**: Gemini AI를 활용한 지능형 코드 분석
- 📊 **통합 리포트**: HTML 및 JSON 형식의 종합 리포트 생성
- 🔧 **유연한 설정**: Properties 파일을 통한 간편한 설정
- 🚀 **쉬운 통합**: Gradle/Maven 프로젝트에 쉽게 통합 가능

## 사용 방법

### 1. 의존성 추가

#### Gradle
```gradle
dependencies {
    implementation 'com.identitybridge:qa-module:1.0.0'
}
```

#### Maven
```xml
<dependency>
    <groupId>com.identitybridge</groupId>
    <artifactId>qa-module</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Gradle Task 설정

```gradle
// build.gradle
import com.identitybridge.qa.QualityAnalyzer
import com.identitybridge.qa.config.QaConfiguration

task qualityCheck {
    doLast {
        def config = QaConfiguration.defaultConfig()
        def report = QualityAnalyzer.analyze(
            projectDir, 
            file("${buildDir}/reports/quality"),
            config
        )
        
        if (report.overallStatus == "fail") {
            throw new GradleException("Quality check failed")
        }
    }
}

// 테스트 후 품질 검사 실행
qualityCheck.dependsOn test
check.dependsOn qualityCheck
```

### 3. 설정 파일 (선택사항)

`config/qa.properties` 파일 생성:
```properties
# 일반 설정
qa.ignoreFailures=false
qa.skipUnavailableAnalyzers=true

# 정적 분석
qa.static.enabled=true
qa.static.checkstyle.enabled=true
qa.static.pmd.enabled=true
qa.static.spotbugs.enabled=true
qa.static.jacoco.enabled=true

# AI 분석
qa.ai.enabled=true
qa.ai.gemini.enabled=true

# 리포트
qa.reports.html.enabled=true
qa.reports.json.enabled=true

# 경로 설정
qa.static.checkstyle.configPath=config/checkstyle.xml
qa.ai.gemini.guidePath=config/gemini-guide.md
```

### 4. 커스텀 설정으로 실행

```gradle
task qualityCheckWithConfig {
    doLast {
        def config = QaConfiguration.fromFile(file("config/qa.properties"))
        QualityAnalyzer.analyze(projectDir, file("${buildDir}/reports/quality"), config)
    }
}
```

### 5. 명령줄 실행

```bash
# 기본 설정으로 실행
./gradlew qualityCheck

# 커스텀 설정으로 실행
./gradlew qualityCheckWithConfig
```

## 분석 결과

분석이 완료되면 다음 위치에 리포트가 생성됩니다:

- **HTML 리포트**: `build/reports/quality/quality-report.html`
- **JSON 리포트**: `build/reports/quality/quality-report.json`

## 조건부 실행

### SonarQube
- SonarQube 서버가 실행 중이어야 분석이 수행됩니다
- 서버가 없으면 자동으로 건너뜁니다

### Gemini AI
- Gemini CLI가 설치되어 있어야 분석이 수행됩니다
- 설치되지 않은 경우 자동으로 건너뜁니다

## 커스터마이징

### Checkstyle 규칙 변경
프로젝트 루트에 `config/static/checkstyle/checkstyle.xml` 파일을 생성하여 기본 규칙을 덮어쓸 수 있습니다.

### Gemini 가이드 변경
프로젝트 루트에 `config/ai/gemini-guide.md` 파일을 생성하여 AI 분석 기준을 커스터마이징할 수 있습니다.

## 라이선스

Apache License 2.0
