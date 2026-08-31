---
description: 팀 커밋 컨벤션에 맞춰 커밋 메시지를 작성하고 커밋합니다
argument-hint: "[추가 지시 (선택)]"
allowed-tools: Bash(git status *), Bash(git diff *), Bash(git add *), Bash(git commit *), Bash(git log *), Bash(git branch *)
---

## 현재 상태

- 브랜치: !`git branch --show-current`
- 변경 파일: !`git status --short`
- 스테이징된 diff: !`git diff --cached --stat`
- 스테이징 안 된 diff: !`git diff --stat`
- 최근 커밋 5개: !`git log --oneline -5`

## 추가 지시

$ARGUMENTS

---

## 할 일

### 1) 안전 확인 — 먼저

- 현재 브랜치가 `main` 또는 `develop` 이면 **커밋하지 말고 중단**합니다. 브랜치를 만들어야 한다고 알려줍니다.
- `.env`, `secrets/`, `*.pem`, `*.key`, API 키가 담긴 파일이 변경 목록에 있으면 **커밋하지 말고 경고**합니다.
- `BE/build/`, `BE/.gradle/`, `FE/node_modules/`, `FE/dist/`, `.idea/` 가 보이면 `.gitignore` 누락이므로 알려줍니다.
- `CLAUDE.md`, `.claude/settings.local.json`, `.mcp.json` 이 보이면 커밋 대상이 아니므로 제외합니다.

### 2) 변경 내용 파악

`git diff` 로 **실제 변경 내용을 읽고** 무엇이 왜 바뀌었는지 파악합니다. 파일 이름만 보고 메시지를 쓰지 않습니다.

### 3) 커밋 단위 판단

서로 관련 없는 변경이 섞여 있으면 (예: 기능 추가 + 무관한 오타 수정) **한 번에 커밋하지 말고** 나눌 것을 제안합니다. 나누기로 하면 `git add <파일>` 로 선택 스테이징 후 여러 번 커밋합니다.

### 4) 메시지 작성

```
[<파트>]<접두어>: <제목 — 한국어, 50자 이내, 마침표 없음, 명령형>

<본문 — 무엇을 왜 바꿨는지. 한 줄 72자 이내. 자명한 변경이면 생략 가능>

Refs: #<이슈번호>          ← 이슈가 있을 때만. 없으면 이 줄을 넣지 않습니다
```

- **파트**: `be` 또는 `fe`. 파트 구분이 없는 작업은 대괄호 없이 씁니다 — 설정·CI·빌드는 `chore: 제목`, 저장소 전체 문서는 `docs: 제목`. 이 둘만 파트를 생략할 수 있습니다.
- **접두어**: `feat` `fix` `docs` `style` `refactor` `test` `chore` `design` `comment` `rename` `remove` `hotfix` `revert`
- 이슈 번호는 **브랜치명에서 추출**합니다 (`be/fix/34-exchange-rate` → `#34`). **브랜치에 번호가 없으면 이슈 없이 진행하는 작업이므로 `Refs:` 줄을 아예 넣지 않습니다.** 사용자에게 이슈 번호를 묻지 마세요.
- 본문에는 "무엇을"보다 **"왜"** 를 씁니다. 무엇은 diff를 보면 됩니다.

좋은 예:

```
[be]feat: 목표 달성률 시뮬레이션 API 추가

월 순저축과 남은 개월 수로 귀국 시 예상 자산을 산출하고,
환율 유지 / ±5% / ±10% 네 시나리오를 병렬로 계산한다.
환율을 예측하지 않는 이유는 결과의 검증 가능성을 지키기 위함이다.

Refs: #12
```

나쁜 예: `update code`, `fix bug`, `[be]feat: 여러가지 수정`

### 5) 커밋

- 메시지가 여러 줄이므로 임시 파일에 쓴 뒤 `git commit -F <파일>` 을 씁니다.
- 커밋 전에 `sh ./scripts/validate-commit-message.sh <파일>` 로 형식을 검증합니다. 통과하지 못하면 메시지를 고칩니다.
- **커밋 후 push는 하지 않습니다.** push는 사용자가 직접 하거나 `/pr` 에서 처리합니다.
- 커밋한 뒤 `git log --oneline -1` 로 결과를 보여줍니다.

### 6) 하지 말 것 — 중요

- 커밋 메시지에 `Co-Authored-By: Claude`, `Generated with Claude Code`, `🤖`, `claude.ai/code` 등 **AI 도구 서명을 절대 넣지 않습니다.** 저장소 커밋 로그와 GitHub contributor 목록에 AI 가 잡히면 안 됩니다.
- `--no-verify` 로 훅을 건너뛰지 않습니다.
- `git push` 를 임의로 실행하지 않습니다.
