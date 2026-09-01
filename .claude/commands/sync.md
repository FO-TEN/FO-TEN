---
description: develop 최신 변경을 내 브랜치에 반영하고 충돌 지점을 정리합니다
argument-hint: "[추가 지시 (선택)]"
allowed-tools: Bash(git fetch *), Bash(git status *), Bash(git log *), Bash(git diff *), Bash(git branch *), Bash(git merge *), Bash(git stash *)
---

## 현재 상태

- 브랜치: !`git branch --show-current`
- 작업 트리: !`git status --short`

## 추가 지시

$ARGUMENTS

---

## 할 일

3명이 같은 시기에 작업하므로 `develop` 이 자주 앞서갑니다. PR 을 열기 전에 한 번 맞춰두면 리뷰 중 충돌이 나는 일을 줄일 수 있습니다.

### 1) 사전 확인

- 현재 브랜치가 `develop` 또는 `main` 이면 `git pull origin <브랜치>` 만 하고 끝냅니다.
- 커밋되지 않은 변경이 있으면 **먼저 알려주고** 어떻게 할지 묻습니다. (커밋 / stash / 중단) 임의로 stash 하지 않습니다.

### 2) develop 최신화 후 차이 확인

```bash
git fetch origin develop
git log --oneline HEAD..origin/develop      # 내가 못 받은 develop 커밋
git log --oneline origin/develop..HEAD      # 내 브랜치에만 있는 커밋
```

`HEAD..origin/develop` 이 비어 있으면 이미 최신이므로 **아무것도 하지 않고** 그렇게 알려줍니다.

### 3) 무엇이 바뀌었는지 먼저 보고

머지하기 전에, 받아올 커밋 중 **내 작업에 영향을 주는 것**을 짚어줍니다.

```bash
git diff --stat HEAD...origin/develop
```

특히 아래는 반드시 짚습니다.

- `db/init/*.sql` 변경 → **스키마 재적용이 필요합니다.** 명령을 함께 알려줍니다.
  - Docker: `docker compose down -v && docker compose up -d`
  - 로컬 MySQL: `mysql -u root -p foten < db/init/01-schema.sql`
- `docker-compose.yml` · `.env.example` 변경 → `.env` 에 새 항목을 추가해야 할 수 있습니다.
- `BE/build.gradle` · `FE/package.json` 변경 → 의존성 재설치가 필요합니다.
- 내가 수정 중인 파일과 겹치는 변경

### 4) 머지

```bash
git merge origin/develop
```

**rebase 가 아니라 merge 를 씁니다.** 이미 push 한 브랜치를 rebase 하면 force push 가 필요하고, 리뷰 중인 PR 의 코멘트 위치가 어긋납니다.

충돌이 나면 **자동으로 해결하지 말고** 충돌 파일 목록과 각 충돌의 성격(양쪽이 같은 줄을 고쳤는지, 한쪽이 삭제했는지)을 정리해 보고합니다. 어느 쪽을 남길지는 사용자가 정합니다.

### 5) 마무리

- 머지 후 빌드가 되는지 확인할지 물어봅니다 (`cd BE && ./gradlew build`, `cd FE && npm run build`).
- 3)에서 스키마·의존성 변경을 발견했다면 **해야 할 일을 다시 한 번 요약**합니다. 이게 이 커맨드의 핵심입니다.
- **push 는 하지 않습니다.**
