---
description: 작업 설명(또는 이슈번호)을 받아 브랜치를 만들고 작업을 시작합니다
argument-hint: "[작업 설명 또는 #이슈번호]"
allowed-tools: Bash(gh issue view *), Bash(gh issue list *), Bash(git branch *), Bash(git switch *), Bash(git fetch *), Bash(git status *), Bash(git pull *)
---

## 요청 내용

$ARGUMENTS

## 현재 상태

- 현재 브랜치: !`git branch --show-current`
- 작업 트리: !`git status --short`
- 최근 브랜치: !`git branch --sort=-committerdate --format='%(refname:short)' | head -8`

---

## 할 일

### 1) 사전 확인

- 작업 트리에 커밋되지 않은 변경이 있으면 **브랜치를 만들지 말고** 먼저 알려줍니다. (커밋할지, stash할지 사용자가 정합니다)
- 요청 내용이 `#12` 처럼 이슈 번호면 `gh issue view 12` 로 내용을 읽고 그에 맞춰 진행합니다.
- 요청이 한 브랜치에 담기엔 너무 크면 (예: "대화형 인터페이스 전체") 나눌 것을 먼저 제안합니다.

### 2) 브랜치 생성

```bash
git fetch origin
git switch develop && git pull origin develop
git switch -c <파트>/<접두어>/<영문-요약>
```

브랜치명 형식은 **`[fe|be]/접두어/기능명`** 입니다. 이슈가 있을 때만 기능명 앞에 번호를 붙입니다.

```
be/feat/goal-simulation           이슈 없이 바로 작업 (기본)
fe/fix/34-exchange-rate-display   이슈 #34 가 있는 경우
chore/docker-compose              파트 구분이 없는 공통 작업
```

- `<파트>`: `be` (Spring · MyBatis · DB) 또는 `fe` (Vue · 화면). 파트 구분이 없는 공통 작업은 `chore/<요약>` 으로 파트 없이 만듭니다.
- `<접두어>`: `feat` `fix` `docs` `style` `refactor` `test` `chore` `design` `comment` `rename` `remove` `hotfix` `revert` 중 하나.
- `<영문-요약>`: 소문자 하이픈 케이스, 2~4단어.

만든 뒤 `sh ./scripts/validate-git-branch.sh` 로 이름이 통과하는지 확인합니다. 통과하지 못하면 `git branch -m` 으로 고칩니다.

### 3) 이슈가 필요한지 판단 — 짧게

이 팀은 **모든 작업에 이슈를 만들지 않습니다.** 바로 할 수 있는 일은 브랜치를 파고 PR 로 기록합니다. 다만 아래에 해당하면 먼저 `/issue` 를 권합니다.

- 방식을 정해야 해서 다른 팀원 의견이 필요할 때
- BE·FE 가 함께 움직여야 해서 순서를 맞춰야 할 때
- **DB 스키마를 바꿔야 할 때** (다른 두 명이 각자 재적용해야 하므로 미리 알려야 합니다)

해당하지 않으면 이슈 얘기를 꺼내지 말고 그냥 진행합니다.

### 4) 작업 계획 제시

실제로 손댈 파일 단위의 계획을 제시합니다.

```
계획:
1. BE/src/main/java/.../GoalController.java        — POST /api/goals/simulate 추가
2. BE/src/main/java/.../GoalSimulationService.java — 월 필요 저축액·달성률 계산
3. BE/src/main/resources/mappers/GoalMapper.xml    — 목표·자산 조회 쿼리
```

- 계획에 없던 작업이 필요해 보이면 **먼저 말하고 승인을 받습니다.** 임의로 범위를 넓히지 않습니다.
- DB 스키마 변경이 필요하면 `db/init/01-schema.sql` 수정이 반드시 포함되어야 함을 짚어줍니다. PR 을 열면 Schema Guard 가 자동으로 재적용 안내를 코멘트합니다.

### 5) 작업 시작

계획에 동의를 받은 뒤 구현을 시작합니다. **커밋은 하지 않습니다** — 커밋은 사용자가 `/commit` 을 호출할 때만 합니다.
