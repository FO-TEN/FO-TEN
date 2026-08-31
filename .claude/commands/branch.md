---
description: 작업 내용을 받아 컨벤션에 맞는 브랜치명을 추천합니다
argument-hint: "[작업 설명 또는 이슈번호]"
allowed-tools: Bash(git branch *), Bash(git switch *), Bash(gh issue view *), Bash(gh issue list *)
---

## 요청 내용

$ARGUMENTS

## 현재 상태

- 현재 브랜치: !`git branch --show-current`
- 로컬 브랜치 목록: !`git branch --format='%(refname:short)'`

---

## 할 일

요청 내용에 맞는 **브랜치명 후보 3개**를 제안합니다. 만들지는 않습니다 — 사용자가 고른 뒤에 만듭니다.

### 형식

```
[fe|be]/접두어/케밥-기능명            기본. 이슈 없이 바로 작업합니다
[fe|be]/접두어/이슈번호-케밥-기능명   이슈가 있을 때만 번호를 붙입니다
chore/케밥-기능명                     파트 구분이 없는 공통 작업 (설정 · CI · 빌드)
docs/케밥-기능명                      저장소 전체 문서
```

| 요소 | 값 |
| --- | --- |
| 파트 | `be` (Spring / MyBatis / DB) · `fe` (Vue / 화면) · 공통은 `chore/` 로 시작 |
| 접두어 | `feat` `fix` `docs` `style` `refactor` `test` `chore` `design` `comment` `rename` `remove` `hotfix` `revert` |
| 기능명 | 소문자 하이픈 케이스, 영문 2~4단어 |

### 판단 기준

- **이슈를 만들라고 권하지 않습니다.** 이 팀은 모든 작업에 이슈를 만들지 않고, 바로 브랜치를 파서 PR 로 기록합니다. 요청에 이슈 번호가 있을 때만 번호를 붙입니다.
- 단, **DB 스키마를 바꾸는 작업**이거나 **BE·FE 가 함께 움직여야 하는 작업**이면 이슈를 먼저 만들 것을 한 줄로 권합니다. 다른 두 명이 각자 재적용하거나 순서를 맞춰야 하기 때문입니다.
- **파트가 애매하면** 어느 쪽 코드를 더 많이 건드리는지로 정합니다. BE·FE를 반반 건드리면 이슈를 나눌 것을 제안합니다.
- 기능명은 **무엇을 하는지**로 짓습니다. `be/feat/12-update` 처럼 의미 없는 이름은 제안하지 않습니다.

### 출력 형식

```
추천 (목표 시뮬레이션 API 추가):

1. be/feat/goal-simulation        ← 권장. 작업 범위와 가장 가깝습니다
2. be/feat/goal-achievement-rate  ← 달성률 계산이 핵심이라면
3. be/feat/savings-plan-api       ← 저축 계획 산출 전체를 담는다면

만들려면:  git switch develop && git pull origin develop && git switch -c be/feat/goal-simulation
```

- 제안한 이름이 `sh ./scripts/validate-git-branch.sh` 의 정규식을 통과하는지 확인한 뒤 출력합니다.
- 이미 같은 이름의 로컬 브랜치가 있으면 알려주고 다른 이름을 제안합니다.
