# 개발보안 지침

## 📋 개요
프로젝트의 보안 코딩 표준과 실무 적용 가이드입니다. 이 문서는 개발 과정에서 반드시 준수해야 할 보안 요구사항과 구현 방법을 제시합니다.

## 🛡️ 보안 원칙
1. **Defense in Depth**: 다층 보안 방어체계 구축
2. **Zero Trust**: 모든 요청은 검증 후 처리
3. **Least Privilege**: 최소 권한 원칙 적용
4. **Fail Secure**: 실패 시 안전한 상태로 복구
5. **Security by Design**: 설계 단계부터 보안 고려

---

## 🔒 1. 인증 및 인가 (Authentication & Authorization)

### 1.1 JWT 토큰 보안 (W13, W16, W18, W19 대응)

#### ✅ 구현 완료 상태
- **파일**: `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`
- **Keycloak 기반 JWT 검증**: RS256 알고리즘 사용
- **토큰 서명 검증**: JWK Set URI를 통한 공개키 검증
- **만료 시간 체크**: 자동 만료 처리
- **폴백 메커니즘**: JWK 엔드포인트 실패 시 수동 검증

#### 📋 개발 지침
```java
// ✅ 올바른 JWT 검증 방식
@Component
public class JwtTokenProvider {
    // 1. 토큰 서명 검증
    public boolean validateToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return !isTokenExpired(jwt);
        } catch (JwtException e) {
            return false;
        }
    }
    
    // 2. 만료 시간 체크
    private boolean isTokenExpired(Jwt jwt) {
        return jwt.getExpiresAt() != null && 
               jwt.getExpiresAt().isBefore(Instant.now());
    }
}

// ❌ 잘못된 방식 - 토큰 검증 누락
// return true; // 절대 하지 마세요!
```

### 1.2 인가 제어 (W17 대응)

#### ✅ 구현 완료 상태
- **파일**: `SecurityConfig.java`
- **Bearer 토큰 필수**: 모든 API 엔드포인트 (인증 제외)
- **역할 기반 접근 제어**: Spring Security 적용
- **Stateless 세션**: 세션 고정 공격 방지

#### 📋 개발 지침
```java
// ✅ 올바른 보안 설정
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v1/auth/**", "/docs/**").permitAll()
                .anyRequest().authenticated())
            .build();
    }
}
```

---

## 🔍 2. 입력 검증 및 인젝션 방어

### 2.1 SQL 인젝션 방어 (W5 대응)

#### ✅ 구현 완료 상태
- **파일**: `UserRepository.java`, `OrganizationRepository.java`
- **JPA 파라미터 바인딩**: @Query + @Param 사용
- **Spring Data JPA**: 자동 쿼리 생성으로 안전한 쿼리 보장

#### 📋 개발 지침
```java
// ✅ 올바른 방식 - 파라미터 바인딩
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.companyId = :companyId")
    Page<User> findByStatusAndCompanyId(@Param("status") String status, 
                                       @Param("companyId") String companyId, 
                                       Pageable pageable);
}

// ❌ 잘못된 방식 - 문자열 연결
// String sql = "SELECT * FROM users WHERE status = '" + status + "'";
```

### 2.2 XSS 방어 (W11 대응)

#### ✅ 구현 완료 상태
- **파일**: `SecurityHeadersFilter.java`
- **CSP 헤더**: default-src 'self' 정책 적용
- **X-XSS-Protection**: 브라우저 XSS 필터 활성화
- **Content-Type 고정**: application/json으로 고정

#### 📋 개발 지침
```java
// ✅ 올바른 보안 헤더 설정
@Component
public class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // CSP 헤더 설정
        httpResponse.setHeader("Content-Security-Policy", "default-src 'self'");
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        chain.doFilter(request, response);
    }
}
```

### 2.3 입력값 검증 (W1, W2 대응)

#### ✅ 구현 완료 상태
- **파일**: `@ValidEmployeeId`, `@ValidOrganizationId`, `@ValidUserStatus`
- **커스텀 검증**: 도메인 특화 검증 규칙
- **Bean Validation**: JSR-303 표준 사용
- **다국어 에러 메시지**: 한국어/영어 지원

#### 📋 개발 지침
```java
// ✅ 올바른 입력값 검증
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidEmployeeIdValidator.class)
public @interface ValidEmployeeId {
    String message() default "직원 ID는 7자리 숫자여야 합니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Controller에서 사용
@GetMapping("/users/{employeeId}")
public ResponseEntity<UserResponse> getUser(
    @PathVariable @ValidEmployeeId String employeeId) {
    // 검증된 입력값 사용
}
```

---

## 🌐 3. 통신 보안

### 3.1 HTTPS 강제 적용 (W27 대응)

#### ✅ 구현 완료 상태
- **파일**: `SecurityConfig.java`
- **HSTS 헤더**: max-age=31536000 (1년)
- **includeSubdomains**: 하위 도메인 포함
- **Secure 쿠키**: HTTPS에서만 전송

#### 📋 개발 지침
```java
// ✅ 올바른 HSTS 설정
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .requiresChannel(channel -> 
                channel.requestMatchers(r -> r.getHeader("X-Forwarded-Proto") != null)
                       .requiresSecure())
            .headers(headers -> headers
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)))
            .build();
    }
}
```

### 3.2 CORS 보안 설정 (W15 대응)

#### ✅ 구현 완료 상태
- **파일**: `CorsConfig.java`
- **허용 도메인 제한**: 내부 시스템만 허용
- **메서드 제한**: 필요한 HTTP 메서드만 허용
- **인증 정보 전송**: credentials 처리

#### 📋 개발 지침
```java
// ✅ 올바른 CORS 설정
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "https://admin.company.com",
            "https://internal.company.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowCredentials(true);
        return source;
    }
}
```

---

## 🚫 4. 접근 제어 및 Rate Limiting

### 4.1 IP 기반 접근 제어 (W24, W25 대응)

#### ✅ 구현 완료 상태
- **파일**: `IpRestrictionFilter.java`, `IpRestrictionConfig.java`
- **CIDR 표기법**: IP 대역 설정 지원
- **Proxy 인식**: X-Forwarded-For 헤더 처리
- **IPv4/IPv6 지원**: 모든 IP 형식 처리

#### 📋 개발 지침
```java
// ✅ 올바른 IP 제한 설정
@Component
public class IpRestrictionFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        String clientIp = getClientIpAddress((HttpServletRequest) request);
        
        if (!isAllowedIp(clientIp)) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        
        chain.doFilter(request, response);
    }
}

// 환경변수로 설정
// ALLOWED_IPS=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
```

### 4.2 Rate Limiting (W20 대응)

#### ✅ 구현 완료 상태
- **파일**: `RateLimitConfig.java`, `RateLimitInterceptor.java`
- **Token Bucket 알고리즘**: Bucket4j 라이브러리 사용
- **다단계 제한**: 초당 5000, 분당 50,000 요청
- **IP별 추적**: 동시성 안전한 추적

#### 📋 개발 지침
```java
// ✅ 올바른 Rate Limiting 설정
@Configuration
public class RateLimitConfig {
    @Bean
    public Map<String, Bucket> bucketMap() {
        return new ConcurrentHashMap<>();
    }
    
    private Bucket createNewBucket() {
        return Bucket4j.builder()
            .addLimit(Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofSeconds(1))))
            .addLimit(Bandwidth.classic(50000, Refill.intervally(50000, Duration.ofMinutes(1))))
            .build();
    }
}
```

---

## 📊 5. 로깅 및 모니터링

### 5.1 보안 이벤트 로깅 (W9 대응)

#### ✅ 구현 완료 상태
- **파일**: `SecurityAuditLogger.java`, `SecurityEventService.java`
- **구조화된 로깅**: JSON 형태 로그 출력
- **민감정보 마스킹**: 개인정보 보호
- **실시간 모니터링**: 보안 이벤트 실시간 추적

#### 📋 개발 지침
```java
// ✅ 올바른 보안 로깅
@Component
public class SecurityAuditLogger {
    public void logSecurityEvent(String eventType, String details, String clientIp) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("timestamp", Instant.now().toString());
        logData.put("eventType", eventType);
        logData.put("clientIp", maskSensitiveData(clientIp));
        logData.put("details", details);
        
        log.info("SECURITY_EVENT: {}", objectMapper.writeValueAsString(logData));
    }
    
    // 민감정보 마스킹
    private String maskSensitiveData(String data) {
        // IP 주소 일부 마스킹 로직
        return data.replaceAll("(\\d+\\.\\d+\\.\\d+)\\.(\\d+)", "$1.***");
    }
}
```

### 5.2 브루트포스 방어 (W12, W20 대응)

#### ✅ 구현 완료 상태
- **파일**: `SecurityEventService.java`
- **실패 횟수 추적**: 5회 실패 시 15분 잠금
- **IP별 모니터링**: 의심스러운 활동 감지
- **자동 해제**: 시간 기반 자동 잠금 해제

#### 📋 개발 지침
```java
// ✅ 올바른 브루트포스 방어
@Service
public class SecurityEventService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    
    public boolean isIpBlocked(String clientIp) {
        FailedAttemptInfo info = failedAttempts.get(clientIp);
        if (info != null && info.getAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            return info.getLastAttemptTime().plus(LOCKOUT_DURATION).isAfter(Instant.now());
        }
        return false;
    }
}
```

---

## 🔧 6. 에러 처리 및 정보 노출 방지

### 6.1 안전한 에러 처리 (W9 대응)

#### ✅ 구현 완료 상태
- **파일**: `GlobalExceptionHandler.java`
- **정보 노출 방지**: 스택 트레이스 숨김
- **일관된 에러 형식**: 표준화된 에러 응답
- **다국어 에러 메시지**: 사용자 친화적 메시지

#### 📋 개발 지침
```java
// ✅ 올바른 에러 처리
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(400)
            .error("Validation Failed")
            .message("요청 데이터 검증에 실패했습니다")
            .validationErrors(ex.getValidationErrors())
            .build();
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // ❌ 절대 하지 마세요: ex.printStackTrace() 또는 스택 트레이스 노출
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(500)
            .error("Internal Server Error")
            .message("서버 내부 오류가 발생했습니다")
            .build();
        return ResponseEntity.status(500).body(error);
    }
}
```

---

## 📚 7. 취약점별 대응 현황

### 7.1 완전 구현된 보안 항목 ✅

| 코드 | 진단 항목 | 구현 파일 | 대응 방법 |
|------|-----------|-----------|----------|
| W5 | SQL 인젝션 | `*Repository.java` | JPA 파라미터 바인딩 |
| W11 | XSS | `SecurityHeadersFilter.java` | CSP 헤더, Content-Type 고정 |
| W13 | 불충분한 인증 | `JwtTokenProvider.java` | Keycloak JWT 검증 |
| W15 | CSRF | `CorsConfig.java` | CORS 설정 (API 특성상 적절) |
| W17 | 불충분한 인가 | `SecurityConfig.java` | 역할 기반 접근 제어 |
| W18 | 불충분한 세션 만료 | `JwtTokenProvider.java` | JWT 만료 시간 검증 |
| W19 | 세션 고정 | `SecurityConfig.java` | Stateless 세션 |
| W20 | 자동화 공격 | `RateLimitConfig.java` | Token Bucket Rate Limiting |
| W24 | 관리자 페이지 노출 | `IpRestrictionFilter.java` | IP 기반 접근 제한 |
| W27 | 데이터 평문 전송 | `SecurityConfig.java` | HTTPS 강제, HSTS |
| W9 | 정보 누출 | `GlobalExceptionHandler.java` | 안전한 에러 처리 |

### 7.2 해당 없는 보안 항목 (API 특성상)

| 코드 | 진단 항목 | 사유 |
|------|-----------|------|
| W22 | 파일 업로드 | 파일 업로드 기능 없음 |
| W23 | 파일 다운로드 | 파일 다운로드 기능 없음 |
| W28 | 쿠키 변조 | JWT 기반 인증으로 쿠키 미사용 |
| W1 | 버퍼 오버플로우 | Java 언어 특성상 해당 없음 |
| W2 | 포맷스트링 | Java 언어 특성상 해당 없음 |

---

## 🎯 8. 보안 개발 체크리스트

### 8.1 개발 단계별 보안 점검

#### Phase 1: 설계 단계 ✅
- [x] 인증/인가 방식 정의
- [x] 데이터 플로우 보안 분석
- [x] 위협 모델링 수행
- [x] 보안 요구사항 정의

#### Phase 2: 구현 단계 ✅
- [x] 입력값 검증 구현
- [x] 보안 헤더 설정
- [x] 에러 처리 표준화
- [x] 로깅 및 모니터링 구현

#### Phase 3: 테스트 단계 ✅
- [x] 보안 단위 테스트
- [x] 보안 통합 테스트
- [x] 침투 테스트 수행
- [x] 보안 코드 리뷰

#### Phase 4: 배포 단계 ✅
- [x] 보안 설정 검증
- [x] 환경변수 보안 설정
- [x] 모니터링 대시보드 구성
- [x] 보안 운영 가이드 작성

### 8.2 코드 리뷰 보안 체크포인트

#### 필수 확인 사항
1. **인증 검증**: 모든 API 엔드포인트에 인증 필요
2. **입력값 검증**: @Valid, 커스텀 검증 어노테이션 사용
3. **SQL 안전성**: @Query + @Param 또는 Spring Data JPA 메서드
4. **에러 처리**: 민감정보 노출 방지
5. **로깅**: 보안 이벤트 적절한 로깅
6. **설정 값**: 하드코딩 금지, 환경변수 사용

---

## 🚨 9. 보안 사고 대응 절차

### 9.1 보안 사고 탐지
- **자동 탐지**: SecurityEventService 실시간 모니터링
- **수동 보고**: 관리자 또는 사용자 신고
- **로그 분석**: 보안 로그 정기 분석

### 9.2 대응 절차
1. **즉시 대응**: 공격 차단 (IP 차단, 서비스 일시 중단)
2. **영향 분석**: 피해 범위 및 원인 분석
3. **복구 작업**: 시스템 복구 및 보안 강화
4. **사후 조치**: 재발 방지 대책 수립

---

## 📖 10. 참고 자료

### 10.1 보안 표준 및 가이드
- **OWASP Top 10**: 웹 애플리케이션 보안 위험
- **SANS Top 25**: 소프트웨어 보안 약점
- **NIST Cybersecurity Framework**: 사이버보안 프레임워크
- **ISO 27001**: 정보보안 관리체계

### 10.2 도구 및 라이브러리
- **Spring Security**: 인증/인가 프레임워크
- **Bucket4j**: Rate Limiting 라이브러리
- **NimbusDS**: JWT 검증 라이브러리
- **SLF4J + Logback**: 보안 로깅

---

## ✅ 11. 보안 지침 준수 확인

### 현재 프로젝트 보안 준수 현황: **95% 완료**

#### 완전 구현된 항목 (18/19)
- ✅ JWT 인증 및 검증
- ✅ SQL 인젝션 방어
- ✅ XSS 방어
- ✅ CSRF 방어 (API 특성상 적절)
- ✅ 입력값 검증
- ✅ Rate Limiting
- ✅ IP 접근 제한
- ✅ 보안 헤더 설정
- ✅ HTTPS 강제
- ✅ 에러 처리 표준화
- ✅ 보안 로깅
- ✅ 브루트포스 방어
- ✅ 세션 보안
- ✅ 정보 노출 방지
- ✅ 보안 테스트
- ✅ 모니터링 및 알림
- ✅ 환경별 보안 설정
- ✅ 코드 리뷰 체크리스트

#### 개선 권장 항목 (1/19)
- 🔄 **시크릿 관리**: 현재 환경변수 사용, 운영환경에서 Vault 등 고도화 권장

**종합 평가**: 프로젝트는 엔터프라이즈 수준의 보안 기준을 만족하며, 모든 주요 보안 위협에 대한 적절한 방어 메커니즘을 갖추고 있습니다.