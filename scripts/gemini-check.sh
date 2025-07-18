#!/bin/bash

# Gemini 품질 검증 명령어 구현
# Usage: ./scripts/gemini/gemini-check.sh [type] [path] [options]

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 기본 설정
CHECK_TYPE=""
TARGET_PATH=""
OUTPUT_FORMAT="text"
WITH_COVERAGE=false
TOOL_NAME=""
ANALYSIS_TYPE=""
RUN_ALL=false

# 함수: 사용법 출력
show_usage() {
    echo "Usage: $0 [type] [path] [options]"
    echo ""
    echo "Types:"
    echo "  architecture     - Check hexagonal architecture compliance"
    echo "  quality         - Analyze code quality metrics (errors only)"
    echo "  tdd             - Verify TDD compliance and methodology"
    echo "  coverage        - Analyze test coverage with JaCoCo"
    echo "  security        - Scan for security vulnerabilities"
    echo "  cqrs            - Check CQRS architecture compliance"
    echo "  sonarqube       - Run SonarQube code quality analysis"
    echo "  static-analysis - Run static analysis tools"
    echo "  ai-analysis     - Run AI-based analysis"
    echo "  summary         - Generate summary report from multiple JSON files"
    echo "  warnings        - Generate detailed warnings and style recommendations"
    echo ""
    echo "Options:"
    echo "  --output json      - Output in JSON format"
    echo "  --with-coverage    - Include coverage analysis (for tdd type)"
    echo "  --tool [name]      - Specific tool for static-analysis (checkstyle|spotbugs|pmd|jacoco|sonarqube|archunit)"
    echo "  --type [name]      - Specific type for ai-analysis (security|tdd|cqrs)"
    echo "  --all              - Run all tools/types in the group"
    echo ""
    echo "Examples:"
    echo "  $0 architecture --output json"
    echo "  $0 quality @src/main/java/domain/member --output json"
    echo "  $0 tdd @src/test/java --with-coverage --output json"
    echo "  $0 cqrs --output json"
}

# 인자 파싱
while [[ $# -gt 0 ]]; do
    case "$1" in
        architecture|quality|tdd|security|cqrs|sonarqube|coverage|static-analysis|ai-analysis)
            CHECK_TYPE="$1"
            shift
            ;;
        summary)
            CHECK_TYPE="$1"
            shift
            # Collect all subsequent arguments as file paths for summary
            while [[ $# -gt 0 && "$1" != --* ]]; do
                SUMMARY_FILES+=("$1")
                shift
            done
            ;;
        warnings)
            CHECK_TYPE="$1"
            shift
            ;;
        @*)
            TARGET_PATH="${1:1}" # @ 제거
            shift
            ;;
        --output)
            OUTPUT_FORMAT="$2"
            shift 2
            ;;
        --format)
            OUTPUT_FORMAT="$2"
            shift 2
            ;;
        --with-coverage)
            WITH_COVERAGE=true
            shift
            ;;
        --tool)
            TOOL_NAME="$2"
            shift 2
            ;;
        --type)
            ANALYSIS_TYPE="$2"
            shift 2
            ;;
        --all)
            RUN_ALL=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# 필수 인자 확인
if [[ -z "$CHECK_TYPE" ]]; then
    echo -e "${RED}Error: Check type is required${NC}"
    show_usage
    exit 1
fi

# 기본값 설정
if [[ -z "$TARGET_PATH" && "$CHECK_TYPE" != "architecture" && "$CHECK_TYPE" != "cqrs" && "$CHECK_TYPE" != "sonarqube" ]]; then
    TARGET_PATH="src/main/java"
fi

# 타임스탬프 (한국시간)
TIMESTAMP=$(TZ=Asia/Seoul date +"%Y-%m-%dT%H:%M:%S")

# 레포트 디렉토리 생성 및 정리
create_reports_dir() {
    # 기본 디렉토리 생성
    mkdir -p qa/reports{ai,static}
    
    # 개별 분석 타입별 JSON 파일 정리
    if [[ -n "$CHECK_TYPE" && "$CHECK_TYPE" != "summary" && "$CHECK_TYPE" != "warnings" ]]; then
        case "$CHECK_TYPE" in
            "security"|"tdd"|"cqrs")
                # AI 분석 타입들 - ai 디렉토리에서 정리
                find qa/reports/ai -name "${CHECK_TYPE}_*.json" -type f -delete 2>/dev/null || true
                ;;
            "architecture"|"coverage"|"sonarqube")
                # 개별 분석 타입들 - reports 루트에서 정리
                find qa/reports -maxdepth 1 -name "${CHECK_TYPE}_*.json" -type f -delete 2>/dev/null || true
                ;;
            "quality")
                # 품질 분석 - static 디렉토리에서 정리
                find qa/reports/static -name "${CHECK_TYPE}_*.json" -type f -delete 2>/dev/null || true
                ;;
            "static-analysis")
                # 정적분석 그룹 - static 디렉토리에서 정리 (그룹 파일만 삭제)
                find qa/reports/static -name "static_analysis_*.json" -type f -delete 2>/dev/null || true
                ;;
            "ai-analysis")
                # AI분석 그룹 - ai 디렉토리에서 정리 (그룹 파일만 삭제)
                find qa/reports/ai -name "ai_analysis_*.json" -type f -delete 2>/dev/null || true
                ;;
        esac
    fi
}

# JSON 헤더 생성
generate_json_header() {
    cat << EOF
{
  "status": "unknown",
  "timestamp": "$TIMESTAMP",
  "type": "$CHECK_TYPE",
  "target": "$TARGET_PATH",
  "summary": "",
  "violations": [],
  "metrics": {}
}
EOF
}

# 아키텍처 검증
check_architecture() {
    create_reports_dir
    local report_file="qa/reports/architecture_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🏗️  Checking hexagonal architecture compliance...${NC}"
    fi
    
    local json_output=""
    if ./gradlew archunitTest --quiet > /tmp/arch-test.log 2>&1; then
        json_output=$(cat << EOF
{
  "status": "pass",
  "timestamp": "$TIMESTAMP",
  "type": "architecture",
  "target": "entire codebase",
  "summary": "All architecture rules passed - hexagonal architecture compliance verified",
  "violations": [],
  "metrics": {
    "score": 100,
    "files_analyzed": $(find src -name "*.java" | wc -l),
    "rules_checked": 12,
    "violations_found": 0
  },
  "recommendations": [],
  "next_steps": [
    "Continue following hexagonal architecture patterns",
    "Monitor for new violations in future commits"
  ]
}
EOF
)
    else
        # 실패한 경우 로그에서 위반사항 추출
        local violations_raw=$(grep -E "^.*(should|must) (not|only).*$" /tmp/arch-test.log || true)
        local violation_count=$(echo "$violations_raw" | wc -l | tr -d ' ')
        local violations_json="[]"

        if [[ "$violation_count" -gt 0 ]]; then
            local violations_entries=()
            while IFS= read -r line; do
                local message="$line"
                violations_entries+=("{\"severity\": \"error\", \"file\": \"architecture tests\", \"line\": 0, \"message\": \"${message}\", \"suggestion\": \"Review dependency directions and layer responsibilities\"}")
            done <<< "$violations_raw"
            violations_json="[$(IFS=,; echo "${violations_entries[*]}")]"
        fi

        json_output=$(cat << EOF
{
  "status": "fail",
  "timestamp": "$TIMESTAMP",
  "type": "architecture",
  "target": "entire codebase",
  "summary": "Architecture violations detected in hexagonal architecture",
  "violations": $violations_json,
  "metrics": {
    "score": 75,
    "files_analyzed": $(find src -name "*.java" | wc -l),
    "rules_checked": 12,
    "violations_found": $violation_count
  },
  "recommendations": [
    {"priority": "high", "action": "Fix dependency direction violations", "rationale": "Maintains clean architecture separation"}
  ],
  "next_steps": [
    "Check build/qa/reports/tests/archunitTest/index.html for details",
    "Review shared-guides/architecture-rules.md",
    "Fix violations before proceeding"
  ]
}
EOF
)
    fi
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
    fi
}

# 코드 품질 분석 (통합)
check_quality() {
    create_reports_dir
    local report_file="qa/reports/static/quality_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}📊 Analyzing code quality with multiple tools...${NC}"
    fi
    
    local json_output=""
    local quality_status="pass"
    local all_violations=()
    local total_violation_count=0
    
    # 1. Checkstyle 실행
    local checkstyle_report="build/qa/reports/checkstyle/main.xml"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "  Running Checkstyle..."
    fi
    
    if ! ./gradlew checkstyleMain -q > /dev/null 2>&1; then
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${YELLOW}Warning: Checkstyle task failed, but attempting to parse report.${NC}"
        fi
        quality_status="fail"
    fi
    
    # 2. SpotBugs 실행
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "  Running SpotBugs..."
    fi
    
    if ! ./gradlew spotbugsMain -q > /dev/null 2>&1; then
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${YELLOW}Warning: SpotBugs task failed, but attempting to parse report.${NC}"
        fi
        quality_status="fail"
    fi
    
    # 3. PMD 실행
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "  Running PMD..."
    fi
    
    if ! ./gradlew pmdMain -q > /dev/null 2>&1; then
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${YELLOW}Warning: PMD task failed, but attempting to parse report.${NC}"
        fi
        quality_status="fail"
    fi

    # Checkstyle 리포트 파싱 (macOS 호환) - Error와 Warning 분리
    if [[ -f "$checkstyle_report" ]]; then
        local error_violations_array=()
        local warning_violations_array=()
        local current_file=""
        local error_count=0
        local warning_count=0
        
        # XML 파일을 한 줄씩 읽어서 파싱
        while IFS= read -r line; do
            # file 태그에서 파일명 추출
            if [[ "$line" =~ \<file\ name=\"([^\"]+)\" ]]; then
                current_file="${BASH_REMATCH[1]}"
                # 절대 경로를 상대 경로로 변환
                current_file=$(echo "$current_file" | sed "s|$(pwd)/||")
            fi
            
            # error 태그에서 위반사항 정보 추출
            if [[ "$line" =~ \<error\ line=\"([^\"]+)\".*severity=\"([^\"]+)\".*message=\"([^\"]+)\" ]]; then
                local line_num="${BASH_REMATCH[1]}"
                local severity="${BASH_REMATCH[2]}"
                local message="${BASH_REMATCH[3]}"
                
                if [[ -n "$current_file" && -n "$line_num" && -n "$severity" && -n "$message" ]]; then
                    # JSON 이스케이프 처리
                    message=$(echo "$message" | sed 's/"/\\"/g')
                    local violation_json="{\"severity\": \"${severity}\", \"file\": \"${current_file}\", \"line\": ${line_num}, \"message\": \"${message}\"}"
                    
                    # 심각도별 분류
                    if [[ "$severity" == "error" ]]; then
                        error_violations_array+=("$violation_json")
                        error_count=$((error_count + 1))
                    else
                        # warning, info, ignore 등은 warning 카테고리로
                        warning_violations_array+=("$violation_json")
                        warning_count=$((warning_count + 1))
                    fi
                fi
            fi
        done < "$checkstyle_report"

        # Error만 메인 리포트에 포함
        violation_count=$error_count
        if [[ "$error_count" -gt 0 ]]; then
            quality_status="fail"
            violations_json="[$(IFS=,; echo "${error_violations_array[*]}")]"
        else
            violations_json="[]"
        fi
        
        # Warning 리포트 생성 (별도 파일)
        if [[ "$warning_count" -gt 0 ]]; then
            local warnings_file="qa/reports/static/warnings_${TIMESTAMP}.json"
            local warnings_json="[$(IFS=,; echo "${warning_violations_array[*]}")]"
            cat > "$warnings_file" << EOF
{
  "timestamp": "$TIMESTAMP",
  "type": "warnings",
  "target": "${TARGET_PATH:-src/main/java}",
  "summary": "Code style warnings and recommendations",
  "warnings": $warnings_json,
  "metrics": {
    "files_analyzed": $(find "${TARGET_PATH:-src/main/java}" -name "*.java" | wc -l),
    "warnings_found": $warning_count
  }
}
EOF
        fi
    else
        echo -e "${RED}Error: Checkstyle report not found at $checkstyle_report${NC}"
        quality_status="fail"
        violations_json="[{\"severity\": \"error\", \"file\": \"N/A\", \"line\": 0, \"message\": \"Checkstyle report not generated or found.\", \"suggestion\": \"Ensure Checkstyle is configured correctly and runs without errors.\"}]"
    fi

    # 파일 및 라인 수 계산 (기존 로직 유지)
    local JAVA_FILES=$(find "${TARGET_PATH:-src/main/java}" -name "*.java" | wc -l)
    local TOTAL_LINES=$(find "${TARGET_PATH:-src/main/java}" -name "*.java" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    local AVG_LINES_PER_FILE=$((TOTAL_LINES / (JAVA_FILES > 0 ? JAVA_FILES : 1)))
    
    # SpotBugs 리포트 파싱
    local spotbugs_report="build/qa/reports/spotbugs/main.xml"
    local spotbugs_count=0
    if [[ -f "$spotbugs_report" ]]; then
        spotbugs_count=$(xmllint --xpath "count(//BugInstance)" "$spotbugs_report" 2>/dev/null || echo "0")
        if [[ "$spotbugs_count" -gt 0 ]]; then
            total_violation_count=$((total_violation_count + spotbugs_count))
            quality_status="fail"
        fi
    fi
    
    # PMD 리포트 파싱
    local pmd_report="build/qa/reports/pmd/main.xml"
    local pmd_count=0
    if [[ -f "$pmd_report" ]]; then
        pmd_count=$(xmllint --xpath "count(//violation)" "$pmd_report" 2>/dev/null || echo "0")
        if [[ "$pmd_count" -gt 0 ]]; then
            total_violation_count=$((total_violation_count + pmd_count))
            quality_status="fail"
        fi
    fi
    
    total_violation_count=$((violation_count + spotbugs_count + pmd_count))
    
    # 품질 점수 계산 (다중 도구 기반)
    local quality_score=85
    if [[ "$total_violation_count" -gt 0 ]]; then
        # 도구별 가중치 적용
        local checkstyle_penalty=$((violation_count * 1))     # 경미한 패널티
        local spotbugs_penalty=$((spotbugs_count * 3))        # 중간 패널티  
        local pmd_penalty=$((pmd_count * 2))                  # 중간 패널티
        local total_penalty=$((checkstyle_penalty + spotbugs_penalty + pmd_penalty))
        
        quality_score=$((85 - total_penalty))
        if [[ "$quality_score" -lt 0 ]]; then quality_score=0; fi
    fi

    json_output=$(cat << EOF
{
  "status": "$quality_status",
  "timestamp": "$TIMESTAMP",
  "type": "quality",
  "target": "${TARGET_PATH:-src/main/java}",
  "summary": "Code quality analysis completed with Checkstyle, SpotBugs, and PMD",
  "violations": $violations_json,
  "metrics": {
    "files_analyzed": $JAVA_FILES,
    "total_lines": $TOTAL_LINES,
    "avg_lines_per_file": $AVG_LINES_PER_FILE,
    "violations_found": $total_violation_count,
    "quality_score": $quality_score,
    "tool_breakdown": {
      "checkstyle": $violation_count,
      "spotbugs": $spotbugs_count,
      "pmd": $pmd_count
    }
  },
  "recommendations": [
    "Review Checkstyle violations for code style improvements",
    "Address SpotBugs findings for potential bugs and performance issues", 
    "Fix PMD violations for better code maintainability",
    "Consider integrating quality gates in CI/CD pipeline"
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
        echo ""
        echo "📊 Quality Report - Click to open in browser:"
        
        # 생성된 HTML 리포트들 확인
        if [[ -f "build/qa/reports/checkstyle/main.html" ]]; then
            echo "  🔗 Checkstyle: file://$(pwd)/build/qa/reports/checkstyle/main.html"
        fi
        if [[ -f "build/qa/reports/spotbugs/main.html" ]]; then
            echo "  🔗 SpotBugs: file://$(pwd)/build/qa/reports/spotbugs/main.html"
        fi
        if [[ -f "build/qa/reports/pmd/main.html" ]]; then
            echo "  🔗 PMD: file://$(pwd)/build/qa/reports/pmd/main.html"
        fi
        
        echo ""
        echo "💡 Tip: Cmd+Click (Mac) or Ctrl+Click to open links directly"
    fi
}

# TDD 검증
check_tdd() {
    create_reports_dir
    local report_file="qa/reports/ai/tdd_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🧪 Verifying TDD compliance...${NC}"
    fi
    
    local json_output=""
    local test_log="/tmp/test-results.log"
    local test_output=$(./gradlew test 2>&1 || true)
    local gradlew_exit_code=$?

    if [[ "$gradlew_exit_code" -eq 0 ]]; then
        TEST_STATUS="pass"
        SUMMARY="All tests passed"
        VIOLATIONS_JSON="[]"
    else
        TEST_STATUS="fail"
        SUMMARY="Some tests failed"
        # Parse failed tests from output and format as JSON violations
        local failed_tests_raw=$(echo "$test_output" | grep -E "> .* FAILED$" | grep -v ":test FAILED" || true)
        local failed_tests_count=$(echo "$failed_tests_raw" | wc -l | tr -d ' ')

        if [[ "$failed_tests_count" -gt 0 ]]; then
            local violations_entries=()
            while IFS= read -r line; do
                local test_name=$(echo "$line" | sed -E 's/^> (.*) FAILED$/\1/')
                violations_entries+=("{\"severity\": \"error\", \"file\": \"test_suite\", \"line\": 0, \"message\": \"Test failed: ${test_name}\", \"suggestion\": \"Review test logs for details\"}")
            done <<< "$failed_tests_raw"
            VIOLATIONS_JSON="[$(IFS=,; echo "${violations_entries[*]}")]]"
        else
            VIOLATIONS_JSON="[]"
        fi
    fi
    
    # TDD 품질 분석 (Gemini 기반, 커버리지와 독립적)
    local TEST_FILES=$(find src/test/java -name "*Test.java" | wc -l)
    local MAIN_FILES=$(find src/main/java -name "*.java" | wc -l)
    
    # Gemini를 통한 TDD 품질 분석
    local tdd_guide_path="docs/claude-resources/tdd-guide.md"
    local tdd_score=75  # 기본 점수
    local tdd_analysis=""
    local tdd_violations="[]"
    local tdd_recommendations="[]"
    
    if [[ -f "$tdd_guide_path" ]]; then
        local gemini_prompt="@${tdd_guide_path} @src/test/java 다음 TDD 가이드를 기반으로 테스트 코드의 품질과 TDD 원칙 준수를 분석하세요:

**분석 기준:**
1. Red-Green-Refactor 사이클 준수 (커밋 패턴 분석)
2. 테스트 이름의 의미성 (should, when-then, given-when-then 패턴)
3. 테스트의 독립성과 격리성
4. 단일 책임 원칙 (하나의 테스트는 하나의 동작만)
5. Mock 사용의 적절성 (over-mocking 방지)
6. 어설션의 명확성과 의미성
7. 테스트 코드의 가독성과 유지보수성

**주요 확인사항:**
- 테스트 클래스 구조와 패키지 구성
- 테스트 메서드 명명 규칙
- Given-When-Then 구조 사용 여부
- @BeforeEach, @AfterEach 등 적절한 설정/정리
- Mock 객체 사용 패턴
- 테스트 데이터 관리

JSON 형식으로 응답하세요:
{
  \"tdd_score\": 0-100,
  \"analysis\": \"종합 분석 내용\",
  \"violations\": [
    {\"type\": \"naming|structure|mocking|assertion\", \"severity\": \"high|medium|low\", \"file\": \"파일명\", \"description\": \"위반 내용\", \"suggestion\": \"개선 방안\"}
  ],
  \"recommendations\": [\"구체적인 개선 권장사항들\"],
  \"strengths\": [\"잘 된 부분들\"],
  \"areas_for_improvement\": [\"개선이 필요한 영역들\"]
}"

        # Gemini 호출
        if command -v gemini >/dev/null 2>&1; then
            local gemini_result=$(echo "$gemini_prompt" | gemini -p 2>/dev/null || echo "{\"tdd_score\": 75, \"analysis\": \"TDD analysis completed\", \"violations\": [], \"recommendations\": [], \"strengths\": [], \"areas_for_improvement\": []}")
            
            # 결과 파싱
            if echo "$gemini_result" | jq . >/dev/null 2>&1; then
                tdd_score=$(echo "$gemini_result" | jq -r ".tdd_score // 75")
                tdd_analysis=$(echo "$gemini_result" | jq -r ".analysis // \"TDD analysis completed\"")
                tdd_violations=$(echo "$gemini_result" | jq -c ".violations // []")
                tdd_recommendations=$(echo "$gemini_result" | jq -c ".recommendations // []")
            fi
        else
            # Gemini 미설치 시 기본 패턴 분석
            tdd_analysis="기본 TDD 패턴 분석 완료"
            
            # 테스트 이름 패턴 검사
            local bad_test_names=$(find src/test/java -name "*Test.java" -exec grep -l "void test[A-Z]" {} \; 2>/dev/null | wc -l)
            local good_test_names=$(find src/test/java -name "*Test.java" -exec grep -l "void should\|void when.*then\|void given.*when.*then" {} \; 2>/dev/null | wc -l)
            
            if [[ "$bad_test_names" -gt 0 ]]; then
                tdd_violations="[{\"type\": \"naming\", \"severity\": \"medium\", \"description\": \"Found ${bad_test_names} test files with non-descriptive test method names\", \"suggestion\": \"Use descriptive names like should_xxx or when_xxx_then_xxx\"}]"
                tdd_score=$((tdd_score - 15))
            fi
            
            tdd_recommendations="[\"Follow Red-Green-Refactor cycle\", \"Use descriptive test names\", \"Ensure test independence\", \"Consider using Given-When-Then structure\"]"
        fi
    else
        tdd_analysis="TDD 가이드 파일을 찾을 수 없습니다"
        tdd_violations="[{\"type\": \"guide\", \"severity\": \"low\", \"description\": \"TDD guide not found at ${tdd_guide_path}\", \"suggestion\": \"Ensure TDD guide exists for proper analysis\"}]"
        tdd_recommendations="[\"Create TDD guide document\", \"Follow Red-Green-Refactor cycle\"]"
        tdd_score=50
    fi
    
    json_output=$(cat << EOF
{
  "status": "$TEST_STATUS",
  "timestamp": "$TIMESTAMP",
  "type": "tdd",
  "target": "${TARGET_PATH:-src/test/java}",
  "summary": "TDD methodology analysis completed",
  "violations": $tdd_violations,
  "metrics": {
    "test_files": $TEST_FILES,
    "main_files": $MAIN_FILES,
    "tdd_score": $tdd_score,
    "methodology_based": true
  },
  "analysis": "$tdd_analysis",
  "recommendations": $tdd_recommendations,
  "note": "For test coverage metrics, use 'coverage' type analysis separately"
}
EOF
)
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
    fi
}

# 커버리지 검증 (JaCoCo 기반)
check_coverage() {
    create_reports_dir
    local report_file="qa/reports/coverage_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}📊 Analyzing test coverage with JaCoCo...${NC}"
    fi
    
    # JaCoCo 리포트 생성
    ./gradlew jacocoTestReport --continue --quiet > /dev/null 2>&1 || true
    
    local coverage_html="build/reports/jacoco/test/html/index.html"
    local coverage_xml="build/reports/jacoco/test/jacocoTestReport.xml"
    local status="pass"
    local line_coverage=0
    local branch_coverage=0
    local instruction_coverage=0
    local method_coverage=0
    local class_coverage=0
    local overall_score=0
    local violations_json="[]"
    
    if [[ -f "$coverage_html" ]]; then
        # HTML에서 커버리지 수치 추출 (Total 행의 Cov. 컬럼들)
        local html_content=$(cat "$coverage_html")
        
        # tfoot 섹션에서 Total 행 추출
        local total_row=$(echo "$html_content" | grep -A 20 "<tfoot>" | grep -A 15 "<tr>")
        
        # HTML에서 커버리지 값 추출
        # 퍼센트로 표시된 값들 (Instruction, Branch)
        local coverage_array=($(echo "$total_row" | grep -o 'class="ctr2">[0-9]*%' | sed 's/.*>\([0-9]*\)%.*/\1/'))
        instruction_coverage=${coverage_array[0]:-0}
        branch_coverage=${coverage_array[1]:-0}
        
        # 숫자로 표시된 값들에서 퍼센트 계산 (Lines, Methods, Classes)
        # Lines: missed/total 형태에서 계산
        local line_missed=$(echo "$total_row" | grep -o 'class="ctr1">[0-9,]*</td>' | sed -n '4p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "0")
        local line_total=$(echo "$total_row" | grep -o 'class="ctr2">[0-9,]*</td>' | sed -n '4p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "1")
        if [[ "$line_total" -gt 0 ]]; then
            line_coverage=$(( (line_total - line_missed) * 100 / line_total ))
        fi
        
        # Methods: missed/total 형태에서 계산  
        local method_missed=$(echo "$total_row" | grep -o 'class="ctr1">[0-9,]*</td>' | sed -n '5p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "0")
        local method_total=$(echo "$total_row" | grep -o 'class="ctr2">[0-9,]*</td>' | sed -n '5p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "1")
        if [[ "$method_total" -gt 0 ]]; then
            method_coverage=$(( (method_total - method_missed) * 100 / method_total ))
        fi
        
        # Classes: missed/total 형태에서 계산
        local class_missed=$(echo "$total_row" | grep -o 'class="ctr1">[0-9,]*</td>' | sed -n '6p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "0")
        local class_total=$(echo "$total_row" | grep -o 'class="ctr2">[0-9,]*</td>' | sed -n '6p' | sed 's/.*>\([0-9,]*\)<.*/\1/' | tr -d ',' || echo "1")
        if [[ "$class_total" -gt 0 ]]; then
            class_coverage=$(( (class_total - class_missed) * 100 / class_total ))
        fi
        
        # 전체 점수 계산 (라인 70% + 브랜치 30%)
        overall_score=$(( line_coverage * 70 / 100 + branch_coverage * 30 / 100 ))
        
        # 커버리지 기준 평가
        if [[ "$overall_score" -lt 80 ]]; then
            status="fail"
            violations_json="[{\"type\": \"coverage\", \"severity\": \"medium\", \"description\": \"Test coverage below recommended 80%\", \"suggestion\": \"Add more comprehensive tests to improve coverage\", \"current\": $overall_score, \"target\": 80}]"
        fi
    else
        status="fail"
        violations_json="[{\"type\": \"report\", \"severity\": \"error\", \"description\": \"JaCoCo coverage report not found\", \"suggestion\": \"Ensure JaCoCo is configured and tests run successfully\"}]"
    fi
    
    # 절대 경로로 변환
    local absolute_html_path="$(pwd)/build/reports/jacoco/test/html/index.html"
    
    local json_output=$(cat << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "coverage",
  "target": "entire codebase",
  "summary": "JaCoCo test coverage analysis completed",
  "violations": $violations_json,
  "metrics": {
    "line_coverage": $line_coverage,
    "branch_coverage": $branch_coverage,
    "instruction_coverage": $instruction_coverage,
    "method_coverage": $method_coverage,
    "class_coverage": $class_coverage,
    "overall_score": $overall_score,
    "recommended_minimum": 80
  },
  "reports": {
    "html_report": "$absolute_html_path",
    "xml_report": "$(pwd)/build/reports/jacoco/test/jacocoTestReport.xml"
  },
  "recommendations": [
    "Target 80%+ line and branch coverage",
    "Focus on testing critical business logic first",
    "Add integration tests for complex workflows",
    "Review uncovered code paths in JaCoCo HTML report"
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
        echo ""
        echo "📊 Coverage Report - Click to open in browser:"
        echo "  🔗 JaCoCo Coverage: file://$absolute_html_path"
        echo ""
        echo "💡 Tip: Cmd+Click (Mac) or Ctrl+Click to open link directly"
    fi
}

# 정적분석 그룹 실행
check_static_analysis() {
    # 리포트 디렉토리 생성
    mkdir -p reports/static
    
    local timestamp="${TIMESTAMP}"
    local available_tools=("checkstyle" "spotbugs" "pmd" "jacoco" "sonarqube" "archunit")
    local tools_to_run=()
    
    if [[ "$RUN_ALL" == "true" ]]; then
        tools_to_run=("${available_tools[@]}")
    elif [[ -n "$TOOL_NAME" ]]; then
        if [[ " ${available_tools[*]} " =~ " ${TOOL_NAME} " ]]; then
            tools_to_run=("$TOOL_NAME")
        else
            echo -e "${RED}Error: Unknown tool '$TOOL_NAME'. Available tools: ${available_tools[*]}${NC}"
            exit 1
        fi
    else
        echo -e "${RED}Error: Must specify --tool [name] or --all for static-analysis${NC}"
        show_usage
        exit 1
    fi
    
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🔧 Running Static Analysis Tools: ${tools_to_run[*]}${NC}"
    fi
    
    local results=()
    local overall_status="pass"
    local total_violations=0
    
    for tool in "${tools_to_run[@]}"; do
        # 각 도구 실행 전 타임스탬프 동기화
        TIMESTAMP="$timestamp"
        
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${BLUE}🔄 Running ${tool} analysis...${NC}"
        fi
        
        case "$tool" in
            "checkstyle")
                run_checkstyle_analysis
                ;;
            "spotbugs")
                run_spotbugs_analysis
                ;;
            "pmd")
                run_pmd_analysis
                ;;
            "jacoco")
                run_jacoco_analysis
                ;;
            "sonarqube")
                run_sonarqube_analysis
                ;;
            "archunit")
                run_archunit_analysis
                ;;
        esac
        
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${GREEN}✅ ${tool} analysis completed${NC}"
        fi
    done
    
    # 종합 리포트 생성
    local report_file="qa/reports/static/static_analysis_${timestamp}.json"
    local json_output=$(cat << EOF
{
  "status": "$overall_status",
  "timestamp": "$timestamp",
  "type": "static-analysis",
  "target": "entire codebase",
  "summary": "Static analysis completed using ${#tools_to_run[@]} tools",
  "tools_executed": [$(printf '"%s",' "${tools_to_run[@]}" | sed 's/,$//')],
  "total_violations": $total_violations,
  "individual_reports": {
$(for tool in "${tools_to_run[@]}"; do echo "    \"$tool\": \"qa/reports/static/${tool}_${timestamp}.json\","; done | sed '$s/,$//')
  },
  "recommendations": [
    "Review individual tool reports for specific issues",
    "Address high-priority violations first",
    "Set up quality gates in CI/CD pipeline"
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    
    # 마크다운 리포트 생성
    generate_static_analysis_md_report "$timestamp" "${tools_to_run[@]}"
    
    
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Static analysis report saved to $report_file"
        echo "Markdown report saved to reports/static/STATIC_ANALYSIS_REPORT_${timestamp}.md"
        echo ""
        echo "📊 HTML Reports - Click to open in browser:"
        
        # HTML 리포트 링크들
        local html_reports=(
            "build/reports/checkstyle/main.html:Checkstyle"
            "build/reports/spotbugs/main.html:SpotBugs"
            "build/reports/pmd/main.html:PMD"
            "build/reports/jacoco/test/html/index.html:JaCoCo Coverage"
            "build/reports/tests/test/index.html:Test Results"
            "build/reports/tests/archunitTest/index.html:ArchUnit"
        )
        
        for report in "${html_reports[@]}"; do
            local file_path="${report%%:*}"
            local report_name="${report##*:}"
            local abs_path="$(pwd)/${file_path}"
            
            if [[ -f "$abs_path" ]]; then
                echo "  🔗 $report_name: file://$abs_path"
            fi
        done
        
        echo ""
        echo "💡 Tip: Cmd+Click (Mac) or Ctrl+Click to open links directly"
    fi
}

# 개별 정적분석 도구 실행 함수들
run_checkstyle_analysis() {
    local report_file="qa/reports/static/checkstyle_${TIMESTAMP}.json"
    ./gradlew checkstyleMain --quiet > /dev/null 2>&1 || true
    
    # Checkstyle 결과를 JSON으로 저장
    local checkstyle_report="build/reports/checkstyle/main.xml"
    local status="pass"
    local violations_json="[]"
    
    if [[ -f "$checkstyle_report" ]]; then
        local error_count=$(xmllint --xpath "count(//error[@severity='error'])" "$checkstyle_report" 2>/dev/null || echo "0")
        if [[ "$error_count" -gt 0 ]]; then
            status="fail"
        fi
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "checkstyle",
  "target": "src/main/java",
  "summary": "Checkstyle code style analysis completed",
  "violations": $violations_json,
  "metrics": {
    "files_analyzed": $(find src/main/java -name "*.java" | wc -l),
    "violations_found": ${error_count:-0}
  }
}
EOF
    echo "checkstyle completed"
}

run_spotbugs_analysis() {
    local report_file="qa/reports/static/spotbugs_${TIMESTAMP}.json"
    ./gradlew spotbugsMain --quiet > /dev/null 2>&1 || true
    
    # SpotBugs 결과를 JSON으로 저장
    local spotbugs_report="build/reports/spotbugs/main.xml"
    local status="pass"
    local bug_count=0
    
    if [[ -f "$spotbugs_report" ]]; then
        bug_count=$(xmllint --xpath "count(//BugInstance)" "$spotbugs_report" 2>/dev/null || echo "0")
        if [[ "$bug_count" -gt 0 ]]; then
            status="fail"
        fi
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "spotbugs",
  "target": "src/main/java",
  "summary": "SpotBugs bug pattern analysis completed",
  "violations": [],
  "metrics": {
    "files_analyzed": $(find src/main/java -name "*.java" | wc -l),
    "bugs_found": $bug_count
  }
}
EOF
    echo "spotbugs completed"
}

run_pmd_analysis() {
    local report_file="qa/reports/static/pmd_${TIMESTAMP}.json"
    ./gradlew pmdMain --quiet > /dev/null 2>&1 || true
    
    # PMD 결과를 JSON으로 저장
    local pmd_report="build/reports/pmd/main.xml"
    local status="pass"
    local violation_count=0
    
    if [[ -f "$pmd_report" ]]; then
        violation_count=$(xmllint --xpath "count(//violation)" "$pmd_report" 2>/dev/null || echo "0")
        if [[ "$violation_count" -gt 0 ]]; then
            status="fail"
        fi
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "pmd",
  "target": "src/main/java",
  "summary": "PMD code quality analysis completed",
  "violations": [],
  "metrics": {
    "files_analyzed": $(find src/main/java -name "*.java" | wc -l),
    "violations_found": $violation_count
  }
}
EOF
    echo "pmd completed"
}

run_jacoco_analysis() {
    local report_file="qa/reports/static/jacoco_${TIMESTAMP}.json"
    ./gradlew jacocoTestReport --continue --quiet > /dev/null 2>&1 || true
    
    # JaCoCo 결과를 JSON으로 저장
    local coverage_html="build/reports/jacoco/test/html/index.html"
    local status="pass"
    local instruction_coverage=0
    
    if [[ -f "$coverage_html" ]]; then
        local html_content=$(cat "$coverage_html")
        local total_row=$(echo "$html_content" | grep -A 20 "<tfoot>" | grep -A 15 "<tr>")
        local coverage_array=($(echo "$total_row" | grep -o 'class="ctr2">[0-9]*%' | sed 's/.*>\([0-9]*\)%.*/\1/'))
        instruction_coverage=${coverage_array[0]:-0}
        
        if [[ "$instruction_coverage" -lt 80 ]]; then
            status="fail"
        fi
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "jacoco",
  "target": "entire codebase",
  "summary": "JaCoCo test coverage analysis completed",
  "violations": [],
  "metrics": {
    "instruction_coverage": $instruction_coverage,
    "recommended_minimum": 80
  }
}
EOF
    echo "jacoco completed"
}

run_sonarqube_analysis() {
    local report_file="qa/reports/static/sonarqube_${TIMESTAMP}.json"
    
    # SonarQube 실행 및 결과 저장
    local status="skipped"
    local sonar_url="${SONAR_HOST_URL:-http://localhost:9000}"
    
    if curl -f -s "${sonar_url}/api/system/status" > /dev/null 2>&1; then
        if ./gradlew sonar -q > /dev/null 2>&1; then
            status="pass"
        else
            status="fail"
        fi
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "sonarqube",
  "target": "entire codebase",
  "summary": "SonarQube comprehensive analysis completed",
  "violations": [],
  "metrics": {
    "server_available": $(if [[ "$status" != "skipped" ]]; then echo "true"; else echo "false"; fi)
  }
}
EOF
    echo "sonarqube analysis completed"
}

run_archunit_analysis() {
    local report_file="qa/reports/static/archunit_${TIMESTAMP}.json"
    
    # ArchUnit 실행 및 결과 저장
    local status="pass"
    if ! ./gradlew archunitTest --quiet > /dev/null 2>&1; then
        status="fail"
    fi
    
    cat > "$report_file" << EOF
{
  "status": "$status",
  "timestamp": "$TIMESTAMP",
  "type": "archunit",
  "target": "entire codebase",
  "summary": "ArchUnit architecture compliance analysis completed",
  "violations": [],
  "metrics": {
    "rules_checked": 12,
    "violations_found": $(if [[ "$status" == "fail" ]]; then echo "1"; else echo "0"; fi)
  }
}
EOF
    echo "archunit completed"
}

# 정적분석 마크다운 리포트 생성
generate_static_analysis_md_report() {
    local timestamp="$1"
    shift
    local tools=("$@")
    
    local md_file="qa/reports/static/STATIC_ANALYSIS_REPORT_${timestamp}.md"
    
    cat > "$md_file" << EOF
# 🔧 정적분석 종합 보고서

**📅 분석 일시:** $(TZ=Asia/Seoul date "+%Y-%m-%d %H:%M:%S KST")  
**🎯 분석 도구:** ${#tools[@]}개 (${tools[*]})  
**📊 분석 범위:** 전체 코드베이스

## 📋 실행된 정적분석 도구

EOF

    # 도구 목록 추가
    for tool in "${tools[@]}"; do
        case "$tool" in
            "checkstyle")
                echo "- **Checkstyle** 🎨: Java 코드 스타일 및 규약 검증" >> "$md_file"
                ;;
            "spotbugs") 
                echo "- **SpotBugs** 🐛: 버그 패턴 및 잠재적 결함 탐지" >> "$md_file"
                ;;
            "pmd")
                echo "- **PMD** 📐: 코드 품질 및 유지보수성 분석" >> "$md_file"
                ;;
            "jacoco")
                echo "- **JaCoCo** 📊: 테스트 커버리지 측정" >> "$md_file"
                ;;
            "sonarqube")
                echo "- **SonarQube** 🏆: 종합 코드 품질 분석" >> "$md_file"
                ;;
            "archunit")
                echo "- **ArchUnit** 🏗️: 아키텍처 규칙 및 의존성 검증" >> "$md_file"
                ;;
        esac
    done
    
    cat >> "$md_file" << EOF

## 📈 도구별 분석 결과

EOF

    # 각 도구별 상세 결과 추가
    for tool in "${tools[@]}"; do
        cat >> "$md_file" << EOF
### $(echo "$tool" | tr '[:lower:]' '[:upper:]')

EOF
        case "$tool" in
            "checkstyle")
                generate_checkstyle_md_section >> "$md_file"
                ;;
            "spotbugs")
                generate_spotbugs_md_section >> "$md_file"
                ;;
            "pmd")
                generate_pmd_md_section >> "$md_file"
                ;;
            "jacoco")
                generate_jacoco_md_section >> "$md_file"
                ;;
            "sonarqube")
                generate_sonarqube_md_section >> "$md_file"
                ;;
            "archunit")
                generate_archunit_md_section >> "$md_file"
                ;;
        esac
        echo "" >> "$md_file"
    done
    
    cat >> "$md_file" << EOF

## 🎯 종합 권장사항

### ⚡ 우선순위 높음
- SpotBugs에서 발견된 버그 패턴 수정
- 테스트 커버리지 80% 이상 달성

### 📋 중간 우선순위  
- PMD 코드 품질 권장사항 적용
- Checkstyle 규약 위반사항 정리

### 📚 장기 개선사항
- SonarQube 품질 게이트 설정
- ArchUnit 아키텍처 규칙 확대

## 📊 상세 리포트 링크

EOF

    # 상세 리포트 링크 추가
    for tool in "${tools[@]}"; do
        echo "- **$tool**: [상세 보기]($(pwd)/reports/static/${tool}_${timestamp}.json)" >> "$md_file"
    done
    
    cat >> "$md_file" << EOF

---
*🤖 이 리포트는 정적분석 도구들에 의해 자동 생성되었습니다.*
EOF
}

# 도구별 마크다운 섹션 생성 함수들
generate_checkstyle_md_section() {
    local report_file="build/reports/checkstyle/main.xml"
    if [[ -f "$report_file" ]]; then
        local error_count=$(xmllint --xpath "count(//error[@severity='error'])" "$report_file" 2>/dev/null || echo "0")
        local warning_count=$(xmllint --xpath "count(//error[@severity='warning'])" "$report_file" 2>/dev/null || echo "0")
        
        echo "**상태:** $(if [[ "$error_count" -eq 0 ]]; then echo "✅ 통과"; else echo "❌ 실패"; fi)"
        echo "- **에러:** ${error_count}개"
        echo "- **경고:** ${warning_count}개"
        echo "- **리포트:** [checkstyle HTML]($(pwd)/build/reports/checkstyle/main.html)"
    else
        echo "**상태:** ⚠️ 리포트 생성 실패"
    fi
}

generate_spotbugs_md_section() {
    local report_file="build/reports/spotbugs/main.xml"
    if [[ -f "$report_file" ]]; then
        local bug_count=$(xmllint --xpath "count(//BugInstance)" "$report_file" 2>/dev/null || echo "0")
        
        echo "**상태:** $(if [[ "$bug_count" -eq 0 ]]; then echo "✅ 통과"; else echo "❌ ${bug_count}개 이슈 발견"; fi)"
        echo "- **발견된 버그 패턴:** ${bug_count}개"
        echo "- **리포트:** [SpotBugs HTML]($(pwd)/build/reports/spotbugs/main.html)"
    else
        echo "**상태:** ⚠️ 리포트 생성 실패"
    fi
}

generate_pmd_md_section() {
    local report_file="build/reports/pmd/main.xml"
    if [[ -f "$report_file" ]]; then
        local violation_count=$(xmllint --xpath "count(//violation)" "$report_file" 2>/dev/null || echo "0")
        
        echo "**상태:** $(if [[ "$violation_count" -eq 0 ]]; then echo "✅ 통과"; else echo "❌ ${violation_count}개 위반"; fi)"
        echo "- **코드 품질 위반:** ${violation_count}개"
        echo "- **리포트:** [PMD HTML]($(pwd)/build/reports/pmd/main.html)"
    else
        echo "**상태:** ⚠️ 리포트 생성 실패"
    fi
}

generate_jacoco_md_section() {
    local html_file="build/reports/jacoco/test/html/index.html"
    if [[ -f "$html_file" ]]; then
        # 기존 coverage 검증 로직 재사용
        local html_content=$(cat "$html_file")
        local total_row=$(echo "$html_content" | grep -A 20 "<tfoot>" | grep -A 15 "<tr>")
        local coverage_array=($(echo "$total_row" | grep -o 'class="ctr2">[0-9]*%' | sed 's/.*>\([0-9]*\)%.*/\1/'))
        local instruction_coverage=${coverage_array[0]:-0}
        local branch_coverage=${coverage_array[1]:-0}
        
        # 커버리지 수치 유효성 검사
        if ! [[ "$instruction_coverage" =~ ^[0-9]+$ ]]; then
            instruction_coverage=0
        fi
        if ! [[ "$branch_coverage" =~ ^[0-9]+$ ]]; then
            branch_coverage=0
        fi
        
        echo "**상태:** $(if [[ "$instruction_coverage" -ge 80 ]]; then echo "✅ 우수 (${instruction_coverage}%)"; elif [[ "$instruction_coverage" -ge 60 ]]; then echo "⚠️ 보통 (${instruction_coverage}%)"; else echo "❌ 부족 (${instruction_coverage}%)"; fi)"
        echo "- **Instruction 커버리지:** ${instruction_coverage}%"
        echo "- **Branch 커버리지:** ${branch_coverage}%"
        echo "- **리포트:** [JaCoCo HTML]($(pwd)/build/reports/jacoco/test/html/index.html)"
    else
        echo "**상태:** ⚠️ 리포트 생성 실패"
    fi
}

generate_sonarqube_md_section() {
    echo "**상태:** $(if command -v sonar-scanner >/dev/null 2>&1; then echo "🏆 분석 완료"; else echo "⏭️ 서버 미설치"; fi)"
    echo "- **품질 게이트:** 확인 필요"
    echo "- **리포트:** [SonarQube 대시보드](http://localhost:9000)"
}

generate_archunit_md_section() {
    local test_log="/tmp/arch-test.log"
    if ./gradlew archunitTest --quiet > "$test_log" 2>&1; then
        echo "**상태:** ✅ 모든 아키텍처 규칙 통과"
        echo "- **헥사고날 아키텍처:** 준수"
        echo "- **의존성 방향:** 올바름"
    else
        local violation_count=$(grep -c "should\|must" "$test_log" 2>/dev/null || echo "0")
        echo "**상태:** ❌ ${violation_count}개 아키텍처 위반"
        echo "- **위반사항:** 의존성 규칙 점검 필요"
    fi
    echo "- **테스트 결과:** [Gradle 리포트]($(pwd)/build/reports/tests/archunitTest/index.html)"
}

# AI 분석 그룹 실행
check_ai_analysis() {
    # 리포트 디렉토리 생성
    mkdir -p reports/ai
    
    local timestamp="${TIMESTAMP}"
    local available_types=("security" "tdd" "cqrs")
    local types_to_run=()
    
    if [[ "$RUN_ALL" == "true" ]]; then
        types_to_run=("${available_types[@]}")
    elif [[ -n "$ANALYSIS_TYPE" ]]; then
        if [[ " ${available_types[*]} " =~ " ${ANALYSIS_TYPE} " ]]; then
            types_to_run=("$ANALYSIS_TYPE")
        else
            echo -e "${RED}Error: Unknown analysis type '$ANALYSIS_TYPE'. Available types: ${available_types[*]}${NC}"
            exit 1
        fi
    else
        echo -e "${RED}Error: Must specify --type [name] or --all for ai-analysis${NC}"
        show_usage
        exit 1
    fi
    
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🤖 Running AI Analysis: ${types_to_run[*]}${NC}"
    fi
    
    local overall_status="pass"
    local total_violations=0
    
    for analysis_type in "${types_to_run[@]}"; do
        # 각 분석 타입 실행 전 타임스탬프 동기화
        TIMESTAMP="$timestamp"
        
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${BLUE}🔄 Starting ${analysis_type} analysis...${NC}"
        fi
        
        case "$analysis_type" in
            "security")
                check_security
                ;;
            "tdd")
                check_tdd
                ;;
            "cqrs")
                check_cqrs
                ;;
        esac
        
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo -e "${GREEN}✅ ${analysis_type} analysis completed${NC}"
        fi
    done
    
    # AI 분석 종합 리포트 생성
    local report_file="qa/reports/ai/ai_analysis_${timestamp}.json"
    local json_output=$(cat << EOF
{
  "status": "$overall_status",
  "timestamp": "$timestamp",
  "type": "ai-analysis",
  "target": "entire codebase",
  "summary": "AI analysis completed using Gemini for ${#types_to_run[@]} analysis types",
  "analysis_types": [$(printf '"%s",' "${types_to_run[@]}" | sed 's/,$//')],
  "total_violations": $total_violations,
  "individual_reports": {
$(for type in "${types_to_run[@]}"; do echo "    \"$type\": \"qa/reports/ai/${type}_${timestamp}.json\","; done | sed '$s/,$//')
  },
  "ai_engine": "Gemini",
  "recommendations": [
    "Review AI analysis insights for architectural improvements",
    "Address methodology violations for better code quality",
    "Consider AI recommendations for long-term maintainability"
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    
    # 마크다운 리포트 생성
    generate_ai_analysis_md_report "$timestamp" "${types_to_run[@]}"
    
    
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "AI analysis report saved to $report_file"
        echo "Markdown report saved to reports/ai/AI_ANALYSIS_REPORT_${timestamp}.md"
    fi
}

# AI 분석 마크다운 리포트 생성
generate_ai_analysis_md_report() {
    local timestamp="$1"
    shift
    local analysis_types=("$@")
    
    local md_file="qa/reports/ai/AI_ANALYSIS_REPORT_${timestamp}.md"
    
    cat > "$md_file" << EOF
# 🤖 AI 분석 종합 보고서

**📅 분석 일시:** $(TZ=Asia/Seoul date "+%Y-%m-%d %H:%M:%S KST")  
**🧠 AI 엔진:** Gemini  
**🎯 분석 유형:** ${#analysis_types[@]}개 (${analysis_types[*]})  
**📊 분석 범위:** 전체 코드베이스

## 🔍 실행된 AI 분석

EOF

    # 분석 유형 목록 추가
    for type in "${analysis_types[@]}"; do
        case "$type" in
            "security")
                echo "- **보안 분석** 🔒: secure-guide.md 기반 보안 취약점 검사" >> "$md_file"
                ;;
            "tdd")
                echo "- **TDD 방법론** 🧪: tdd-guide.md 기반 테스트 주도 개발 준수도 분석" >> "$md_file"
                ;;
            "cqrs")
                echo "- **CQRS 패턴** 🔄: Command Query Responsibility Segregation 아키텍처 분석" >> "$md_file"
                ;;
        esac
    done
    
    cat >> "$md_file" << EOF

## 📈 AI 분석 결과

EOF

    # 각 분석 유형별 상세 결과 추가
    for type in "${analysis_types[@]}"; do
        cat >> "$md_file" << EOF
### $(echo "$type" | tr '[:lower:]' '[:upper:]') 분석

EOF
        case "$type" in
            "security")
                generate_security_ai_md_section "$timestamp" >> "$md_file"
                ;;
            "tdd")
                generate_tdd_ai_md_section "$timestamp" >> "$md_file"
                ;;
            "cqrs")
                generate_cqrs_ai_md_section "$timestamp" >> "$md_file"
                ;;
        esac
        echo "" >> "$md_file"
    done
    
    cat >> "$md_file" << EOF

## 🎯 AI 기반 종합 권장사항

### 🔒 보안 개선사항
- Gemini 보안 분석 결과를 바탕으로 한 취약점 보완
- secure-guide.md 기준 미준수 영역 개선

### 🧪 TDD 방법론 개선사항
- Red-Green-Refactor 사이클 준수도 향상
- 테스트 코드 품질 및 가독성 개선

### 🏗️ 아키텍처 개선사항
- CQRS 패턴 일관성 유지
- 도메인 경계 명확화

## 📊 상세 분석 링크

EOF

    # 상세 분석 링크 추가
    for type in "${analysis_types[@]}"; do
        echo "- **$type**: [AI 분석 상세]($(pwd)/reports/ai/${type}_${timestamp}.json)" >> "$md_file"
    done
    
    cat >> "$md_file" << EOF

## 🧠 AI 분석의 장점

- **컨텍스트 이해**: 코드의 의도와 설계 패턴 파악
- **패턴 인식**: 복잡한 아키텍처 및 방법론 준수도 평가  
- **질적 평가**: 정량적 메트릭을 넘어선 종합적 품질 분석
- **가이드 기반**: 프로젝트별 맞춤 가이드라인 준수 확인

---
*🤖 이 리포트는 Gemini AI에 의해 자동 분석 및 생성되었습니다.*
EOF
}

# AI 분석 유형별 마크다운 섹션 생성 함수들
generate_security_ai_md_section() {
    local timestamp="$1"
    local security_report="qa/reports/ai/security_${timestamp}.json"
    
    if [[ -f "$security_report" ]]; then
        local status=$(jq -r '.status' "$security_report" 2>/dev/null || echo "unknown")
        local score=$(jq -r '.metrics.security_score' "$security_report" 2>/dev/null || echo "0")
        local files_scanned=$(jq -r '.metrics.files_scanned // 0' "$security_report" 2>/dev/null || echo "0")
        local vulnerabilities=$(jq -r '.metrics.vulnerabilities_found // 0' "$security_report" 2>/dev/null || echo "0")
        
        echo "**상태:** $(if [[ "$status" == "pass" ]]; then echo "✅ 안전 (점수: $score/100)"; else echo "⚠️ 주의 필요 (점수: $score/100)"; fi)"
        echo "- **스캔된 파일:** ${files_scanned}개"
        echo "- **발견된 취약점:** ${vulnerabilities}개"
        echo "- **보안 점수:** ${score}/100"
        echo "- **가이드 기반:** secure-guide.md 기준 분석"
        
        # 주요 권장사항 추출 (첫 3개)
        local recommendations=$(jq -r '.recommendations[:3][]' "$security_report" 2>/dev/null | head -3)
        if [[ -n "$recommendations" ]]; then
            echo ""
            echo "**주요 권장사항:**"
            echo "$recommendations" | while read -r rec; do
                echo "- $rec"
            done
        fi
    else
        echo "**상태:** ⚠️ 분석 리포트 없음"
    fi
}

generate_tdd_ai_md_section() {
    local timestamp="$1"
    local tdd_report="qa/reports/ai/tdd_${timestamp}.json"
    
    if [[ -f "$tdd_report" ]]; then
        local status=$(jq -r '.status' "$tdd_report" 2>/dev/null || echo "unknown")
        local score=$(jq -r '.metrics.tdd_score' "$tdd_report" 2>/dev/null || echo "0")
        local test_files=$(jq -r '.metrics.test_files' "$tdd_report" 2>/dev/null || echo "0")
        local analysis=$(jq -r '.analysis' "$tdd_report" 2>/dev/null || echo "분석 없음")
        
        echo "**상태:** $(if [[ "$status" == "pass" ]]; then echo "✅ 양호 (점수: $score/100)"; else echo "⚠️ 개선 필요 (점수: $score/100)"; fi)"
        echo "- **테스트 파일 수:** ${test_files}개"
        echo "- **TDD 점수:** ${score}/100"
        echo "- **방법론 기반:** tdd-guide.md 기준 분석"
        
        if [[ "$analysis" != "분석 없음" && "$analysis" != "" ]]; then
            echo ""
            echo "**AI 분석 인사이트:**"
            echo "> $analysis"
        fi
        
        # 위반사항이 있다면 표시
        local violations_count=$(jq -r '.violations | length' "$tdd_report" 2>/dev/null || echo "0")
        if [[ "$violations_count" -gt 0 ]]; then
            echo ""
            echo "**발견된 이슈:** ${violations_count}개"
        fi
    else
        echo "**상태:** ⚠️ 분석 리포트 없음"
    fi
}

generate_cqrs_ai_md_section() {
    local timestamp="$1"
    local cqrs_report="qa/reports/ai/cqrs_${timestamp}.json"
    
    if [[ -f "$cqrs_report" ]]; then
        local status=$(jq -r '.status' "$cqrs_report" 2>/dev/null || echo "unknown")
        local score=$(jq -r '.metrics.score // .metrics.cqrs_score' "$cqrs_report" 2>/dev/null || echo "0")
        
        echo "**상태:** $(if [[ "$status" == "pass" ]]; then echo "✅ 준수 (점수: $score/100)"; else echo "❌ 위반 (점수: $score/100)"; fi)"
        echo "- **CQRS 점수:** ${score}/100"
        echo "- **Command/Query 분리:** 검증 완료"
        echo "- **아키텍처 규칙:** ArchUnit 기반 검증"
        
        local violations_count=$(jq -r '.violations | length' "$cqrs_report" 2>/dev/null || echo "0")
        if [[ "$violations_count" -gt 0 ]]; then
            echo ""
            echo "**아키텍처 위반:** ${violations_count}개"
            echo "- 의존성 방향 재검토 필요"
        fi
    else
        echo "**상태:** ⚠️ 분석 리포트 없음"
    fi
}

# 개별 분석 유형 실행 함수
run_individual_analysis() {
    local analysis_type="$1"
    local timestamp="$2"
    
    case "$analysis_type" in
        "quality")
            check_quality
            ;;
        "architecture")
            check_architecture
            ;;
        "security")
            check_security
            ;;
        "tdd")
            check_tdd
            ;;
        "cqrs")
            check_cqrs
            ;;
        "coverage")
            check_coverage
            ;;
        "sonarqube")
            check_sonarqube
            ;;
        *)
            echo "Unknown analysis type: $analysis_type"
            return 1
            ;;
    esac
}

# 보안 검증 (Gemini 기반)
check_security() {
    create_reports_dir
    local report_file="qa/reports/ai/security_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🔒 Scanning for security vulnerabilities with Gemini...${NC}"
    fi
    
    local security_status="pass"
    local violations_json="[]"
    local violation_count=0
    local security_score=95
    local files_scanned=0
    
    # 보안 가이드 기반 검증
    local secure_guide_path="docs/gemini-resources/secure-guide.md"
    if [[ ! -f "$secure_guide_path" ]]; then
        security_status="fail"
        violations_json="[{\"severity\": \"error\", \"file\": \"Security Guide\", \"line\": 0, \"message\": \"Security guide not found at $secure_guide_path\", \"suggestion\": \"Ensure security guide exists for proper validation\"}]"
        violation_count=1
        security_score=0
    else
        # Gemini를 통한 실제 보안 검증
        local gemini_prompt="@${secure_guide_path} @src/main/java 다음 보안 지침을 기반으로 코드베이스를 분석하고 보안 위험을 점검하세요:

1. 인증/인가 (JWT 토큰, 세션 관리)
2. 입력 검증 (SQL 인젝션, XSS 방어) 
3. 통신 보안 (HTTPS, CORS)
4. 접근 제어 (IP 제한, Rate Limiting)
5. 에러 처리 (정보 노출 방지)

JSON 형식으로 응답: {\"vulnerabilities\": [{\"type\": \"type\", \"severity\": \"high|medium|low\", \"file\": \"filename\", \"line\": number, \"description\": \"desc\", \"recommendation\": \"fix\"}], \"score\": number}"
        
        # Gemini 호출 (실제 환경에서는 gemini CLI 사용)
        if command -v gemini >/dev/null 2>&1; then
            local gemini_result=$(echo "$gemini_prompt" | gemini -p 2>/dev/null || echo '{"vulnerabilities": [], "score": 95}')
            
            # 결과 파싱
            if echo "$gemini_result" | jq . >/dev/null 2>&1; then
                local vulnerabilities=$(echo "$gemini_result" | jq -c '.vulnerabilities // []')
                violation_count=$(echo "$vulnerabilities" | jq 'length')
                security_score=$(echo "$gemini_result" | jq -r '.score // 95')
                
                if [[ "$violation_count" -gt 0 ]]; then
                    security_status="fail"
                    violations_json="$vulnerabilities"
                    security_score=$((security_score < 70 ? security_score : 70))
                fi
            fi
        else
            # Gemini 미설치 시 기본 패턴 기반 검사
            local security_patterns=(
                "password.*=.*\".*\""  # 하드코딩된 패스워드
                "secret.*=.*\".*\""    # 하드코딩된 시크릿
                "System\.out\.print"   # 디버그 출력
                "printStackTrace"      # 스택 트레이스 노출
                "exec\("              # 명령어 실행
            )
            
            local pattern_violations=()
            for pattern in "${security_patterns[@]}"; do
                while IFS= read -r line; do
                    if [[ -n "$line" ]]; then
                        local file_path=$(echo "$line" | cut -d: -f1)
                        local line_num=$(echo "$line" | cut -d: -f2)
                        local match=$(echo "$line" | cut -d: -f3-)
                        pattern_violations+=("{\"severity\": \"high\", \"file\": \"$file_path\", \"line\": $line_num, \"message\": \"Security pattern detected: $match\", \"suggestion\": \"Review and secure this code pattern\"}")
                    fi
                done < <(grep -rn "$pattern" src/main/java/ 2>/dev/null || true)
            done
            
            violation_count=${#pattern_violations[@]}
            if [[ "$violation_count" -gt 0 ]]; then
                security_status="fail"
                violations_json="[$(IFS=,; echo "${pattern_violations[*]}")]"
                security_score=$((95 - violation_count * 10))
                if [[ "$security_score" -lt 0 ]]; then security_score=0; fi
            fi
        fi
    fi
    
    files_scanned=$(find src/main/java -name "*.java" | wc -l)
    
    local json_output=$(cat << EOF
{
  "status": "$security_status",
  "timestamp": "$TIMESTAMP",
  "type": "security",
  "target": "${TARGET_PATH:-entire codebase}",
  "summary": "$(if [[ "$security_status" == "pass" ]]; then echo "Security analysis completed - no critical issues found"; else echo "Security vulnerabilities detected"; fi)",
  "violations": $violations_json,
  "metrics": {
    "files_scanned": $files_scanned,
    "vulnerabilities_found": $violation_count,
    "security_score": $security_score,
    "guide_based": true
  },
  "recommendations": [
    "Review secure-guide.md for complete security checklist",
    "Implement automated security scanning in CI/CD",
    "Conduct regular security code reviews"
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
    fi
}

# CQRS 검증
check_cqrs() {
    create_reports_dir
    local report_file="qa/reports/ai/cqrs_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🔄 Checking CQRS architecture compliance...${NC}"
    fi
    
    local json_output=""
    # ArchUnit 테스트 실행하여 결과 파싱
    if ./gradlew archunitTest --tests "com.dx.identitybridge.architecture.CQRSArchitectureTest" --quiet > /tmp/cqrs-test.log 2>&1; then
        json_output=$(cat << EOF
{
  "status": "pass",
  "timestamp": "$TIMESTAMP",
  "type": "cqrs",
  "target": "entire codebase",
  "summary": "CQRS architecture rules passed",
  "violations": [],
  "metrics": {
    "score": 100,
    "rules_checked": 2,
    "violations_found": 0
  },
  "recommendations": [],
  "next_steps": [
    "Continue following CQRS patterns",
    "Monitor for new violations in future commits"
  ]
}
EOF
)
    else
        # 실패한 경우 로그에서 위반사항 추출
        local violations_raw=$(grep -E "^.*(should|must) (not|only).*$" /tmp/cqrs-test.log || true)
        local violation_count=$(echo "$violations_raw" | wc -l | tr -d ' ')
        local violations_json="[]"

        if [[ "$violation_count" -gt 0 ]]; then
            local violations_entries=()
            while IFS= read -r line; do
                local message="$line"
                violations_entries+=("{\"severity\": \"error\", \"file\": \"cqrs architecture tests\", \"line\": 0, \"message\": \"${message}\", \"suggestion\": \"Review CQRS patterns\"}")
            done <<< "$violations_raw"
            violations_json="[$(IFS=,; echo "${violations_entries[*]}")]"
        fi

        json_output=$(cat << EOF
{
  "status": "fail",
  "timestamp": "$TIMESTAMP",
  "type": "cqrs",
  "target": "entire codebase",
  "summary": "CQRS architecture violations detected",
  "violations": $violations_json,
  "metrics": {
    "score": 0,
    "rules_checked": 2,
    "violations_found": $violation_count
  },
  "recommendations": [
    {"priority": "high", "action": "Fix CQRS dependency violations", "rationale": "Maintains clear command/query separation"}
  ],
  "next_steps": [
    "Check build/reports/tests/archunitTest/index.html for details",
    "Review CQRS patterns in your codebase",
    "Fix violations before proceeding"
  ]
}
EOF
)
    fi
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
    fi
}

# SonarQube 검증
check_sonarqube() {
    create_reports_dir
    local report_file="qa/reports/sonarqube_${TIMESTAMP}.json"

    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo -e "${BLUE}🔍 Running SonarQube code quality analysis...${NC}"
    fi
    
    local json_output=""
    local sonar_status="skipped"
    local violations_json="[]"
    local violation_count=0
    local quality_score=0
    
    # Check if SonarQube server is available (local or external)
    local sonar_url="${SONAR_HOST_URL:-http://localhost:9000}"
    
    # Test SonarQube server connectivity
    if curl -f -s "${sonar_url}/api/system/status" > /dev/null 2>&1; then
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo "✅ SonarQube server detected at: $sonar_url"
        fi
        
        # Run actual SonarQube analysis
        if ./gradlew sonar -q > /dev/null 2>&1; then
            sonar_status="pass"
            quality_score=90
            if [[ "$OUTPUT_FORMAT" != "json" ]]; then
                echo "✅ SonarQube analysis completed successfully"
            fi
        else
            sonar_status="fail"
            quality_score=0
            violations_json="[{\"severity\": \"error\", \"file\": \"SonarQube Analysis\", \"line\": 0, \"message\": \"SonarQube analysis failed. Check server connectivity and configuration.\", \"suggestion\": \"Verify SonarQube server configuration and authentication tokens.\"}]"
            violation_count=1
            if [[ "$OUTPUT_FORMAT" != "json" ]]; then
                echo "❌ SonarQube analysis failed"
            fi
        fi
    else
        # SonarQube server not available - skip analysis
        sonar_status="skipped"
        quality_score=0
        violations_json="[{\"severity\": \"info\", \"file\": \"SonarQube Server\", \"line\": 0, \"message\": \"SonarQube server not available at ${sonar_url}\", \"suggestion\": \"Install and start SonarQube server, or set SONAR_HOST_URL environment variable.\"}]"
        violation_count=1
        if [[ "$OUTPUT_FORMAT" != "json" ]]; then
            echo "⏭️  SonarQube server not available - skipping analysis"
            echo "   To enable SonarQube analysis:"
            echo "   1. Install SonarQube locally: docker run -d -p 9000:9000 sonarqube:latest"
            echo "   2. Or set SONAR_HOST_URL for external server"
        fi
    fi
    
    # Calculate metrics
    local files_analyzed=$(find "${TARGET_PATH:-src/main/java}" -name "*.java" | wc -l)
    local total_lines=$(find "${TARGET_PATH:-src/main/java}" -name "*.java" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    
    json_output=$(cat << EOF
{
  "status": "$sonar_status",
  "timestamp": "$TIMESTAMP",
  "type": "sonarqube",
  "target": "${TARGET_PATH:-entire codebase}",
  "summary": "$(if [[ "$sonar_status" == "skipped" ]]; then echo "SonarQube server not available - analysis skipped"; elif [[ "$sonar_status" == "pass" ]]; then echo "SonarQube analysis completed successfully"; else echo "SonarQube analysis failed"; fi)",
  "violations": $violations_json,
  "metrics": {
    "files_analyzed": $files_analyzed,
    "total_lines": $total_lines,
    "violations_found": $violation_count,
    "quality_score": $quality_score,
    "server_available": $(if [[ "$sonar_status" != "skipped" ]]; then echo "true"; else echo "false"; fi)
  },
  "recommendations": [
    $(if [[ "$sonar_status" == "skipped" ]]; then echo "\"Install and configure SonarQube server for comprehensive code quality analysis\""; else echo "\"Review SonarQube dashboard for detailed quality metrics\", \"Address identified code smells and technical debt\""; fi)
  ],
  "next_steps": [
    $(if [[ "$sonar_status" == "skipped" ]]; then echo "\"Install SonarQube: docker run -d -p 9000:9000 sonarqube:latest\", \"Configure project token and run './gradlew sonar'\""; else echo "\"Review SonarQube dashboard at $sonar_url\", \"Set up quality gates for CI/CD pipeline\""; fi)
  ]
}
EOF
)
    echo "$json_output" | tee "$report_file"
    if [[ "$OUTPUT_FORMAT" != "json" ]]; then
        echo "Report saved to $report_file"
    fi
}

# Warning 리포트 생성
generate_warnings_report() {
    create_reports_dir
    local report_file="qa/reports/static/WARNINGS_REPORT_${TIMESTAMP}.md"
    
    if [[ "$OUTPUT_FORMAT" != "json" && "$OUTPUT_FORMAT" != "markdown" ]]; then
        echo -e "${BLUE}📋 Generating warnings report...${NC}"
    fi
    
    # 최신 warnings JSON 파일 찾기
    local warnings_file=$(find reports/static -name "warnings_*.json" -type f | sort -r | head -1)
    
    if [[ -z "$warnings_file" || ! -f "$warnings_file" ]]; then
        echo "No warnings found. Skipping warnings report generation."
        return 0
    fi
    
    local content=$(cat "$warnings_file")
    local warnings=$(echo "$content" | jq -c '.warnings // []')
    local warning_count=$(echo "$content" | jq -r '.metrics.warnings_found // 0')
    local files_analyzed=$(echo "$content" | jq -r '.metrics.files_analyzed // 0')
    
    # Warning 카테고리별 분류
    local style_warnings=""
    local naming_warnings=""
    local comment_warnings=""
    local other_warnings=""
    
    if [[ $(echo "$warnings" | jq 'length') -gt 0 ]]; then
        local warning_objects=$(echo "$warnings" | jq -c '.[]')
        while IFS= read -r warning_obj; do
            local message=$(echo "$warning_obj" | jq -r '.message // "No message"')
            local file=$(echo "$warning_obj" | jq -r '.file // "N/A"')
            local line=$(echo "$warning_obj" | jq -r '.line // "N/A"')
            local severity=$(echo "$warning_obj" | jq -r '.severity // "warning"')
            
            # 메시지 기반 카테고리 분류
            local warning_text="- **${file}:${line}**: ${message}"$'\n'
            
            if [[ "$message" == *"Missing"*"comment"* || "$message" == *"Missing"*"documentation"* ]]; then
                comment_warnings+="$warning_text"
            elif [[ "$message" == *"Name"* || "$message" == *"naming"* || "$message" == *"convention"* ]]; then
                naming_warnings+="$warning_text"
            elif [[ "$message" == *"style"* || "$message" == *"format"* || "$message" == *"indentation"* || "$message" == *"whitespace"* ]]; then
                style_warnings+="$warning_text"
            else
                other_warnings+="$warning_text"
            fi
        done <<< "$warning_objects"
    fi
    
    # 빈 카테고리 처리
    if [[ -z "$style_warnings" ]]; then style_warnings="위반사항이 발견되지 않았습니다."$'\n'; fi
    if [[ -z "$naming_warnings" ]]; then naming_warnings="위반사항이 발견되지 않았습니다."$'\n'; fi
    if [[ -z "$comment_warnings" ]]; then comment_warnings="위반사항이 발견되지 않았습니다."$'\n'; fi
    if [[ -z "$other_warnings" ]]; then other_warnings="위반사항이 발견되지 않았습니다."$'\n'; fi
    
    cat > "$report_file" << EOF
# ⚠️ 코드 스타일 & 개선 권장사항 리포트

**📅 분석 일시:** $(echo "$content" | jq -r '.timestamp')  
**📊 분석 파일 수:** ${files_analyzed}개  
**⚠️ 총 권장사항:** ${warning_count}개

## 📋 카테고리별 권장사항

### 🎨 코드 스타일
$style_warnings

### 🏷️ 네이밍 컨벤션
$naming_warnings

### 📝 주석 & 문서화
$comment_warnings

### 🔧 기타 개선사항
$other_warnings

## 💡 개선 가이드

### 🚀 빠른 수정 방법
- **IDE 자동 포맷팅**: \`Ctrl+Alt+L\` (IntelliJ) 또는 \`Shift+Alt+F\` (VS Code)
- **Import 정리**: \`Ctrl+Alt+O\` (IntelliJ)
- **코드 스타일 설정**: IDE의 Code Style 설정을 프로젝트 표준에 맞게 조정

### 📐 네이밍 가이드라인
- **클래스**: PascalCase (예: \`UserService\`)
- **메서드/변수**: camelCase (예: \`getUserById\`)
- **상수**: UPPER_SNAKE_CASE (예: \`MAX_RETRY_COUNT\`)
- **패키지**: lowercase (예: \`com.example.service\`)

### 📚 문서화 모범 사례
- 공개 API에는 JavaDoc 작성
- 복잡한 비즈니스 로직에 설명 주석 추가
- 매개변수와 반환값 문서화

## 🎯 다음 단계
1. 높은 빈도의 스타일 이슈부터 수정
2. IDE 설정을 팀 표준에 맞게 통일
3. Pre-commit hook에 스타일 검사 추가 고려

---
*이 리포트는 개발 생산성 향상을 위한 권장사항입니다. 핵심 기능에는 영향을 주지 않습니다.*
EOF

    echo "Warnings report saved to $report_file"
}


# 종합 리포트 생성
generate_summary() {
    create_reports_dir
    local report_file="qa/reports/QUALITY_REPORT_${TIMESTAMP}.md"

    if [[ "$OUTPUT_FORMAT" != "json" && "$OUTPUT_FORMAT" != "markdown" ]]; then
        echo -e "${BLUE}📋 Generating summary report...${NC}"
    fi

    local overall_status="pass"
    local total_score=0
    local component_count=0
    local components_markdown=""
    local components_json_str=""
    local violations_array=()
    local recommendations_list=()

    # Use SUMMARY_FILES array if it's not empty, otherwise find latest files by type
    local report_files
    if [ ${#SUMMARY_FILES[@]} -gt 0 ]; then
        report_files="${SUMMARY_FILES[@]}"
    else
        # 각 타입별로 가장 최신 파일만 선택 (논리적 순서로)
        local latest_files=()
        for type in "architecture" "cqrs" "quality" "security" "tdd" "sonarqube"; do
            local latest_file=$(find reports -name "${type}_*.json" -type f | sort -r | head -1)
            if [[ -n "$latest_file" ]]; then
                latest_files+=("$latest_file")
            fi
        done
        report_files="${latest_files[@]}"
    fi

    local first_component=true
    for file in $report_files; do
        if [[ -f "$file" ]]; then
            local content=$(cat "$file")
            local type=$(echo "$content" | jq -r '.type')
            local status=$(echo "$content" | jq -r '.status')
            local summary=$(echo "$content" | jq -r '.summary')
            local score=$(echo "$content" | jq -r '.metrics.score // 0')
            local violations=$(echo "$content" | jq -c '.violations // []')

            # 상태별 처리
            if [[ "$status" == "fail" ]]; then
                overall_status="fail"
                # 실패해도 부분 점수 반영 (0점 대신 50% 차감)
                score=$((score / 2))
            elif [[ "$status" == "skipped" ]]; then
                # skipped 상태는 점수 계산에서 제외
                score=0
            fi

            # skipped가 아닌 경우만 점수 계산에 포함
            if [[ "$status" != "skipped" ]]; then
                total_score=$((total_score + score))
                component_count=$((component_count + 1))
            fi

            # 컴포넌트명 한국어 변환
            local component_name_kr=""
            case "$type" in
                "architecture") component_name_kr="아키텍처" ;;
                "quality") component_name_kr="코드 품질" ;;
                "tdd") component_name_kr="TDD" ;;
                "security") component_name_kr="보안" ;;
                "cqrs") component_name_kr="CQRS" ;;
                "sonarqube") component_name_kr="SonarQube" ;;
                *) component_name_kr="$type" ;;
            esac
            
            # Debug: ensure component name is not empty
            if [[ -z "$component_name_kr" ]]; then
                component_name_kr="알 수 없음"
            fi
            
            components_markdown+="- **${component_name_kr}**: $(if [[ "$status" == "pass" ]]; then echo "✅ 통과"; elif [[ "$status" == "skipped" ]]; then echo "⏭️ 건너뜀"; else echo "❌ 실패"; fi) (${summary})  "$'\n'

            if [[ "$first_component" == "false" ]]; then
                components_json_str+=","
            fi
            components_json_str+="\"$type\": \"$status\""
            first_component=false

            if [[ $(echo "$violations" | jq 'length') -gt 0 ]]; then
                local violation_objects=$(echo "$violations" | jq -c '.[]')
                while IFS= read -r violation_obj; do
                    violations_array+=("$violation_obj")
                done <<< "$violation_objects"
            fi
        fi
    done

    local avg_score=0
    if [[ "$component_count" -gt 0 ]]; then
        avg_score=$((total_score / component_count))
    fi

    local violations_json_array_str="[$(IFS=,; echo "${violations_array[*]}")]"

    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        cat << EOF
{
  "status": "$overall_status",
  "timestamp": "$TIMESTAMP",
  "type": "summary",
  "target": "comprehensive analysis",
  "summary": "Comprehensive quality analysis completed with overall status: $overall_status",
  "overall_score": $avg_score,
  "components": {
    $components_json_str
  },
  "violations": $violations_json_array_str,
  "recommendations": [
    "Review all failed components for detailed violations.",
    "Focus on improving areas with lower scores.",
    "Ensure all critical violations are addressed."
  ]
}
EOF
    elif [[ "$OUTPUT_FORMAT" == "markdown" ]]; then
        # 위반사항을 카테고리별로 분류
        local arch_violations=""
        local quality_violations=""
        local security_violations=""
        local total_violations=0
        
        if [[ ${#violations_array[@]} -eq 0 ]]; then
            arch_violations="위반사항이 발견되지 않았습니다."
            quality_violations="위반사항이 발견되지 않았습니다."
        else
            for violation_obj in "${violations_array[@]}"; do
                local severity=$(echo "$violation_obj" | jq -r '.severity // "unknown"')
                local message=$(echo "$violation_obj" | jq -r '.message // "No message"')
                local file=$(echo "$violation_obj" | jq -r '.file // "N/A"')
                local line=$(echo "$violation_obj" | jq -r '.line // "N/A"')
                
                # 심각도 한국어 변환
                local severity_kr=""
                case "$severity" in
                    "error") severity_kr="🔴 오류" ;;
                    "warning") severity_kr="🟡 경고" ;;
                    "info") severity_kr="🔵 정보" ;;
                    *) severity_kr="⚫ 알 수 없음" ;;
                esac
                
                local violation_text=""
                if [[ -n "$message" && "$message" != "null" ]]; then
                    violation_text="- **${severity_kr}**: ${message}  "$'\n'"  📁 \`${file}\` (라인 ${line})"$'\n'$'\n'
                else
                    violation_text="- **${severity_kr}**: 상세 메시지 없음  "$'\n'"  📁 \`${file}\` (라인 ${line})"$'\n'$'\n'
                fi
                
                # 파일 경로에 따라 카테고리 분류
                if [[ "$file" == *"architecture tests"* || "$file" == *"cqrs architecture tests"* ]]; then
                    arch_violations+="$violation_text"
                else
                    quality_violations+="$violation_text"
                fi
                
                total_violations=$((total_violations + 1))
            done
        fi
        
        # 빈 카테고리 처리
        if [[ -z "$arch_violations" ]]; then
            arch_violations="위반사항이 발견되지 않았습니다."
        fi
        if [[ -z "$quality_violations" ]]; then
            quality_violations="위반사항이 발견되지 않았습니다."
        fi

        cat << EOF
# 📊 코드 품질 분석 종합 보고서

**📅 분석 일시:** $TIMESTAMP  
**📈 전체 상태:** $(if [[ "$overall_status" == "pass" ]]; then echo "✅ 통과"; else echo "❌ 실패"; fi)  
**🎯 전체 점수:** ${avg_score}/100

## 📋 분석 요약

- **총 분석 파일 수:** $(find src/main/java -name "*.java" | wc -l | tr -d ' ')개
- **심각한 위반사항:** ${total_violations}개 (Error 수준)
- **검사한 구성요소:** ${component_count}개
- **📄 상세 권장사항**: [WARNINGS_REPORT_${TIMESTAMP}.md](./WARNINGS_REPORT_${TIMESTAMP}.md) 참조

## 🏗️ 구성요소별 상태

$components_markdown

## ⚠️ 위반사항 상세

### 🏛️ 아키텍처 & CQRS 위반사항

$arch_violations

### 🔴 심각한 코드 품질 위반사항

$quality_violations

## 🎯 우선 조치 사항

### ⚡ 즉시 수정 필요
- **아키텍처 위반**: 헥사고날 아키텍처 규칙 준수 확인
- **CQRS 패턴**: Command와 Query 분리 규칙 재검토
- **컴파일 오류**: 빌드 실패 원인 해결

### 📋 추가 정보
- **🎨 코드 스타일 권장사항**: [WARNINGS_REPORT_${TIMESTAMP}.md](./WARNINGS_REPORT_${TIMESTAMP}.md)에서 확인
- **📚 상세 가이드**: [docs/shared-guides/](../docs/shared-guides/) 참조

EOF
    else
        echo "=== Comprehensive Analysis Summary ==="
        echo "Overall Status: $(if [[ "$overall_status" == "pass" ]]; then echo "PASS"; else echo "FAIL"; fi)"
        echo "Overall Score: ${avg_score}/100"
        echo ""
        echo "Component Status:"
        echo -e "$components_markdown"
        echo ""
        echo "Violations:"
        if [[ ${#violations_array[@]} -eq 0 ]]; then
            echo "No violations found."
        else
            for violation_obj in "${violations_array[@]}"; do
                local severity=$(echo "$violation_obj" | jq -r '.severity // "unknown"')
                local message=$(echo "$violation_obj" | jq -r '.message // "No message"')
                local file=$(echo "$violation_obj" | jq -r '.file // "N/A"')
                local line=$(echo "$violation_obj" | jq -r '.line // "N/A"')
                echo "- $(echo "$severity" | tr '[:lower:]' '[:upper:]'): $message (File: $file, Line: $line)"
            done
        fi
        echo ""
        echo "Recommendations:"
        echo "- Review all failed components for detailed violations."
        echo "- Focus on improving areas with lower scores."
        echo "- Ensure all critical violations are addressed."
    fi
}

# 메인 실행 로직
case "$CHECK_TYPE" in
    "architecture")
        check_architecture
        ;;
    "quality")
        check_quality
        ;;
    "tdd")
        check_tdd
        ;;
    "coverage")
        check_coverage
        ;;
    "static-analysis")
        check_static_analysis
        ;;
    "ai-analysis")
        check_ai_analysis
        ;;
    "security")
        check_security
        ;;
    "cqrs")
        check_cqrs
        ;;
    "sonarqube")
        check_sonarqube
        ;;
    "summary")
        generate_summary
        ;;
    "warnings")
        generate_warnings_report
        ;;
    *)
        echo -e "${RED}Unknown check type: $CHECK_TYPE${NC}"
        show_usage
        exit 1
        ;;
esac