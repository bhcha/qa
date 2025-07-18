# TDD 보완 계획

## 🎯 목적
Kent Beck의 TDD와 Tidy First 원칙에 따른 체계적인 테스트 개발 강화

## 📋 현재 TDD 성숙도 평가

### ✅ 잘 구현된 부분
- **Red-Green-Refactor 사이클**: 도메인 모델에서 잘 적용됨
- **테스트 우선 개발**: Authentication 도메인 완성도 높음
- **작은 단위 커밋**: 구조 변경과 기능 변경 분리
- **의미있는 테스트명**: 한글로 명확한 의도 표현

### ⚠️ 개선 필요 영역
- **Mock 과다 사용**: 일부 테스트에서 불필요한 Mock
- **통합 테스트 의존성**: Bean 중복 문제로 제거됨
- **엣지 케이스 부족**: 실패 시나리오 테스트 미흡
- **리팩토링 빈도**: 구조 개선이 기능 추가와 섞임

## 🔄 TDD 사이클 강화 계획

### 1. RED (실패하는 테스트) 개선

#### 현재 문제점
```java
// 너무 많은 기능을 한번에 테스트
@Test
void shouldAuthenticateUserWithValidCredentials() {
    // Given: 복잡한 설정
    // When: 여러 단계 실행
    // Then: 여러 검증 항목
}
```

#### 개선 방향
```java
// 하나의 작은 행동만 테스트
@Test
void 유효한_이메일로_사용자를_찾을_수_있어야_한다() {
    // Given: 최소한의 설정
    String validEmail = "user@example.com";
    
    // When: 단일 행동
    User user = userRepository.findByEmail(validEmail);
    
    // Then: 단일 검증
    assertThat(user).isNotNull();
}
```

### 2. GREEN (최소 구현) 개선

#### Tidy First 원칙 적용
1. **구조 변경 먼저**: 기능 추가 전 코드 정리
2. **최소 구현**: 테스트를 통과하는 가장 간단한 코드
3. **점진적 개선**: 한 번에 하나씩만 변경

#### 예시 워크플로우
```bash
# 1. 구조 정리 (Structural Change)
git commit -m "refactor: 메서드 이름 명확화 - extractUserCredentials"

# 2. 기능 추가 (Behavioral Change)  
git commit -m "feat: 이메일 검증 로직 추가 - validateEmailFormat"

# 3. 추가 구조 정리
git commit -m "refactor: 중복 코드 제거 - 공통 검증 메서드 추출"
```

### 3. REFACTOR (구조 개선) 체계화

#### 리팩토링 체크리스트
- [ ] **중복 제거**: 동일한 로직 통합
- [ ] **의미 명확화**: 변수, 메서드명 개선
- [ ] **복잡도 감소**: 긴 메서드 분할
- [ ] **의존성 명시화**: 인터페이스 활용
- [ ] **테스트 검증**: 리팩토링 후 모든 테스트 통과

## 📝 TDD 실천 가이드

### 개발 프로세스

#### 1단계: 테스트 계획 수립
```markdown
## 기능: 사용자 인증
### 테스트 시나리오:
1. [ ] 유효한 이메일 형식 검증
2. [ ] 비밀번호 길이 검증  
3. [ ] 존재하지 않는 사용자 처리
4. [ ] 잘못된 비밀번호 처리
5. [ ] 계정 잠금 처리
```

#### 2단계: Red-Green-Refactor 순환
```java
// RED: 실패하는 테스트
@Test
void 이메일이_null이면_예외를_발생시켜야_한다() {
    assertThrows(IllegalArgumentException.class, 
        () -> new Email(null));
}

// GREEN: 최소 구현
public Email(String value) {
    if (value == null) {
        throw new IllegalArgumentException();
    }
    this.value = value;
}

// REFACTOR: 의미 명확화 (별도 커밋)
public Email(String value) {
    validateNotNull(value);
    this.value = value;
}

private void validateNotNull(String email) {
    if (email == null) {
        throw new IllegalArgumentException("이메일은 null일 수 없습니다");
    }
}
```

#### 3단계: 점진적 확장
- 한 번에 하나의 테스트만 추가
- 각 테스트마다 최소 구현
- 2-3개 테스트 후 리팩토링

### Mock 사용 가이드

#### 🟢 Mock 사용이 적절한 경우
```java
@Test
void 외부_API_호출_실패시_재시도해야_한다() {
    // Given: 외부 시스템 Mock
    when(externalApiClient.call()).thenThrow(new NetworkException());
    
    // When & Then: 재시도 로직 검증
    assertThat(service.processWithRetry()).isTrue();
    verify(externalApiClient, times(3)).call();
}
```

#### 🔴 Mock 사용을 피해야 하는 경우
```java
// 나쁜 예: 도메인 객체를 Mock
@Test
void shouldCalculatePrice() {
    Product mockProduct = mock(Product.class);
    when(mockProduct.getPrice()).thenReturn(BigDecimal.valueOf(100));
    // 실제 Product 객체를 사용해야 함
}

// 좋은 예: 실제 객체 사용
@Test
void 상품_가격을_올바르게_계산해야_한다() {
    Product product = Product.builder()
        .name("테스트 상품")
        .price(BigDecimal.valueOf(100))
        .build();
        
    assertThat(product.getPrice()).isEqualTo(BigDecimal.valueOf(100));
}
```

## 📊 TDD 품질 측정

### 테스트 품질 지표
1. **테스트 커버리지**: 80% 이상 (명령어 기준)
2. **순환 복잡도**: 10 이하 (메서드별)
3. **테스트 실행 속도**: 30초 이내 (전체)
4. **테스트 안정성**: 100% 성공률

### 코드 품질 지표
1. **중복도**: SonarQube 3% 이하
2. **기술 부채**: A 등급 유지
3. **코드 냄새**: 0개 목표
4. **보안 취약점**: 0개 유지

## 🎓 TDD 학습 계획

### 1주차: 기본 사이클 습득
- [ ] Red-Green-Refactor 10회 반복
- [ ] 작은 단위 테스트 작성 연습
- [ ] 커밋 메시지 규칙 정착

### 2주차: Mock과 Stub 활용
- [ ] 외부 의존성 테스트 방법
- [ ] Mock 최소화 기법
- [ ] Test Double 패턴 학습

### 3주차: 리팩토링 기법
- [ ] Tidy First 적용
- [ ] 코드 냄새 제거
- [ ] 구조와 행동 분리

### 4주차: 고급 TDD 패턴
- [ ] 테스트 피라미드 구축
- [ ] 통합 테스트 전략
- [ ] 성능 테스트 통합

## ⚡ 즉시 적용 가능한 TDD 개선사항

### 1. 테스트 명명 규칙 통일
```java
// Before: 영어 + 애매한 의미
@Test
void testUserLogin() { }

// After: 한글 + 명확한 의도
@Test  
void 유효한_자격증명으로_로그인하면_토큰을_반환해야_한다() { }
```

### 2. Given-When-Then 구조 강제
```java
@Test
void 잘못된_비밀번호로_로그인하면_예외를_발생시켜야_한다() {
    // Given
    String email = "user@example.com";
    String wrongPassword = "wrongpassword";
    
    // When & Then
    assertThrows(AuthenticationException.class, 
        () -> authService.login(email, wrongPassword));
}
```

### 3. 테스트 데이터 빌더 패턴
```java
public class UserTestDataBuilder {
    public static User.Builder validUser() {
        return User.builder()
            .email("test@example.com")
            .password("validpassword123")
            .name("테스트 사용자");
    }
}

// 사용법
@Test
void 유효한_사용자_정보로_회원가입할_수_있어야_한다() {
    User user = UserTestDataBuilder.validUser().build();
    assertThat(user.isValid()).isTrue();
}
```

## 🚀 다음 단계

1. **adapter.shared 패키지**: TDD로 테스트 커버리지 11% → 70%
2. **실패 시나리오 보강**: 각 도메인별 엣지 케이스 추가
3. **성능 테스트**: 느린 테스트 최적화
4. **문서화**: 모든 TDD 사이클 기록

## ✅ 성공 기준

- [ ] 모든 새 기능을 TDD로 개발
- [ ] 리팩토링과 기능 추가 분리
- [ ] 테스트 커버리지 80% 달성
- [ ] 100% 테스트 성공률 유지
- [ ] 개발 속도 향상 (작은 단위 반복)