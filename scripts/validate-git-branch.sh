#!/usr/bin/env sh
#
# 브랜치명 검증. lefthook 의 pre-push 훅에서 호출된다.
#
#   be/feat/goal-simulation           이슈 없이 바로 작업하는 경우 (기본)
#   be/fix/34-exchange-rate-display   이슈가 있는 경우 앞에 번호를 붙인다
#   chore/docker-compose              파트 구분이 없는 공통 작업
#   docs/setup-guide                  저장소 전체 문서

set -eu

branch="$(git branch --show-current)"

# 보호 브랜치와 detached HEAD 는 검사하지 않는다.
if [ -z "$branch" ] || [ "$branch" = 'develop' ] || [ "$branch" = 'main' ]; then
  exit 0
fi

pattern='^((fe|be)/(feat|fix|docs|style|refactor|test|chore|design|comment|rename|remove|hotfix|revert)|chore|docs)/([0-9]+-)?[a-z0-9]+(-[a-z0-9]+)*$'

if ! printf '%s\n' "$branch" | grep -Eq "$pattern"; then
  {
    echo '브랜치명 형식이 맞지 않습니다.'
    echo
    echo '  [fe|be]/접두어/기능명            예) be/feat/goal-simulation'
    echo '  chore/기능명                     예) chore/docker-compose      (설정 · CI · 빌드)'
    echo '  docs/기능명                      예) docs/setup-guide         (저장소 전체 문서)'
    echo
    echo '  이슈가 있으면 기능명 앞에 번호를 붙입니다.'
    echo '    예) be/fix/34-exchange-rate-display, chore/7-github-actions'
    echo
    echo '  접두어: feat fix docs style refactor test chore'
    echo '          design comment rename remove hotfix revert'
    echo '  기능명: 소문자·숫자·하이픈만. 영문 2~4단어'
    echo
    echo "  현재 브랜치: $branch"
    echo
    echo '  이름만 바꾸려면:  git branch -m <새 이름>'
  } >&2
  exit 1
fi
