#!/usr/bin/env bash
# 커밋·PR·이슈에 AI 도구 서명이 섞이는 것을 차단합니다.
# 저장소 커밋 로그와 GitHub contributor 목록에 AI 가 잡히면 안 됩니다.
# .claude/settings.json 의 PreToolUse 훅으로 등록해서 사용합니다.
#
# 방어선은 셋입니다:
#   1) .claude/settings.json 의 attribution 설정 (자동 삽입 자체를 끔)
#   2) 이 훅                                     (수동으로 넣는 경우를 차단)
#   3) scripts/validate-commit-message.sh        (lefthook commit-msg. 사람이 쳐도 차단)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-extract-command.sh
. "$SCRIPT_DIR/lib-extract-command.sh"

INPUT=$(cat)
CMD=$(extract_command "$INPUT")

AI_PATTERN='Co-authored-by:[^"]{0,80}(claude|copilot|cursor|gemini|anthropic)|Generated with .{0,3}Claude|claude\.ai/code|🤖'

case "$CMD" in
  *"git commit"*|*"gh pr create"*|*"gh pr edit"*|*"gh issue create"*)
    if printf '%s' "$CMD" | grep -Eqi "$AI_PATTERN"; then
      echo "차단됨: 커밋/PR/이슈 본문에 AI 도구 서명이 포함되어 있습니다." >&2
      echo "Co-Authored-By, 'Generated with Claude Code', 🤖, claude.ai/code 를 제거하고 다시 실행하세요." >&2
      exit 2
    fi
    ;;
esac

# --no-verify 로 컨벤션 검증 훅을 건너뛰는 것을 막습니다.
case "$CMD" in
  *"git commit"*|*"git push"*)
    if printf '%s' "$CMD" | grep -Eq '(^|[[:space:]])(--no-verify|-n)([[:space:]]|$)'; then
      echo "차단됨: --no-verify 로 컨벤션 검증 훅을 건너뛸 수 없습니다." >&2
      echo "커밋 메시지나 브랜치명을 컨벤션에 맞게 고치세요." >&2
      echo "  git branch -m be/feat/12-goal-simulation   # 브랜치명 수정" >&2
      echo "  git commit --amend                         # 직전 커밋 메시지 수정" >&2
      exit 2
    fi
    ;;
esac

exit 0
