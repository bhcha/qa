# 테스트 전략 가이드

## 🎯 테스트 목표
- **테스트 커버리지**: 전체 80% 이상, 핵심 비즈니스 로직 90% 이상
- **테스트 피라미드**: 단위(70%) > 통합(20%) > E2E(10%)
- **실행 시간**: 전체 테스트 5분 이내, 단위 테스트 1분 이내

## 📊 테스트 레벨별 전략

### 1. 단위 테스트 (Unit Test)
**목적**: 개별 클래스/메서드의 동작 검증

#### 도메인 계층 테스트
```java
@Test
@DisplayName("회원 가입 시 이메일은 필수값이다")
void registerWithoutEmailShouldThrowException() {
    // given
    String email = null;
    String nickname = "tester";
    String password = "password123";
    
    // when & then
    assertThrows(IllegalArgumentException.class, 
        () -> Member.register(email, nickname, password));
}
```

**검증 포인트**:
- 비즈니스 규칙 준수
- 불변식 유지
- 예외 처리
- 상태 변경 정확성

#### 애플리케이션 계층 테스트
```java
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock MemberRepository memberRepository;
    @Mock EmailSender emailSender;
    @InjectMocks MemberService memberService;
    
    @Test
    void 회원가입_성공() {
        // Mocking과 행위 검증
    }
}
```

**검증 포인트**:
- 워크플로우 정확성
- 포트 호출 검증
- 트랜잭션 경계
- 예외 전파

### 2. 통합 테스트 (Integration Test)
**목적**: 계층 간 상호작용 검증

#### 리포지토리 테스트
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MemberRepositoryTest {
    @Autowired MemberRepository repository;
    
    @Test
    void 이메일로_회원조회() {
        // DB 실제 연동 테스트
    }
}
```

#### API 통합 테스트
```java
@SpringBootTest
@AutoConfigureMockMvc
class MemberApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    
    @Test
    void 회원가입_API_호출() throws Exception {
        mockMvc.perform(post("/api/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "email": "test@example.com",
                    "nickname": "tester",
                    "password": "password123"
                }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberId").exists());
    }
}
```

### 3. 아키텍처 테스트
```java
@AnalyzeClasses(packages = "com.example")
class HexagonalArchitectureTest {
    @ArchTest
    static final ArchRule domainIndependence = 
        noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..application..", "..adapter..");
}
```

## 🔍 테스트 품질 검증

### 커버리지 분석
```bash
# JaCoCo 리포트 생성
./gradlew test jacocoTestReport

# 커버리지 확인
open build/qa/reports/jacoco/test/html/index.html
```

### 커버리지 기준
| 구분 | 목표 | 최소 |
|------|------|------|
| 전체 | 80% | 70% |
| 도메인 | 90% | 85% |
| 애플리케이션 | 85% | 75% |
| 어댑터 | 70% | 60% |

### 제외 대상
- DTO (단순 데이터 전달)
- 설정 클래스
- main 메서드
- 자동 생성 코드

## 🧪 테스트 작성 원칙

### 1. FIRST 원칙
- **Fast**: 빠른 실행
- **Independent**: 독립적 실행
- **Repeatable**: 반복 가능
- **Self-Validating**: 자체 검증
- **Timely**: 적시 작성 (TDD)

### 2. Given-When-Then 패턴
```java
@Test
void 테스트명_조건_결과() {
    // given - 테스트 전제 조건
    var input = createTestInput();
    
    // when - 테스트 대상 실행
    var result = sut.execute(input);
    
    // then - 결과 검증
    assertThat(result).isEqualTo(expected);
}
```

### 3. 테스트 명명 규칙
- 한글 사용 권장 (가독성)
- 메서드명_조건_예상결과
- @DisplayName으로 상세 설명

## 📈 테스트 메트릭

### 측정 지표
1. **커버리지**: Line, Branch, Method
2. **실행 시간**: 테스트별, 전체
3. **안정성**: Flaky 테스트 비율
4. **유지보수성**: 테스트 코드 복잡도

### 리포트 생성
```bash
# 테스트 실행 및 리포트
./gradlew test

# 상세 리포트 확인
cat build/qa/reports/tests/test/index.html
```

## 🚨 테스트 실패 대응

### 실패 분류
1. **비즈니스 로직 오류**: 즉시 수정
2. **테스트 오류**: 테스트 코드 수정
3. **환경 문제**: 환경 설정 확인
4. **Flaky 테스트**: 안정화 작업

### 대응 절차
1. 실패 원인 분석
2. 영향 범위 파악
3. 수정 및 재테스트
4. 회귀 테스트 추가

## ✅ 테스트 체크리스트

### 작성 시
- [ ] 테스트가 하나의 기능만 검증하는가?
- [ ] 테스트 이름이 명확한가?
- [ ] Given-When-Then 구조인가?
- [ ] 외부 의존성이 격리되었는가?

### 검증 시
- [ ] 모든 분기가 테스트되는가?
- [ ] 경계값 테스트가 있는가?
- [ ] 예외 케이스가 검증되는가?
- [ ] 성능이 적절한가?

## 🔄 지속적 개선

### 월간 분석
- 커버리지 트렌드
- 자주 실패하는 테스트
- 실행 시간 증가 추이
- 테스트 부채 관리

### 개선 활동
- 테스트 리팩토링
- 테스트 데이터 관리 개선
- 테스트 인프라 최적화
- 팀 교육 및 공유