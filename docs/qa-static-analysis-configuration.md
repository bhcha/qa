# QA 모듈 정적 분석 도구 설정 가이드

## 개요

QA 모듈은 Java 프로젝트의 코드 품질을 분석하기 위해 여러 정적 분석 도구를 통합 제공합니다. 각 도구는 세밀한 설정을 통해 프로젝트 특성에 맞게 커스터마이징할 수 있습니다.

## 지원하는 정적 분석 도구

- **Checkstyle**: 코딩 스타일 및 컨벤션 검사
- **PMD**: 코드 품질 및 잠재적 버그 검출
- **SpotBugs**: 바이트코드 레벨 버그 패턴 분석
- **JaCoCo**: 테스트 코드 커버리지 측정
- **ArchUnit**: 아키텍처 규칙 및 의존성 검증

---

## 1. 전체 설정 (qa.properties)

### 설정 파일 위치
```
프로젝트루트/config/qa.properties
```

### 주요 설정 항목

#### 기본 설정
```properties
# 전체 동작 설정
qa.ignoreFailures=true                    # 분석 실패 시 빌드 계속 진행
qa.skipUnavailableAnalyzers=true          # 사용불가 분석기 자동 스킵

# 출력 설정
qa.report.html.enabled=true               # HTML 리포트 생성
qa.report.json.enabled=true               # JSON 리포트 생성
```

#### 분석기 활성화/비활성화
```properties
# 정적 분석 도구
qa.static.checkstyle.enabled=true         # Checkstyle 활성화
qa.static.pmd.enabled=true                # PMD 활성화
qa.static.spotbugs.enabled=true           # SpotBugs 활성화
qa.static.jacoco.enabled=true             # JaCoCo 활성화
qa.static.archUnit.enabled=false          # ArchUnit 비활성화

# AI 분석 (선택사항)
qa.ai.enabled=false                       # AI 분석 비활성화
qa.ai.gemini.enabled=false                # Gemini 분석 비활성화
```

#### 베이스 패키지 설정
```properties
# 아키텍처 분석용 베이스 패키지 (자동 감지 또는 수동 설정)
qa.static.archunit.basePackage=com.dx.identitybridge
```

---

## 2. Checkstyle 설정

### 설정 파일 구조
```
config/static/checkstyle/
├── checkstyle-custom.xml           # 커스텀 규칙 (우선순위 높음)
├── checkstyle.xml                  # 표준 규칙
└── checkstyle-suppressions.xml     # 예외 규칙
```

### 주요 규칙 설정

#### 코드 스타일 규칙
```xml
<!-- 라인 길이 제한 -->
<module name="LineLength">
    <property name="max" value="100"/>
    <property name="ignorePattern" value="^package.*|^import.*|a href|href|http://|https://|ftp://"/>
</module>

<!-- 메서드 길이 제한 -->
<module name="MethodLength">
    <property name="max" value="150"/>
    <property name="countEmpty" value="false"/>
</module>

<!-- 파일 길이 제한 -->
<module name="FileLength">
    <property name="max" value="2000"/>
</module>
```

#### 복잡도 규칙
```xml
<!-- 순환 복잡도 -->
<module name="CyclomaticComplexity">
    <property name="max" value="12"/>
</module>

<!-- NPath 복잡도 -->
<module name="NPathComplexity">
    <property name="max" value="200"/>
</module>
```

#### 헥사고날 아키텍처 규칙
```xml
<!-- 패키지 명명 규칙 -->
<module name="PackageName">
    <property name="format" value="^[a-z]+(\.[a-z][a-z0-9]*)*$"/>
    <message key="name.invalidPattern"
             value="패키지명은 소문자로 시작해야 하며, 헥사고날 아키텍처 구조를 따라야 합니다: {0}, pattern {1}"/>
</module>
```

### 예외 설정 (Suppressions)
```xml
<!-- 테스트 파일 예외 -->
<suppress files=".*Test\.java" checks="MagicNumber"/>
<suppress files=".*Test\.java" checks="MethodLength"/>

<!-- DTO 클래스 예외 -->
<suppress files=".*Dto\.java" checks="VisibilityModifier"/>
<suppress files=".*Request\.java" checks="VisibilityModifier"/>
<suppress files=".*Response\.java" checks="VisibilityModifier"/>

<!-- 설정 클래스 예외 -->
<suppress files=".*Config\.java" checks="HideUtilityClassConstructor"/>
```

---

## 3. PMD 설정

### 설정 파일 위치
```
config/static/pmd/
├── pmd-custom-rules.xml            # 커스텀 규칙
└── pmd-ruleset.xml                 # 표준 규칙
```

### 주요 규칙 설정

#### 복잡도 제한
```xml
<!-- 메서드당 최대 개수 -->
<rule ref="category/design/TooManyMethods">
    <properties>
        <property name="maxmethods" value="15"/>
    </properties>
</rule>

<!-- 파일당 최대 라인 수 -->
<rule ref="category/design/ExcessiveClassLength">
    <properties>
        <property name="minimum" value="1500"/>
    </properties>
</rule>
```

#### 보안 규칙 (인증 도메인 특화)
```xml
<!-- 패스워드 하드코딩 금지 -->
<rule ref="category/security/HardCodedCryptoKey"/>

<!-- SQL 인젝션 방지 -->
<rule ref="category/security/DetachedTestCase"/>
```

#### 헥사고날 아키텍처 규칙
```xml
<!-- 커스텀 XPath 규칙: 어댑터가 도메인 직접 접근 금지 -->
<rule name="AdapterShouldNotAccessDomainDirectly"
      language="java"
      message="어댑터는 도메인을 직접 접근하면 안됩니다. 포트를 통해 접근하세요."
      class="net.sourceforge.pmd.lang.rule.XPathRule">
    <description>헥사고날 아키텍처에서 어댑터는 도메인을 직접 접근하면 안됩니다.</description>
    <priority>1</priority>
    <properties>
        <property name="xpath">
            <value><![CDATA[
//ImportDeclaration[contains(@Image, '.domain.')]
[ancestor::CompilationUnit[contains(@Image, '.adapter.')]]
            ]]></value>
        </property>
    </properties>
</rule>
```

#### 변수명 규칙 완화
```xml
<!-- 변수명 길이 -->
<rule ref="category/codestyle/ShortVariable">
    <properties>
        <property name="minimum" value="2"/>
    </properties>
</rule>

<rule ref="category/codestyle/LongVariable">
    <properties>
        <property name="minimum" value="25"/>
    </properties>
</rule>
```

---

## 4. SpotBugs 설정

### 설정 파일 위치
```
config/static/spotbugs/
├── spotbugs-exclude.xml            # 제외 규칙
└── exclude.xml                     # 대안 제외 규칙
```

### 주요 제외 규칙

#### Spring Boot 특화 제외
```xml
<!-- Spring 설정 클래스 필드 경고 제외 -->
<Match>
    <Class name="~.*Config$"/>
    <Bug pattern="UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"/>
</Match>

<!-- Spring Boot Application 클래스 -->
<Match>
    <Class name="~.*Application$"/>
    <Bug pattern="SIC_INNER_SHOULD_BE_STATIC_ANON"/>
</Match>
```

#### DTO 직렬화 관련 제외
```xml
<!-- Serialization 경고 제외 -->
<Match>
    <Class name="~.*Dto$"/>
    <Bug pattern="SE_NO_SERIALVERSIONID"/>
</Match>

<Match>
    <Class name="~.*Request$"/>
    <Bug pattern="SE_NO_SERIALVERSIONID"/>
</Match>

<Match>
    <Class name="~.*Response$"/>
    <Bug pattern="SE_NO_SERIALVERSIONID"/>
</Match>
```

#### 의존성 주입 패턴 제외
```xml
<!-- Field injection 경고 제외 -->
<Match>
    <Class name="~.*Service$"/>
    <Bug pattern="UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"/>
</Match>

<!-- Repository injection 경고 제외 -->
<Match>
    <Class name="~.*Repository$"/>
    <Bug pattern="UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"/>
</Match>
```

#### 테스트 클래스 제외
```xml
<!-- 테스트 파일 전체 제외 -->
<Match>
    <Class name="~.*Test$"/>
</Match>

<Match>
    <Class name="~.*Tests$"/>
</Match>
```

---

## 5. ArchUnit 설정

### 설정 파일 위치
```
config/archunit/archunit.properties
```

### 주요 설정 항목

#### 기본 설정
```properties
# 베이스 패키지
archunit.basePackage=com.dx.identitybridge

# 실패 시 동작
fail.on.empty.should=true

# 허용 위반 수
archunit.naming.tolerance=5
```

#### 아키텍처 규칙 활성화
```properties
# 헥사고날 아키텍처 규칙
archunit.hexagonal.enabled=true
archunit.layering.enabled=true
archunit.cqrs.enabled=true

# 네이밍 컨벤션
archunit.naming.enabled=true
```

### 검증하는 아키텍처 규칙

#### 레이어 분리 규칙
- **Domain Layer**: 외부 의존성 없이 순수한 비즈니스 로직
- **Application Layer**: 도메인 서비스 조합 및 유스케이스 구현
- **Infrastructure Layer**: 외부 시스템과의 연동

#### 의존성 방향 규칙
- 외부 레이어에서 내부 레이어로만 의존 허용
- 도메인은 어떤 레이어에도 의존하지 않음
- 어댑터는 포트를 통해서만 도메인 접근

#### CQRS 패턴 규칙
- Command와 Query 명확한 분리
- Command는 상태 변경, Query는 조회만
- 각각 독립적인 처리 핸들러

---

## 6. JaCoCo 설정

### 자동 설정
JaCoCo는 `./gradlew qaInit` 실행 시 자동으로 플러그인이 추가됩니다.

**v1.1.0부터**: `qualityCheck` 태스크가 테스트를 자동 실행하므로 별도로 `test` 태스크를 실행할 필요가 없습니다.

### 커버리지 임계값
```properties
# 기본 임계값 (QA 모듈 내장)
jacoco.instruction.threshold=80.0      # Instruction Coverage 80% 미만 시 경고
jacoco.branch.threshold=70.0           # Branch Coverage 70% 미만 시 경고
```

### 생성되는 리포트
- **HTML 리포트**: `build/reports/jacoco/test/index.html`
- **XML 리포트**: `build/reports/jacoco/test/jacocoTestReport.xml`
- **CSV 리포트**: `build/reports/jacoco/test/jacocoTestReport.csv`

---

## 7. 설정 우선순위 시스템

### 우선순위 순서
1. **프로젝트별 설정** (`config/` 폴더) - **최우선**
2. **자동 감지** (build.gradle, @SpringBootApplication 등)
3. **기본값** (QA 모듈 내장)

### 자동 감지 메커니즘

#### 베이스 패키지 자동 감지
```java
// 1순위: Properties 파일 설정
qa.static.archunit.basePackage=com.example.myproject

// 2순위: build.gradle에서 감지
group = 'com.example.myproject'

// 3순위: 메인 클래스에서 감지
@SpringBootApplication
public class MyProjectApplication { ... }
```

---

## 8. 사용 방법

### 초기 설정
```bash
# QA 설정 파일 생성 및 JaCoCo 플러그인 자동 추가
./gradlew qaInit

# 기존 설정 파일 덮어쓰기
./gradlew qaInit -Poverwrite=true
```

### 품질 검사 실행
```bash
# 전체 품질 검사 (테스트 자동 실행, JaCoCo 포함)
./gradlew qualityCheck

# 캐시 클리어 후 품질 검사
./gradlew clean qualityCheck
```

### 리포트 확인
```bash
# HTML 통합 리포트
open build/reports/quality/quality-report.html

# 개별 도구 리포트
open build/reports/checkstyle/main.html
open build/reports/pmd/main.html
open build/reports/spotbugs/main.html
open build/reports/jacoco/test/index.html
```

---

## 9. 커스터마이징 가이드

### 새로운 프로젝트 설정
1. `./gradlew qaInit` 실행으로 기본 설정 생성
2. `config/qa.properties`에서 필요한 분석기 활성화/비활성화
3. 각 도구별 설정 파일을 프로젝트에 맞게 조정
4. `./gradlew qualityCheck`로 설정 검증 (테스트 자동 실행됨)

### 기존 프로젝트 통합
1. 기존 설정 파일이 있다면 백업
2. `./gradlew qaInit`로 QA 설정 구조 생성
3. 기존 설정을 새로운 구조에 맞게 이관
4. 단계적으로 규칙 적용 (ignoreFailures=true로 시작)

### 팀별 설정 표준화
1. 조직 표준 설정을 git repository로 관리
2. QA 모듈의 기본 설정을 조직 표준으로 교체
3. 프로젝트별 특화 설정은 최소화
4. CI/CD 파이프라인에 품질 게이트 통합

---

## 10. 문제 해결

### 일반적인 문제들

#### 설정 파일을 찾을 수 없음
```bash
# 해결방법: qaInit으로 설정 파일 재생성
./gradlew qaInit -Poverwrite=true
```

#### 특정 분석기가 실행되지 않음
```properties
# qa.properties에서 해당 분석기 활성화 확인
qa.static.checkstyle.enabled=true
```

#### JaCoCo 데이터가 생성되지 않음
```bash
# qualityCheck는 이제 테스트를 자동 실행합니다
./gradlew clean qualityCheck

# 기존 방식 (여전히 지원됨)
./gradlew clean test qualityCheck
```

#### 아키텍처 규칙 위반이 너무 많음
```properties
# 단계적 적용을 위해 임시 비활성화
qa.static.archUnit.enabled=false
```

### 성능 최적화
- 대규모 프로젝트에서는 병렬 실행 고려
- 불필요한 분석기 비활성화
- 제외 규칙을 통한 분석 범위 축소

---

## 결론

QA 모듈의 정적 분석 도구 설정은 프로젝트의 아키텍처와 팀의 코딩 스타일에 맞게 유연하게 구성할 수 있습니다. 초기에는 관대한 설정으로 시작하여 점진적으로 품질 기준을 높여가는 것을 권장합니다.

정기적인 설정 리뷰와 팀 피드백을 통해 지속적으로 개선하여 효과적인 코드 품질 관리 체계를 구축하시기 바랍니다.