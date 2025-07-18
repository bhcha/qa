# QA Module 사용 가이드

## 📋 개요
이 문서는 IdentityBridge QA 모듈의 사용 방법과 설정을 안내합니다.

## 🚀 빠른 시작

### 1. 기본 설정 파일 생성
```bash
./gradlew qaInit
```

### 2. 품질 검사 실행
```bash
./gradlew qualityCheck
```

### 3. 리포트 확인
- HTML 리포트: `build/reports/quality/quality-report.html`
- JSON 리포트: `build/reports/quality/quality-report.json`

## ⚙️ 설정 파일

### config/qa.properties
메인 설정 파일로, 다음과 같은 항목들을 설정할 수 있습니다:

```properties
# 일반 설정
qa.ignoreFailures=false                    # 실패 시 빌드 중단 여부
qa.skipUnavailableAnalyzers=true          # 사용 불가능한 분석기 건너뛰기

# 정적 분석 도구 활성화/비활성화
qa.static.checkstyle.enabled=true
qa.static.pmd.enabled=true
qa.static.spotbugs.enabled=true
qa.static.jacoco.enabled=true
qa.static.sonarqube.enabled=false

# AI 분석 도구
qa.ai.gemini.enabled=true
```

### 정적 분석 설정 파일들
- `config/static/checkstyle/checkstyle.xml`: Checkstyle 규칙
- `config/static/pmd/ruleset.xml`: PMD 규칙  
- `config/static/spotbugs/exclude.xml`: SpotBugs 제외 규칙
- `config/ai/gemini-guide.md`: Gemini AI 분석 가이드

## 🔧 커스터마이징

### 1. 품질 기준 조정
```properties
qa.quality.coverage.instruction.minimum=80
qa.quality.coverage.branch.minimum=70
qa.quality.violations.checkstyle.maximum=50
qa.quality.violations.pmd.maximum=20
qa.quality.violations.spotbugs.maximum=10
```

### 2. 분석 대상 조정
```properties
qa.analysis.sourceDirectory=src/main/java
qa.analysis.testDirectory=src/test/java
qa.analysis.excludePatterns=**/*Test.java,**/*Config.java
```

### 3. 리포트 형식 선택
```properties
qa.reports.html.enabled=true
qa.reports.json.enabled=true
```

## 🎯 Gradle 태스크

### qualityCheck
전체 품질 분석을 실행합니다.
```bash
./gradlew qualityCheck
```

### qaInit
기본 설정 파일들을 프로젝트에 복사합니다.
```bash
./gradlew qaInit
```

### qaInit --overwrite
기존 설정 파일들을 덮어씁니다.
```bash
./gradlew qaInit --overwrite
```

## 📊 품질 메트릭

### 커버리지 기준
- **Instruction Coverage**: 80% 이상
- **Branch Coverage**: 70% 이상
- **Line Coverage**: 80% 이상

### 정적 분석 기준
- **Checkstyle 위반**: 50개 이하
- **PMD 위반**: 20개 이하
- **SpotBugs 위반**: 10개 이하

## 🔍 문제 해결

### 1. 설정 파일을 찾을 수 없는 경우
```bash
./gradlew qaInit
```

### 2. 특정 분석기를 비활성화하려는 경우
`config/qa.properties`에서 해당 분석기를 false로 설정:
```properties
qa.static.checkstyle.enabled=false
```

### 3. 커스텀 규칙 적용
해당 설정 파일을 직접 수정하거나 경로를 변경:
```properties
qa.static.checkstyle.configPath=custom/checkstyle.xml
```

## 🚀 CI/CD 통합

### GitHub Actions 예시
```yaml
- name: Quality Check
  run: ./gradlew qualityCheck
  
- name: Upload Quality Report
  uses: actions/upload-artifact@v3
  with:
    name: quality-report
    path: build/reports/quality/
```

### Jenkins 예시
```groovy
stage('Quality Check') {
    steps {
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
```

## 📞 지원

문제가 발생하거나 기능 요청이 있으시면:
1. 프로젝트 Issues 페이지 확인
2. 설정 파일 재초기화 시도
3. 로그 파일 확인

---

더 자세한 내용은 [Quality Standards](quality-standards.md)를 참조하세요.