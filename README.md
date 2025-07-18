# IdentityBridge QA Module

품질 분석을 위한 Java 라이브러리 모듈입니다. 정적 분석 도구들과 ArchUnit 아키텍처 검증을 통합하여 종합적인 코드 품질 리포트를 제공합니다.

## ✨ 특징

- ✅ **정적 분석 도구 통합**: Checkstyle, PMD, SpotBugs, JaCoCo
- 🏗️ **아키텍처 검증**: ArchUnit을 통한 헥사고날 아키텍처 규칙 검증
- 🤖 **AI 기반 분석**: Gemini CLI와 연동한 지능형 코드 분석 (선택사항)
- 📊 **통합 리포트**: HTML 및 JSON 형식의 종합 리포트 생성
- 🔧 **자동 초기화**: QA 설정 파일 자동 생성
- 🚀 **간편한 통합**: 단일 Gradle 태스크로 모든 분석 실행

## 🚀 빠른 시작

### 1. 프로젝트에 QA 모듈 추가

**Root 프로젝트의 `build.gradle`에 다음 내용 추가:**

```gradle
// QA Module을 서브프로젝트로 포함 (settings.gradle)
include 'qa'

// Root build.gradle
dependencies {
    implementation project(':qa')
}

// QA 초기화 태스크
task qaInit(type: JavaExec) {
    dependsOn ':qa:jar'
    description = 'Initialize QA configuration files in project root'
    group = 'setup'
    
    mainClass = 'com.identitybridge.qa.QaInitializer'
    classpath = project(':qa').sourceSets.main.runtimeClasspath
    
    doFirst {
        def overwrite = project.hasProperty('overwrite') ? project.property('overwrite') : false
        args = [projectDir.toString()]
        if (overwrite) {
            args << '--overwrite'
        }
    }
}

// 품질 검사 태스크
task qualityCheck(type: JavaExec) {
    dependsOn compileJava, ':qa:jar'
    description = 'Runs quality analysis using QA Module'
    group = 'verification'
    
    mainClass = 'com.identitybridge.qa.QualityAnalyzer'
    classpath = project(':qa').sourceSets.main.runtimeClasspath
    args = [projectDir.toString(), "${buildDir}/reports/quality".toString()]
    
    doFirst {
        file("${buildDir}/reports/quality").mkdirs()
    }
    
    doLast {
        def htmlReport = file("${buildDir}/reports/quality/quality-report.html")
        if (htmlReport.exists()) {
            println ""
            println "=" * 60
            println "Quality Analysis Complete!"
            println "=" * 60
            println "HTML Report: file://${htmlReport.absolutePath}"
            println "=" * 60
            println ""
        }
    }
}

// 테스트 후 품질 검사 자동 실행
check.dependsOn qualityCheck
```

### 2. QA 환경 초기화 (최초 1회)

```bash
# QA 설정 파일들을 프로젝트 루트에 생성
./gradlew qaInit
```

**생성되는 설정 파일들:**
```
config/
├── static/
│   ├── checkstyle/checkstyle-custom.xml
│   ├── pmd/pmd-custom-rules.xml
│   └── spotbugs/spotbugs-exclude.xml
├── archunit/
│   └── archunit.properties
└── ai/
    └── gemini-guide.md
├── qa.properties
└── docs/
    ├── qa-guide.md
    └── quality-standards.md
```

### 3. 품질 검사 실행

```bash
# 종합 품질 검사 (권장)
./gradlew qualityCheck

# 개별 컴포넌트 테스트
./gradlew test

# 아키텍처 검증만 실행
./gradlew archunitTest
```

## 📊 분석 결과

### 리포트 위치
```
build/reports/
├── quality/
│   ├── quality-report.html    # 🎯 통합 HTML 리포트 (메인)
│   ├── quality-report.json    # 상세 JSON 데이터
│   └── [analyzer-name]/       # 개별 분석 도구 결과
│       ├── checkstyle/
│       ├── pmd/
│       ├── spotbugs/
│       ├── jacoco/
│       └── archunit/
└── tests/
    └── archunitTest/
        └── index.html         # 🏗️ ArchUnit 아키텍처 검증 상세 리포트
```

### HTML 리포트 확인
```bash
# 🎯 통합 품질 리포트
open build/reports/quality/quality-report.html          # macOS
xdg-open build/reports/quality/quality-report.html      # Linux  
start build/reports/quality/quality-report.html         # Windows

# 🏗️ ArchUnit 아키텍처 검증 상세 리포트  
open build/reports/tests/archunitTest/index.html        # macOS
xdg-open build/reports/tests/archunitTest/index.html    # Linux
start build/reports/tests/archunitTest/index.html       # Windows
```

### 리포트 종류별 특징

#### 🎯 통합 품질 리포트 (`quality-report.html`)
- **전체 분석 결과**: 모든 도구의 통합 뷰
- **요약 대시보드**: 핵심 메트릭과 점수
- **위반사항 목록**: 파일별, 심각도별 분류
- **트렌드 분석**: 이전 결과와 비교

#### 🏗️ ArchUnit 리포트 (`archunitTest/index.html`)  
- **아키텍처 규칙 검증**: 헥사고날 아키텍처, CQRS 패턴
- **테스트 결과**: 통과/실패한 규칙들
- **상세 위반 정보**: 정확한 클래스와 패키지 위치
- **규칙별 설명**: 각 아키텍처 규칙의 목적과 위반 이유

## ⚙️ 설정 커스터마이징

### 기본 설정 (`config/qa.properties`)
```properties
# 일반 설정
qa.ignoreFailures=false
qa.skipUnavailableAnalyzers=true

# 정적 분석 활성화
qa.static.enabled=true
qa.static.checkstyle.enabled=true
qa.static.pmd.enabled=true
qa.static.spotbugs.enabled=true
qa.static.jacoco.enabled=true

# ArchUnit 아키텍처 검증
qa.archunit.enabled=true

# AI 분석 (선택사항)
qa.ai.enabled=true
qa.ai.gemini.enabled=true

# 리포트 형식
qa.reports.html.enabled=true
qa.reports.json.enabled=true
```

### ArchUnit 설정 (`config/archunit/archunit.properties`)
```properties
# 패키지 구조 정의
package.base=com.dx.identitybridge
package.domain=..domain..
package.application=..application..
package.adapter=..adapter..

# 아키텍처 규칙
rule.layer.dependency.check=true
rule.hexagonal.domain.strict=true
rule.cycles.check=true
rule.naming.convention.enforce=true

# 허용 오차
tolerance.layer.violations=0
tolerance.cycle.violations=0
tolerance.domain.dependencies=0
```

### Checkstyle 규칙 커스터마이징
프로젝트 루트에 `config/static/checkstyle/checkstyle-custom.xml` 파일을 수정하여 코딩 스타일 규칙을 조정할 수 있습니다.

### AI 분석 가이드 커스터마이징
`config/ai/gemini-guide.md` 파일을 편집하여 AI 분석 기준을 프로젝트에 맞게 조정할 수 있습니다.

## 🔧 고급 사용법

### 커스텀 설정으로 실행
```gradle
task qualityCheckCustom(type: JavaExec) {
    dependsOn compileJava, ':qa:jar'
    mainClass = 'com.identitybridge.qa.QualityAnalyzer'
    classpath = project(':qa').sourceSets.main.runtimeClasspath
    
    // 커스텀 설정 파일 지정
    args = [
        projectDir.toString(),
        "${buildDir}/reports/quality-custom".toString(),
        "custom-qa.properties"
    ]
}
```

### 프로그래밍 방식 사용
```java
import com.identitybridge.qa.QualityAnalyzer;
import com.identitybridge.qa.config.QaConfiguration;

// 기본 설정으로 실행
QaConfiguration config = QaConfiguration.defaultConfig();
QualityReport report = QualityAnalyzer.analyze(
    projectDir.toFile(),
    outputDir.toFile(),
    config
);

// 커스텀 설정으로 실행
QaConfiguration customConfig = QaConfiguration.fromFile(
    new File("custom-qa.properties")
);
QualityReport report = QualityAnalyzer.analyze(
    projectDir.toFile(),
    outputDir.toFile(),
    customConfig
);
```

## 🧪 CI/CD 통합

### GitHub Actions
```yaml
name: Quality Check

on: [push, pull_request]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Initialize QA
      run: ./gradlew qaInit
      
    - name: Run Quality Check
      run: ./gradlew qualityCheck
      
    - name: Upload Quality Reports
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: quality-reports
        path: build/reports/quality/
```

### Jenkins Pipeline
```groovy
pipeline {
    agent any
    stages {
        stage('Quality Check') {
            steps {
                sh './gradlew qaInit'
                sh './gradlew qualityCheck'
            }
            post {
                always {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'build/reports/quality',
                        reportFiles: 'quality-report.html',
                        reportName: 'Quality Report'
                    ])
                }
            }
        }
    }
}
```

## 🔍 지원하는 분석 도구

| 도구 | 목적 | 버전 | 설정 파일 | HTML 리포트 |
|------|------|------|-----------|-------------|
| **Checkstyle** | 코딩 스타일 검사 | 10.12.0 | `config/static/checkstyle/` | 통합 리포트 |
| **PMD** | 정적 코드 분석 | 6.55.0 | `config/static/pmd/` | 통합 리포트 |
| **SpotBugs** | 버그 패턴 검출 | 4.7.3 | `config/static/spotbugs/` | 통합 리포트 |
| **JaCoCo** | 테스트 커버리지 | - | 자동 생성 | 통합 리포트 |
| **ArchUnit** | 아키텍처 검증 | 1.2.1 | `config/archunit/` | ✅ `archunitTest/index.html` |
| **Gemini CLI** | AI 기반 분석 | 최신 | `config/ai/` | 통합 리포트 |

## 🚨 문제 해결

### 일반적인 문제들

**Q: `./gradlew qaInit` 실행 시 파일이 생성되지 않음**
```bash
# QA 모듈이 올바르게 빌드되었는지 확인
./gradlew :qa:build

# 명시적으로 overwrite 옵션 사용
./gradlew qaInit -Poverwrite=true
```

**Q: ArchUnit 분석에서 클래스를 찾을 수 없음**
```properties
# config/archunit/archunit.properties에서 패키지 경로 확인
package.base=com.your.package.name
```

**Q: ArchUnit 규칙 위반사항을 상세히 보고 싶음**
```bash
# ArchUnit 전용 HTML 리포트 확인
./gradlew archunitTest
open build/reports/tests/archunitTest/index.html

# 또는 통합 품질 검사 후 확인  
./gradlew qualityCheck
open build/reports/tests/archunitTest/index.html
```

**Q: Gemini AI 분석이 건너뛰어짐**
```bash
# Gemini CLI 설치 확인
which gemini

# 수동으로 Gemini 비활성화
echo "qa.ai.gemini.enabled=false" >> config/qa.properties
```

**Q: PMD 분석에서 너무 많은 위반사항**
```bash
# PMD 규칙 완화 또는 제외 설정
# config/static/pmd/pmd-custom-rules.xml 편집
```

### 로그 및 디버깅
```bash
# 상세 로그와 함께 실행
./gradlew qualityCheck --info

# 스택 트레이스 포함
./gradlew qualityCheck --stacktrace

# 디버그 모드
./gradlew qualityCheck --debug
```

## 📈 성능 최적화

### 대용량 프로젝트
```properties
# config/qa.properties
qa.parallel.execution=true
qa.max.violations.per.rule=100
qa.cache.enabled=true
```

### 선택적 분석 실행
```properties
# 특정 도구만 활성화
qa.static.checkstyle.enabled=true
qa.static.pmd.enabled=false
qa.static.spotbugs.enabled=false
```

## 📝 라이선스

Apache License 2.0

## 🤝 기여하기

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📞 지원

- **이슈**: GitHub Issues
- **문서**: [프로젝트 위키](link-to-wiki)
- **이메일**: support@identitybridge.com