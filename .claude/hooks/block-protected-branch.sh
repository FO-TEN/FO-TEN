#!/usr/bin/env bash
# main / develop 브랜치에서의 직접 커밋·푸시를 차단합니다.
# .claude/settings.json 의 PreToolUse 훅으로 등록해서 사용합니다.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-extract-command.sh
. "$SCRIPT_DIR/lib-extract-command.sh"

INPUT=$(cat)
CMD=$(extract_command "$INPUT")

case "$CMD" in
  *"git commit"*|*"git push"*)
    BRANCH=$(git branch --show-current 2>/dev/null || echo "")
    if [ "$BRANCH" = "main" ] || [ "$BRANCH" = "develop" ]; then
      echo "차단됨: 보호 브랜치 '$BRANCH' 에서는 직접 커밋/푸시할 수 없습니다." >&2
      echo "먼저 작업 브랜치를 만드세요:" >&2
      echo "  git switch -c be/feat/<이슈번호>-<영문-요약>   (예: be/feat/12-goal-simulation)" >&2
      echo "  git switch -c fe/fix/<이슈번호>-<영문-요약>" >&2
      echo "  git switch -c chore/<이슈번호>-<영문-요약>     (파트 구분 없는 공통 작업)" >&2
      exit 2
    fi
    ;;
esac

exit 0
