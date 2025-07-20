# 정적 도구별 적용 규칙 문서

## 1. Checkstyle 규칙
- 스타일검사(강력)

### 기본 설정
- **문자 인코딩**: UTF-8
- **심각도**: warning
- **대상 파일**: *.java

### 적용 규칙 목록

#### 코드 스타일
- **LineLength**: 최대 120자 (패키지 및 import 문 제외)
- **FileTabCharacter**: 탭 문자 사용 금지

#### Import 관련
- **AvoidStarImport**: * import 금지
- **UnusedImports**: 사용하지 않는 import 제거
- **RedundantImport**: 중복 import 제거

#### 블록 구조
- **EmptyBlock**: 빈 블록에는 설명 필요
- **NeedBraces**: 모든 제어문에 중괄호 필수
- **LeftCurly**: 여는 중괄호 위치 규칙
- **RightCurly**: 닫는 중괄호 위치 규칙

#### 공백 처리
- **WhitespaceAfter**: 특정 토큰 뒤 공백 필수
- **WhitespaceAround**: 연산자 주변 공백 필수
- **NoWhitespaceBefore**: 특정 토큰 앞 공백 금지

#### 네이밍 규칙
- **PackageName**: 패키지명은 소문자
- **TypeName**: 클래스/인터페이스명은 UpperCamelCase
- **MethodName**: 메소드명은 lowerCamelCase
- **ConstantName**: 상수는 UPPER_SNAKE_CASE
- **LocalVariableName**: 지역변수는 lowerCamelCase

## 2. PMD 규칙
- 버그검출(기본), 복잡도 분석(강력), 스타일검사(기본)

### 기본 설정
- PMD 6.55.0 사용
- Saxon XPath 제외 (충돌 방지)

### 적용 규칙 목록

#### Best Practices
- 모든 규칙 적용, 단 다음 제외:
  - JUnitTestsShouldIncludeAssert
  - GuardLogStatement

#### Code Style
- 모든 규칙 적용, 단 다음 제외:
  - OnlyOneReturn
  - AtLeastOneConstructor
  - CallSuperInConstructor
  - CommentDefaultAccessModifier
  - DefaultPackage

#### Design
- 모든 규칙 적용, 단 다음 제외:
  - LawOfDemeter
  - UseUtilityClass
  - AvoidCatchingGenericException

#### Error Prone
- 모든 규칙 적용, 단 다음 제외:
  - BeanMembersShouldSerialize
  - DataflowAnomalyAnalysis
  - AvoidLiteralsInIfCondition

#### Performance
- 모든 규칙 적용

#### Security
- 모든 규칙 적용

## 3. SpotBugs 규칙
- 버그검출(강력)

### 기본 설정
- SpotBugs 4.7.3 사용
- 제외 규칙은 spotbugs-exclude.xml로 관리

### 적용 규칙 카테고리
- **CORRECTNESS**: 명확한 버그
- **BAD_PRACTICE**: 나쁜 프로그래밍 관습
- **STYLE**: 코드 스타일 문제
- **PERFORMANCE**: 성능 문제
- **SECURITY**: 보안 취약점
- **MALICIOUS_CODE**: 악의적 코드 취약점
- **MT_CORRECTNESS**: 멀티스레드 정확성

## 4. JaCoCo 규칙
- 커버리지

### 기본 설정
- 코드 커버리지 측정 도구
- 커버리지 기준값 설정 가능

### 측정 지표
- **Instruction Coverage**: 명령어 커버리지 (기본 80% 이상)
- **Branch Coverage**: 분기 커버리지 (기본 70% 이상)
- **Line Coverage**: 라인 커버리지
- **Method Coverage**: 메소드 커버리지
- **Class Coverage**: 클래스 커버리지

## 5. ArchUnit 규칙
- 아키텍처

### 기본 설정
- 아키텍처 규칙 검증 도구
- basePackage 자동 감지 또는 설정 가능

### 적용 가능한 규칙 예시
- 패키지 의존성 규칙
- 레이어 아키텍처 검증
- 순환 참조 금지
- 네이밍 규칙 검증
- 어노테이션 사용 규칙

## 6. Kingfisher 규칙

### 기본 설정
- Kingfisher v1.18+ 사용
- Rust 기반 고성능 비밀 스캐너
- 실시간 자격 증명 검증 지원

### 탐지 카테고리
- **클라우드 프로바이더**: AWS, Azure, GCP 자격 증명
- **버전 관리**: GitHub, GitLab, Bitbucket 토큰
- **데이터베이스**: Connection strings, passwords
- **API 서비스**: 다양한 SaaS 서비스 API 키
- **인증 토큰**: JWT, OAuth, Bearer 토큰
- **암호화 키**: SSH keys, TLS certificates

### 신뢰도 레벨
- **low**: 모든 잠재적 비밀 (오탐 가능)
- **medium**: 균형잡힌 탐지 (기본값)
- **high**: 확실한 비밀만 탐지

### 검증 기능
- 탐지된 자격 증명의 실제 유효성 검증
- 활성/비활성 비밀 구분
- 검증 타임아웃: 5초

### 커스텀 규칙 예시
```yaml
rules:
  - id: internal-api-key
    pattern: 'internal[_-]?api[_-]?key\s*[:=]\s*["\']?([a-zA-Z0-9]{32,})'
    severity: high
    tags: [internal, api-key]
```

## 설정 우선순위

1. **프로젝트별 커스텀 설정** (최우선)
   - `config/static/checkstyle/checkstyle-custom.xml`
   - `config/static/pmd/pmd-custom-rules.xml`
   - `config/static/spotbugs/spotbugs-exclude.xml`

2. **QA 모듈 기본 설정** (차선)
   - 의존성 프로젝트의 설정 파일 자동 감지
   - `src/main/resources/default-configs/` 내 기본 설정

3. **도구 기본값** (최후)
   - 각 정적 분석 도구의 기본 규칙

## 규칙 커스터마이징 방법

### 1. Checkstyle 규칙 수정
```xml
<!-- config/static/checkstyle/checkstyle-custom.xml -->
<module name="LineLength">
    <property name="max" value="150"/> <!-- 기본값 120에서 변경 -->
</module>
```

### 2. PMD 규칙 추가/제외
```xml
<!-- config/static/pmd/pmd-custom-rules.xml -->
<rule ref="category/java/bestpractices.xml">
    <exclude name="AdditionalRuleToExclude"/>
</rule>
```

### 3. SpotBugs 특정 경고 제외
```xml
<!-- config/static/spotbugs/spotbugs-exclude.xml -->
<FindBugsFilter>
    <Match>
        <Bug pattern="DM_CONVERT_CASE"/>
    </Match>
</FindBugsFilter>
```

### 4. 프로퍼티 파일로 전체 설정
```properties
# config/qa.properties
qa.static.checkstyle.enabled=true
qa.static.pmd.enabled=true
qa.static.spotbugs.enabled=false  # SpotBugs 비활성화
qa.quality.violations.checkstyle.maximum=100  # 허용 위반 수 조정
```



## 참고사항

- 모든 정적 도구는 개별적으로 활성화/비활성화 가능
- 각 도구별 상세 HTML/XML 리포트 생성
- 통합 리포트에서 각 도구별 결과 확인 가능
- CI/CD 파이프라인 통합 지원
- Kingfisher는 별도 바이너리 설치 필요