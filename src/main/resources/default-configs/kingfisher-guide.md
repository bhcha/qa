# Kingfisher Secret Scanner 가이드

## 개요

Kingfisher는 MongoDB에서 개발한 고성능 비밀(secret) 스캐닝 도구로, 소스 코드에서 하드코딩된 자격 증명, API 키, 토큰 등을 탐지합니다.

## 주요 기능

- **고성능 스캐닝**: Rust로 작성되어 매우 빠른 속도로 스캔
- **실시간 검증**: AWS, Azure, GCP 등의 자격 증명을 실제로 검증
- **700+ 내장 규칙**: 다양한 서비스의 비밀 패턴 탐지
- **Git 히스토리 스캔**: 과거 커밋에서도 비밀 탐지
- **낮은 오탐률**: 엔트로피 분석과 언어 인식 파싱 결합

## 설치 방법

### macOS
```bash
brew install mongodb/brew/kingfisher
```

### Linux
```bash
# GitHub releases에서 바이너리 다운로드
wget https://github.com/mongodb/kingfisher/releases/latest/download/kingfisher-linux-amd64
chmod +x kingfisher-linux-amd64
sudo mv kingfisher-linux-amd64 /usr/local/bin/kingfisher
```

### Windows
```bash
# GitHub releases에서 Windows 바이너리 다운로드
# https://github.com/mongodb/kingfisher/releases
```

## QA 모듈에서의 사용법

### 1. 기본 사용
QA 모듈에서 Kingfisher는 자동으로 실행됩니다:
```bash
./gradlew qualityCheck
```

### 2. Kingfisher만 실행
```bash
kingfisher scan /path/to/project
```

### 3. 설정 커스터마이징
`config/kingfisher/rules.yaml` 파일을 수정하여 커스텀 규칙을 추가할 수 있습니다.

## 탐지 가능한 비밀 유형

### 클라우드 프로바이더
- AWS (Access Keys, Secret Keys, Session Tokens)
- Azure (Service Principal, Storage Keys, CosmosDB)
- Google Cloud Platform (Service Account Keys, API Keys)

### 버전 관리 시스템
- GitHub (Personal Access Tokens, OAuth Tokens)
- GitLab (Personal Access Tokens, CI/CD Variables)
- Bitbucket (App Passwords, OAuth Tokens)

### 데이터베이스
- MongoDB (Connection Strings)
- PostgreSQL/MySQL (Connection Strings)
- Redis (Passwords)

### API 서비스
- Stripe, Twilio, SendGrid
- Slack, Discord, Telegram
- OpenAI, Anthropic

### 기타
- JWT Tokens
- SSH Private Keys
- TLS Certificates
- Generic API Keys

## 설정 옵션

### confidence 레벨
- **low**: 모든 잠재적 비밀 탐지 (오탐 가능성 높음)
- **medium**: 균형잡힌 탐지 (기본값)
- **high**: 확실한 비밀만 탐지 (놓칠 가능성 있음)

### validation 옵션
- 실제 서비스에 연결하여 자격 증명 유효성 검증
- 활성화된 비밀과 비활성화된 비밀 구분

## 베이스라인 관리

### 베이스라인 생성
기존 비밀을 문서화하여 향후 스캔에서 제외:
```bash
kingfisher scan /path/to/project \
  --confidence low \
  --manage-baseline \
  --baseline-file config/kingfisher/baseline.yaml
```

### 베이스라인 사용
```bash
kingfisher scan /path/to/project \
  --baseline-file config/kingfisher/baseline.yaml
```

## 제외 패턴

기본적으로 다음 경로는 제외됩니다:
- `build/**`
- `.git/**`
- `**/*.class`
- `**/node_modules/**`
- `**/target/**`

추가 제외 패턴은 `rules.yaml`에서 설정 가능합니다.

## CI/CD 통합

### GitHub Actions
```yaml
- name: Run Kingfisher
  run: |
    wget https://github.com/mongodb/kingfisher/releases/latest/download/kingfisher-linux-amd64
    chmod +x kingfisher-linux-amd64
    ./kingfisher-linux-amd64 scan . --confidence medium
```

### Jenkins
```groovy
stage('Secret Scanning') {
    steps {
        sh 'kingfisher scan . --output-format json --output kingfisher-report.json'
        publishHTML([
            reportDir: 'build/reports/kingfisher',
            reportFiles: 'main.html',
            reportName: 'Kingfisher Report'
        ])
    }
}
```

## 문제 해결

### 오탐(False Positive) 처리
1. 베이스라인에 추가
2. 커스텀 제외 규칙 추가
3. 코드 주석으로 표시: `// kingfisher:ignore`

### 성능 최적화
- 대용량 바이너리 파일 제외
- Git 히스토리 깊이 제한
- 병렬 처리 활용

## 보안 모범 사례

1. **절대 하드코딩하지 마세요**
   - 환경 변수 사용
   - 시크릿 관리 도구 활용 (Vault, AWS Secrets Manager 등)

2. **즉시 교체**
   - 노출된 비밀은 즉시 폐기하고 새로 발급

3. **정기적 스캔**
   - CI/CD 파이프라인에 통합
   - 주기적인 전체 리포지토리 스캔

4. **교육과 인식**
   - 개발팀 교육
   - 보안 정책 수립

## 참고 자료

- [Kingfisher GitHub Repository](https://github.com/mongodb/kingfisher)
- [Kingfisher Documentation](https://github.com/mongodb/kingfisher/tree/main/docs)
- [Secret Management Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)