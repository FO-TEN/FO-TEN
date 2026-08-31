#!/usr/bin/env sh
#
# 커밋의 작성자(author)·커밋터(committer) 신원과 공동 작성자 트레일러를 검사한다.
#
# GitHub 은 커밋의 author, committer, 그리고 Co-authored-by 트레일러를 모두 읽어
# 기여자로 집계한다. 개발 보조 도구 이름이 여기 들어가면 저장소 기여자 목록에 남는다.
# 도구 설정에만 기대지 않고 git 단에서 한 번 더 막는다.
#
# 사용법
#   sh scripts/validate-commit-author.sh                    # origin/develop..HEAD
#   sh scripts/validate-commit-author.sh origin/main..HEAD  # 범위 지정
#
# 오탐이 나면(예: 팀원 이름이 패턴에 걸림) 아래 pattern 을 좁히고,
# 왜 좁혔는지 주석으로 남길 것.

set -eu

range="${1:-}"

if [ -z "$range" ]; then
  if git rev-parse --verify --quiet origin/develop >/dev/null 2>&1; then
    range='origin/develop..HEAD'
  else
    range='HEAD'
  fi
fi

pattern='claude|anthropic|copilot|codeium|windsurf|devin|cursor|\[bot\]|noreply@anthropic'

status=0
checked=0

for sha in $(git rev-list "$range" 2>/dev/null); do
  checked=$((checked + 1))

  an=$(git log -1 --format='%an' "$sha")
  ae=$(git log -1 --format='%ae' "$sha")
  cn=$(git log -1 --format='%cn' "$sha")
  ce=$(git log -1 --format='%ce' "$sha")
  subject=$(git log -1 --format='%h %s' "$sha")

  for field in "author 이름:$an" "author 메일:$ae" "committer 이름:$cn" "committer 메일:$ce"; do
    label=${field%%:*}
    value=${field#*:}
    if printf '%s' "$value" | grep -Eqi "$pattern"; then
      printf '%s\n' "차단됨: $subject" >&2
      printf '%s\n' "  $label 에 개발 보조 도구 이름이 있습니다: $value" >&2
      status=1
    fi
  done

  # Co-authored-by 트레일러. GitHub 이 이 줄을 읽어 공동 기여자로 집계한다.
  if git log -1 --format='%B' "$sha" | grep -Ei '^[[:space:]]*Co-authored-by:' | grep -Eqi "$pattern"; then
    printf '%s\n' "차단됨: $subject" >&2
    printf '%s\n' '  Co-authored-by 트레일러에 개발 보조 도구가 있습니다:' >&2
    git log -1 --format='%B' "$sha" | grep -Ei '^[[:space:]]*Co-authored-by:' | sed 's/^/    /' >&2
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  {
    echo
    echo '고치는 방법'
    echo '  가장 최근 커밋 하나면:'
    echo '    git commit --amend --author="이름 <메일>"      # 작성자 교체'
    echo '    git commit --amend                              # 편집기에서 Co-authored-by 줄 삭제'
    echo
    echo '  여러 커밋이면:'
    echo '    git rebase -i <기준커밋>                        # 해당 커밋을 edit 으로 바꿔 수정'
    echo
    echo '  내 git 설정부터 확인:'
    echo '    git config user.name'
    echo '    git config user.email'
  } >&2
  exit 1
fi

printf '%s\n' "커밋 작성자 확인 완료 ($checked건)"
