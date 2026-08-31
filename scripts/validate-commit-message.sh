#!/usr/bin/env sh
#
# 커밋 메시지 검증. lefthook 의 commit-msg 훅에서 호출된다.
#
#   [be]feat: 목표 시뮬레이션 API 추가
#   [fe]fix: 환율 표시 소수점 오류 수정
#   chore: Docker Compose 구성 추가        (파트 구분이 없는 공통 작업)

set -eu

message_file="${1:?커밋 메시지 파일 경로가 필요합니다.}"
subject="$(sed -n '1p' "$message_file")"

# merge / revert 커밋은 Git 이 생성하므로 검증하지 않는다.
case "$subject" in
  'Merge '*|'Revert "'*) exit 0 ;;
esac

pattern='^(\[(fe|be)\](feat|fix|docs|style|refactor|test|chore|design|comment|rename|remove|hotfix|revert)|chore): .+$'

if ! printf '%s\n' "$subject" | grep -Eq "$pattern"; then
  printf '%s\n' '커밋 메시지 형식이 맞지 않습니다.' >&2
  printf '%s\n' '' >&2
  printf '%s\n' '  [fe|be]접두어: 기능명     예) [be]feat: 목표 시뮬레이션 API 추가' >&2
  printf '%s\n' '  chore: 기능명             예) chore: Docker Compose 구성 추가' >&2
  printf '%s\n' '' >&2
  printf '%s
' '  접두어: feat fix docs style refactor test chore' >&2
  printf '%s
' '          design comment rename remove hotfix revert' >&2
  printf '%s\n' '' >&2
  printf '%s\n' "  입력한 제목: $subject" >&2
  exit 1
fi

# 외부 도구가 자동으로 붙이는 공동 작성자 서명을 차단한다.
# 실제로 코드를 쓴 사람만 커밋 로그와 contributor 목록에 남아야 한다.
signature_pattern='Co-[Aa]uthored-[Bb]y:.*([Cc]laude|[Cc]opilot|[Cc]ursor|[Gg]emini|[Aa]nthropic|noreply@anthropic)|Generated with \[?Claude|claude\.ai/code|🤖 Generated'

if grep -Eq "$signature_pattern" "$message_file"; then
  printf '%s\n' '커밋 메시지에 AI 도구 서명이 포함되어 있습니다. 지우고 다시 커밋하세요.' >&2
  printf '%s\n' '' >&2
  grep -En "$signature_pattern" "$message_file" >&2
  exit 1
fi
