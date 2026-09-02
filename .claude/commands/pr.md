---
description: 팀 PR 양식에 맞춰 본문을 작성하고 PR을 생성합니다
argument-hint: "[관련 이슈번호 (선택 — 없으면 브랜치명에서 추출)]"
allowed-tools: Bash(git *), Bash(gh pr create *), Bash(gh pr view *), Bash(gh issue view *)
---

## 현재 상태

- 브랜치: !`git branch --show-current`
- 커밋되지 않은 변경: !`git status --short`
- 이 브랜치의 커밋: !`git log --oneline -20`

## 추가 지시

$ARGUMENTS

---

## 할 일

### 1) 사전 확인

- 현재 브랜치가 `main` 또는 `develop` 이면 **중단**합니다.
- 커밋되지 않은 변경이 남아 있으면 알려주고, 커밋할지 물어봅니다.
- 아직 push되지 않았으면 `git push -u origin <브랜치명>` 을 먼저 실행합니다. (pre-push 훅이 브랜치명을 검증합니다)

### 2) 변경 내용 수집

직접 실행해서 확인합니다 (위 주입된 정보만으로 판단하지 마세요):

```bash
git fetch origin develop
git log origin/develop..HEAD --oneline
git diff origin/develop...HEAD --stat
```

브랜치명에 이슈 번호가 있으면 `gh issue view <번호>` 로 읽고 내용을 대조합니다. **번호가 없으면 이슈 없이 진행한 작업이므로 이슈를 찾지 않습니다.** 이 경우 PR 이 유일한 기록이므로 "무엇을 왜" 항목을 특히 충실히 씁니다.

### 3) 테스트 실행

PR 본문의 "테스트 결과"에 **실제로 돌린 결과**를 씁니다. 돌리지 않고 통과했다고 쓰지 않습니다.

```bash
cd BE && ./gradlew build
cd FE && npm run build
```

실패하면 PR을 만들지 말고 실패 내용을 먼저 보고합니다.

### 4) 본문 작성

`.github/pull_request_template.md` 의 항목을 그대로 따릅니다. **한국어로** 작성합니다.

**이슈를 매번 만들지 않으므로, PR 이 이 작업의 유일한 기록입니다.** "무엇을 왜" 항목을 파일 변경 요약으로 때우지 말고, 배경과 판단 근거를 남기세요. 두 달 뒤에 이 PR 만 보고 이해할 수 있어야 합니다.

```markdown
## 무엇을 왜

<배경 한두 줄 + 이 PR 이 해결하는 것>

## 작업 내용

<파일 나열이 아니라 기능 단위로. 3~6개 불릿>

## 주요 결정

<리뷰어가 "이거 왜 이렇게 했지?" 할 만한 지점. 자명하면 "없음">

## 변경된 DB 스키마

<없으면 "없음". 있으면 반영 여부와 재적용 방법>

- `tbl_goal` 테이블 추가 (`db/init/01-schema.sql` 반영 완료)
- ⚠️ 머지 후 각자 스키마 재적용 필요
  - Docker      : docker compose down -v && docker compose up -d
  - 로컬 MySQL  : mysql -u root -p foten < db/init/01-schema.sql

## 확인한 것

- [ ] `cd BE && ./gradlew build` 통과
- [ ] `cd FE && npm run build` 통과
- [ ] 로컬에서 동작 확인 (실행 방식: Docker / 로컬 MySQL / 원격 DB)

## 리뷰 포인트

<특히 봐줬으면 하는 부분. 없으면 "전체 훑어주세요">

## 남은 작업

<이 PR 범위 밖으로 미룬 것. 나중에 꼭 해야 하면 이슈로 남길 것을 권합니다>

## 관련 이슈

<이슈가 있을 때만 Closes #12. 없으면 이 항목을 통째로 지웁니다>
```

### 5) 생성

- **본문은 임시 파일에 쓴 뒤 `--body-file` 로 넘깁니다.** 한국어와 줄바꿈이 셸 이스케이프로 깨지는 것을 막기 위함입니다.

```bash
gh pr create --base develop --title "<제목>" --body-file <임시파일>
```

- 제목은 **커밋 컨벤션과 같은 형식**입니다: `[be]feat: 목표 달성률 시뮬레이션 API 추가`
  - squash merge 하면 이 제목이 그대로 `develop` 의 커밋 메시지가 됩니다. CI 의 `convention` 잡이 제목을 검증합니다.
  - 만들기 전에 `printf '%s
' "<제목>" > /tmp/t && sh ./scripts/validate-commit-message.sh /tmp/t` 로 확인하세요.
- **base는 항상 `develop`** 입니다. `main` 으로 여는 PR은 사용자가 명시적으로 요청할 때만.
- 작업이 아직 진행 중이면 `--draft` 를 붙일지 물어봅니다.
- 생성 후 PR URL을 알려줍니다.

### 6) 하지 말 것 — 중요

- PR 본문에 `🤖 Generated with Claude Code`, `Co-Authored-By: Claude` 등 **AI 도구 서명을 절대 넣지 않습니다.**
- `gh pr merge` — 머지는 리뷰 승인 후 사람이 합니다. (기능 PR은 squash merge, develop → main 릴리스는 merge 커밋 — ruleset 이 브랜치별로 나머지 버튼을 막아둡니다)
- 테스트를 돌리지 않고 통과 체크박스를 채우기.
- 본문을 "작업 완료" 한 줄로 때우기.
