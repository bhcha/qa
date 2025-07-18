#!/bin/bash
# 개발 사이클 완료 후 종합 분석 실행 (정적분석 + AI분석 분리)

echo "🔍 Starting Gemini Comprehensive Analysis..."
echo "======================================"

# 결과 디렉토리 생성 및 기존 파일 정리
mkdir -p reports qa/reports/static reports/ai

# 기존 분석 결과 파일들 정리 (30일 이상 된 파일 삭제)
echo "🧹 Cleaning up old analysis reports..."
find reports -name "*.json" -type f -mtime +30 -delete 2>/dev/null || true
find reports -name "*.md" -type f -mtime +30 -delete 2>/dev/null || true

# 같은 날짜의 이전 결과 삭제 (중복 방지)
today=$(date +%Y%m%d)
today_iso=$(date +%Y-%m-%d)
find reports -name "*${today}_*.json" -type f -delete 2>/dev/null || true
find reports -name "*${today}_*.md" -type f -delete 2>/dev/null || true
find reports -name "*${today_iso}T*.json" -type f -delete 2>/dev/null || true
find reports -name "*${today_iso}T*.md" -type f -delete 2>/dev/null || true

# 각 타입별로 최대 2개 파일만 유지 (가장 오래된 것부터 삭제)
for report_type in "static_analysis" "ai_analysis"; do
    # reports/static과 reports/ai 디렉토리에서 각각 정리
    for subdir in "static" "ai"; do
        all_files=()
        while IFS= read -r -d '' file; do
            all_files+=("$file")
        done < <(find reports/${subdir} -name "${report_type}_20*.json" -type f -print0 2>/dev/null | sort -z)
        
        file_count=${#all_files[@]}
        if [[ "$file_count" -gt 2 ]]; then
            excess_count=$((file_count - 2))
            for ((i=0; i<excess_count; i++)); do
                rm -f "${all_files[i]}" 2>/dev/null || true
            done
        fi
    done
done

# 종합 리포트도 최대 3개만 유지
for report_type in "STATIC_ANALYSIS_REPORT" "AI_ANALYSIS_REPORT"; do
    all_reports=()
    while IFS= read -r -d '' file; do
        all_reports+=("$file")
    done < <(find reports -name "${report_type}_20*.md" -type f -print0 2>/dev/null | sort -z)
    
    report_count=${#all_reports[@]}
    if [[ "$report_count" -gt 3 ]]; then
        excess_count=$((report_count - 3))
        for ((i=0; i<excess_count; i++)); do
            rm -f "${all_reports[i]}" 2>/dev/null || true
        done
    fi
done

timestamp=$(date +%Y%m%d_%H%M%S)

# 1. 정적 분석 그룹 실행
echo "1/3 Running Static Analysis Group..."
./qa/scripts/gemini-check.sh static-analysis --all --output json

# 2. AI 분석 그룹 실행  
echo "2/3 Running AI Analysis Group..."
./qa/scripts/gemini-check.sh ai-analysis --all --output json

# 3. 종합 리포트 생성
echo "3/3 Generating Comprehensive Reports..."
# 가장 최근 생성된 파일들을 찾아서 요약 리포트 생성
latest_static=$(find qa/reports/static -name "static_analysis_*.json" -type f | sort | tail -1)
latest_ai=$(find qa/reports/ai -name "ai_analysis_*.json" -type f | sort | tail -1)

if [[ -f "$latest_static" ]]; then
    ./qa/scripts/gemini-check.sh summary "$latest_static" --format markdown > qa/reports/STATIC_ANALYSIS_REPORT_${timestamp}.md
fi

if [[ -f "$latest_ai" ]]; then
    ./qa/scripts/gemini-check.sh summary "$latest_ai" --format markdown > qa/reports/AI_ANALYSIS_REPORT_${timestamp}.md
fi

# 결과 요약 출력
echo ""
echo "✅ Analysis Complete!"
echo "======================================"
echo "Reports generated in qa//reports directory"
echo "📊 Static Analysis: qa/reports/STATIC_ANALYSIS_REPORT_${timestamp}.md"
echo "🤖 AI Analysis: qa/reports/AI_ANALYSIS_REPORT_${timestamp}.md"

# 주요 지표 출력
echo ""
echo "Key Metrics:"
# 정적 분석 메트릭
if [[ -f "$latest_static" ]]; then
    echo "📊 Static Analysis:"
    if jq -e '.total_violations' "$latest_static" > /dev/null 2>&1; then
        violations=$(jq -r '.total_violations' "$latest_static" 2>/dev/null)
        tools=$(jq -r '.tools_executed | length' "$latest_static" 2>/dev/null)
        echo "  Tools executed: $tools"
        echo "  Total violations: $violations"
    fi
else
    echo "  Static analysis report file not found"
fi

# AI 분석 메트릭  
if [[ -f "$latest_ai" ]]; then
    echo "🤖 AI Analysis:"
    if jq -e '.total_violations' "$latest_ai" > /dev/null 2>&1; then
        violations=$(jq -r '.total_violations' "$latest_ai" 2>/dev/null)
        types=$(jq -r '.analysis_types | length' "$latest_ai" 2>/dev/null)
        echo "  Analysis types: $types" 
        echo "  Total violations: $violations"
    fi
else
    echo "  AI analysis report file not found"
fi