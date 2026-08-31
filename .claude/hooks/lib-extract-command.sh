#!/usr/bin/env bash
# PreToolUse 훅의 stdin(JSON)에서 실행하려는 명령 문자열을 꺼냅니다.
#
# jq → python → 원문 순으로 시도합니다. 팀원 PC 에 jq 가 없어도 훅이 조용히
# 무력화되지 않게 하기 위함입니다.
#
# 주의: Windows 의 python3 는 Microsoft Store 설치 유도 스텁인 경우가 있어
#       stderr 로 안내문을 뱉고 실패합니다. stderr 를 버리고 종료 코드로 판정합니다.
#
# 마지막 원문 폴백은 오탐(예: 커밋과 무관한 파일에 금지 문자열이 들어간 경우)이
# 날 수 있지만, 검증이 꺼지는 것보다 낫다고 보고 fail-closed 로 둡니다.

extract_command() {
  local input="$1" out

  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null && return
  fi

  local py
  for py in python python3 py; do
    command -v "$py" >/dev/null 2>&1 || continue
    if out=$(printf '%s' "$input" | "$py" -c 'import sys, json
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(1)
sys.stdout.write((data.get("tool_input") or {}).get("command") or "")' 2>/dev/null); then
      printf '%s' "$out"
      return
    fi
  done

  printf '%s' "$input"
}
