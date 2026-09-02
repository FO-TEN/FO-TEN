---
name: pr-create
description: 이 레포에서 PR을 만들 때 반드시 쓰는 필수 워크플로. "PR 만들어줘", "커밋하고 PR 올려줘", "이 브랜치 PR로 만들어줘" 같은 요청에 사용. 기존 PR에 리뷰 코멘트를 다는 용도로는 쓰지 않음.
---

# pr-create

FO:TEN 레포에서 PR을 생성할 때 쓰는 워크플로다. GitHub Actions(`claude-code-review.yml`)를 대신하지 않는다 — 그 워크플로는 PR이 열린 **뒤** 원격에서 도는 별개 트랙이고, 이 스킬은 PR을 만들기 **전** 로컬에서 직접 빌드·테스트를 돌려 그 결과로 본문을 채운다. 둘 다 남겨두고 독립적으로 쓴다.

## 이 스킬이 하지 않는 것

- 기존 PR에 리뷰 코멘트 달기 (그건 별개 작업)
- `gh pr merge` — 머지는 리뷰 승인 후 사람이 한다
- 커밋하지 않고 통과 체크박스를 채우기, 돌리지 않은 명령을 돌렸다고 쓰기

## 워크플로

### 1. 현재 상태 확인

```bash
git status --short
git branch --show-current
gh auth status
```

### 2. 브랜치 확인

현재 브랜치가 `develop`이나 `main`이면 **새 브랜치부터 만든다.** 이미 브랜치가 있어도 이름이 컨벤션(`scripts/validate-git-branch.sh`)에 맞지 않으면 사용자에게 확인한다.

```
[fe|be]/접두어/기능명            예) be/feat/goal-simulation
chore/기능명                     예) chore/docker-compose      (파트 구분 없는 공통 작업)
docs/기능명                      예) docs/setup-guide          (저장소 전체 문서)
```

이슈 번호가 있으면 기능명 앞에 붙인다 (`be/fix/34-exchange-rate-display`). 없으면 붙이지 않는다 — 이 팀은 이슈 없이 바로 작업하는 게 기본이다.

### 3. 변경 내용 파악

```bash
git diff --stat
git diff
```

**PR 본문은 이 diff에 실제로 있는 내용만 근거로 쓴다.** 파일 이름만 보고 짐작해서 쓰지 않는다.

### 4. DB 스키마 변경 확인

`db/init/*.sql`이 diff에 있으면, PR 본문의 "변경된 DB 스키마" 섹션에 **반드시** 재적용 방법을 적는다:

```
- ⚠️ 머지 후 각자 스키마 재적용 필요
  - Docker      : docker compose down -v && docker compose up -d
  - 로컬 MySQL  : mysql -u root -p foten < db/init/01-schema.sql
```

### 5. 검증 실행

변경된 코드에 해당하는 것만 돌린다.

- **BE 코드가 바뀌었으면**: `cd BE && ./gradlew clean build`
- **FE 코드가 바뀌었으면**: `cd FE && npm run build` — `FE/package.json`이 없으면 건너뛰고 그 사실을 본문에 명시한다.
- 실패가 있으면 **이번 diff 때문인지, 로컬 환경 문제(DB 미기동, 시크릿 파일 없음 등)인지 구분**해서 기록한다. 구분이 안 서면 "확인 필요"라고 솔직히 남긴다.
- `.claude/rules/backend.md`, `.claude/rules/database.md`의 컨벤션(`javax.servlet` 고정, `/api` 프리픽스, 금액은 `DECIMAL`, 컬럼명 `snake_case`, 생성자 주입 등)에 어긋나는 부분이 diff에 보이면 리뷰 포인트에 짚어준다.

### 6. 커밋

`scripts/validate-commit-message.sh`를 통과하는 형식으로 커밋 메시지를 쓴다.

```
[be]feat: 목표 시뮬레이션 API 추가
[fe]fix: 환율 표시 소수점 오류 수정
chore: Docker Compose 구성 추가        (파트 구분이 없는 공통 작업)
docs: README 컨벤션 표 갱신            (저장소 전체 문서)
```

- 접두어: `feat fix docs style refactor test chore design comment rename remove hotfix revert`
- 파트(`fe`/`be`)를 생략할 수 있는 건 `chore`와 `docs` 둘뿐이다.
- 본문에는 "무엇을"이 아니라 **"왜"**를 쓴다. 무엇은 diff를 보면 안다.
- AI 도구 서명(`Co-Authored-By: Claude`, `Generated with Claude Code`, `🤖` 등)을 **절대 넣지 않는다.** `validate-commit-message.sh`가 이걸 걸러낸다.
- 메시지가 여러 줄이면 임시 파일에 쓴 뒤 `git commit -F <파일>`을 쓰고, 커밋 전에 `sh ./scripts/validate-commit-message.sh <파일>`로 검증한다.

### 7. 푸시

```bash
git push -u origin "$(git branch --show-current)"
```

### 8. PR 생성

- **base는 항상 `develop`이다.** `main`으로 여는 PR은 사용자가 명시적으로 요청할 때만.
- 제목은 커밋과 같은 형식: `[be]feat: 목표 시뮬레이션 API 추가`. squash merge 하면 이 제목이 그대로 `develop`의 커밋 메시지가 되고, `convention` CI가 이 형식을 검증한다.
- 본문은 아래 "PR 본문 템플릿"을 따른다. 임시 파일에 써서 `--body-file`로 넘긴다 (한국어·줄바꿈이 셸 이스케이프로 깨지는 걸 막기 위함).

```bash
gh pr create --base develop --title "<제목>" --body-file <임시파일> --assignee @me
```

`--assignee @me`가 실패하면 (예: 로그인 계정과 GitHub 사용자명이 안 맞음) `gh api user --jq .login`으로 실제 로그인을 확인한 뒤 그 값으로 재시도한다.

생성 후 PR URL을 사용자에게 알려준다.

## PR 본문 템플릿

`.github/pull_request_template.md`를 그대로 따른다 (`gh pr create`가 자동으로 이 템플릿을 채워 넣으므로, 아래 섹션 순서·제목이 실제 파일과 항상 일치해야 한다 — 파일이 바뀌면 이 스킬도 같이 고친다).

```markdown
## 무엇을 왜

<배경 한두 줄 + 이 PR 이 해결하는 것>

## 작업 내용

<파일 나열이 아니라 기능 단위로. 3~6개 불릿>

## 주요 결정

<리뷰어가 "이거 왜 이렇게 했지?" 할 만한 지점. 없으면 "없음">

## 변경된 DB 스키마

<없으면 "없음". 있으면 반영 여부와 재적용 방법 (위 4번 참고)>

## 확인한 것

<!-- 실제로 돌려서 통과한 것만 [x]. 안 돌렸으면 [ ] 로 비워둔다 -->

- [ ] develop 브랜치의 최신 코드를 pull 받았나요?
- [ ] `cd BE && ./gradlew build` 통과
- [ ] `cd FE && npm run build` 통과
- [ ] 로컬에서 동작 확인 (실행 방식: Docker / 로컬 MySQL / 원격 DB)

## 🤖 AI 리뷰

<실제로 실행한 명령과 결과를 불릿으로. 실패가 있으면 diff 때문인지 환경
문제인지 구분해서 한 줄로. 이번 변경이 FE/다른 도메인에 영향을 주는지
확인한 결과도 남긴다. 문체는 간결하고 사실 기반으로:
  - "cd BE && ./gradlew clean build 통과 — 테스트 12건 전부 성공"
  - "이 코드를 단언하는 테스트는 원래 없어 수정 불필요"
  - "FE는 이번 diff와 무관 — 변경 파일이 BE/ 아래로만 한정됨"
돌리지 않은 걸 돌렸다고 쓰지 않는다.>

## 리뷰 포인트

<특히 봐줬으면 하는 부분. 없으면 "전체 훑어주세요">

## 남은 작업

<이 PR 범위 밖으로 미룬 것. 없으면 "없음">

## 관련 이슈

<이슈가 있을 때만 Closes #. 없으면 이 항목을 통째로 지운다>

---

- [ ] base 브랜치가 `develop` 입니다
- [ ] 제목이 `[fe|be]접두어: 기능명` 형식입니다
- [ ] `.env` · API 키 · pem 키가 포함되지 않았습니다
- [ ] 디버그용 `console.log` / `System.out.println` 을 제거했습니다
```

## 하지 말 것

- `.github/workflows/claude-code-review.yml`은 이 스킬과 별개 트랙이다. 건드리지 않는다.
- 테스트를 돌리지 않고 통과 체크박스를 채우지 않는다.
- 본문을 "작업 완료" 한 줄로 때우지 않는다 — 이 팀은 이슈를 매번 만들지 않으므로 PR이 이 작업의 유일한 기록이다.
