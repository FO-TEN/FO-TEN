# 개발 환경 세팅

FO:TEN 은 **Docker 경로와 로컬 경로 두 가지**를 지원합니다. 팀원 중 한 명이 PC 사양 때문에 Docker 를 쓰지 못하기 때문입니다.

두 경로는 **포트·DB명·계정·시간대가 완전히 같아야** 합니다. 하나라도 다르면 "내 PC 에서만 되는" 문제가 생깁니다.

| | Docker 경로 | 로컬 경로 |
| --- | --- | --- |
| MySQL | 컨테이너 `foten-mysql` | PC 에 직접 설치 |
| 백엔드 | 컨테이너 `foten-api` (Tomcat 9) | IDE 에서 Tomcat 9 실행 |
| 프론트 | 컨테이너 `foten-web` (nginx) | `npm run dev` (Vite) |
| 접속 주소 | http://localhost:5173 | http://localhost:5173 |
| API 주소 | `/api` → nginx → `app:8080` | `/api` → Vite 프록시 → `localhost:8080` |

접속 주소가 같도록 포트를 맞춰뒀습니다. 어느 경로로 띄우든 브라우저 주소와 API 호출 코드는 동일합니다.

---

## 공통 — 처음 한 번만

```bash
git clone https://github.com/FO-TEN/FO-TEN.git
cd FO-TEN

# 환경변수 파일 생성 (.env 는 커밋되지 않습니다)
cp .env.example .env

# Git 훅 설치 — 커밋 메시지·브랜치명을 자동 검증합니다
npm install
npx lefthook install
```

`.env` 를 열어 `LLM_API_KEY`, `EXIM_API_KEY` 를 채웁니다. 키는 팀 채널에서 공유하고 **절대 커밋하지 않습니다.**

> Windows 에서 `npx lefthook install` 이 실패하면 Git Bash 에서 실행해 보세요. PowerShell 에서 훅 스크립트 경로 해석이 다를 수 있습니다.

---

## A. Docker 경로

### 필요한 것

- Docker Desktop

### 실행

```bash
docker compose up -d          # MySQL + 백엔드 + 프론트 전체
```

http://localhost:5173 으로 접속합니다.

부분 실행도 됩니다. 개발 중에는 이쪽이 더 편합니다.

```bash
docker compose up -d mysql          # DB 만 컨테이너로, BE·FE 는 IDE / npm run dev
docker compose up -d mysql app      # DB + 백엔드, 프론트만 npm run dev (HMR 사용)
```

### 자주 쓰는 명령

```bash
docker compose logs -f app          # 백엔드 로그 실시간 확인
docker compose restart app          # 백엔드만 재시작
docker compose down                 # 중지 (데이터는 유지)
docker compose down -v              # 중지 + DB 볼륨 삭제 (스키마 재적용할 때)
docker compose up -d --build app    # 코드 바뀐 뒤 이미지 다시 빌드
```

### 스키마가 바뀌었을 때

`db/init/*.sql` 마운트는 **데이터 디렉터리가 완전히 빈 최초 기동에만** 실행됩니다. 그냥 재시작하면 반영되지 않습니다.

```bash
docker compose down -v && docker compose up -d
```

---

## B. 로컬 경로 (Docker 없이)

### 필요한 것

- JDK 17
- MySQL 8.4
- Node.js 20
- Tomcat 9 (IDE 내장 또는 별도 설치) — **Tomcat 10 이상은 안 됩니다.** `javax.*` → `jakarta.*` 전환 때문에 Spring 5 앱이 뜨지 않습니다.

### 1) MySQL 준비

컨테이너와 **동일한 값**을 씁니다.

```sql
CREATE DATABASE foten DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'foten'@'localhost' IDENTIFIED BY 'foten';
GRANT ALL PRIVILEGES ON foten.* TO 'foten'@'localhost';
FLUSH PRIVILEGES;
```

시간대를 `Asia/Seoul` 로 맞춥니다. 안 맞추면 귀국 D-day 계산과 환율 배치 시각이 하루씩 밀립니다.

```sql
SET GLOBAL time_zone = '+09:00';
```

재시작해도 유지되게 하려면 `my.ini` (Windows) 의 `[mysqld]` 에 `default-time-zone='+09:00'` 를 추가합니다.

### 2) 스키마 적용

```bash
mysql -u root -p foten < db/init/01-schema.sql
mysql -u root -p foten < db/init/02-seed.sql
```

**머지 후 `db/init/*.sql` 이 바뀌었으면 이 명령을 다시 돌려야 합니다.** pull 만으로는 DB 가 바뀌지 않습니다.

### 3) 백엔드 설정

`BE/src/main/resources/application-local.properties` 를 만듭니다. 이 파일은 gitignore 대상입니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/foten?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=foten
spring.datasource.password=foten
```

IDE 에서 Tomcat 9 를 8080 포트로 띄웁니다. VM 옵션에 `-Duser.timezone=Asia/Seoul` 을 넣습니다.

### 4) 프론트엔드

```bash
cd FE
npm install
npm run dev
```

http://localhost:5173 으로 접속합니다. `vite.config.js` 의 프록시가 `/api` 를 `localhost:8080` 으로 넘깁니다.

```js
// FE/vite.config.js
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
```

---

## C. 원격 공용 DB 로 전환

각자 DB 를 관리하기 번거로울 때, 팀 서버나 클라우드 MySQL 하나를 공유할 수 있습니다. **바꾸는 것은 접속 정보 한 줄뿐입니다.**

- Docker 경로: `.env` 의 `DB_URL` 을 원격 주소로 바꾸고 `docker compose up -d mysql` 을 하지 않습니다 (mysql 서비스를 띄우지 않음)
- 로컬 경로: `application-local.properties` 의 `spring.datasource.url` 을 원격 주소로 바꿉니다

```
DB_URL=jdbc:mysql://<원격호스트>:3306/foten?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

주의할 점:

- 원격 DB 는 **스키마가 절대 어긋나지 않는 대신, 누가 데이터를 지우면 전원이 영향**을 받습니다. 시연 직전에는 각자 로컬로 돌아가는 편이 안전합니다.
- 원격 DB 의 시간대도 `Asia/Seoul` 이어야 합니다.
- 원격 DB 계정 정보는 `.env` 에만 두고 커밋하지 않습니다.

---

## 문제 해결

**컨테이너는 떴는데 API 가 500 을 냄**
```bash
docker compose logs -f app
```
DB 연결 실패면 `.env` 의 `DB_URL` 호스트를 확인합니다. Docker 경로는 `mysql`, 로컬 경로는 `localhost` 입니다.

**3306 포트가 이미 사용 중**

PC 에 MySQL 이 이미 돌고 있는 경우입니다. 로컬 MySQL 서비스를 끄거나, `docker-compose.yml` 의 포트를 `'3307:3306'` 으로 바꾸고 `.env` 의 `DB_URL` 도 함께 바꿉니다.

**스키마를 바꿨는데 반영이 안 됨**

Docker 는 `docker compose down -v && docker compose up -d`, 로컬은 SQL 파일을 다시 실행해야 합니다. 위의 "스키마가 바뀌었을 때" 항목을 보세요.

**시간이 9시간 어긋남**

MySQL 서버 시간대, 앱의 `TZ`, JDBC URL 의 `serverTimezone` 세 곳을 모두 `Asia/Seoul` 로 맞춰야 합니다. JDBC 옵션만 바꾸는 것으로는 서버의 `NOW()` 가 바뀌지 않습니다.

**커밋이 훅에서 막힘**

메시지나 브랜치명이 컨벤션에 안 맞는 경우입니다. 에러 메시지에 올바른 형식이 나옵니다. `--no-verify` 로 넘기지 말고 이름을 고치세요.

```bash
git branch -m be/feat/12-goal-simulation    # 브랜치명 수정
git commit --amend                          # 직전 커밋 메시지 수정
```

---

## Git 작업 자동화

### 로컬 — lefthook

`npx lefthook install` 을 하면 아래 검증이 자동으로 걸립니다. 추가 도구는 필요 없습니다.

| 시점 | 검사 | 스크립트 |
| --- | --- | --- |
| `commit-msg` | 커밋 메시지 형식 · 자동 생성 서명 | `scripts/validate-commit-message.sh` |
| `pre-push` | 브랜치명 형식 | `scripts/validate-git-branch.sh` |
| `pre-commit` | FE prettier · eslint 자동 수정 | (FE 에 설치돼 있을 때만 동작) |

### PR — GitHub Actions

로컬 훅을 설치하지 않은 팀원이 있어도 컨벤션이 지켜집니다.

| 워크플로 | 하는 일 |
| --- | --- |
| `ci` | `backend`(Gradle) · `frontend`(npm) 빌드 |
| `convention` | 브랜치명 · **PR 제목** · 브랜치의 모든 커밋 메시지 검증 |
| `schema-guard` | `db/init/*.sql` 이 바뀌면 PR 에 재적용 명령을 자동 코멘트 |
| `labeler` | 변경 경로로 `BE` `FE` `db` `infra` `docs` 라벨 자동 부착 |

`convention` 은 `scripts/validate-*.sh` 의 정규식을 그대로 읽어서 씁니다. **규칙을 바꿀 때는 스크립트만 고치면 CI 도 따라옵니다.** (사람이 읽는 `README.md` 의 표는 따로 고쳐야 합니다)

PR 제목을 검사하는 이유는 squash merge 때문입니다. 머지하면 PR 제목이 그대로 `develop` 의 커밋 메시지가 되므로, 제목이 컨벤션에 안 맞으면 커밋 로그가 깨집니다.

### 스크립트를 직접 돌려보기

```bash
sh ./scripts/validate-git-branch.sh                 # 현재 브랜치명 검사
echo "[be]feat: 테스트" > /tmp/m && sh ./scripts/validate-commit-message.sh /tmp/m
```

### 커밋 기여자 확인

GitHub 은 커밋의 **author**, **committer**, **`Co-authored-by:` 트레일러** 세 곳을 모두 읽어 기여자를 집계합니다. 이 저장소는 실제로 작업한 사람만 남도록 세 지점을 모두 검사합니다.

| 언제 | 무엇을 |
| --- | --- |
| `pre-push` (로컬) | push 하려는 커밋의 author · committer · 트레일러 |
| `convention` (CI) | PR 의 모든 커밋에 대해 같은 검사 |

로컬 훅을 설치하지 않았거나 다른 도구로 커밋했더라도 PR 에서 걸립니다.

직접 확인하려면:

```bash
git shortlog -sne --all              # 저장소 전체 기여자 목록
sh ./scripts/validate-commit-author.sh origin/develop..HEAD
```

내 git 설정이 맞는지 먼저 보세요. 여기가 틀리면 커밋마다 잘못된 이름이 박힙니다.

```bash
git config user.name
git config user.email
```

GitHub 계정과 같은 메일을 쓰면 커밋이 내 계정에 연결됩니다. 메일을 공개하고 싶지 않으면 GitHub 의 `noreply` 주소를 쓰세요 (Settings → Emails → Keep my email addresses private).

### 팀 도구 설정 (`.claude/`)

`.claude/` 에는 팀이 함께 쓰는 것만 커밋합니다.

| 커밋 | 무시 |
| --- | --- |
| `commands/` — 브랜치·커밋·PR 작성 보조 | `settings.local.json` (개인 권한 설정) |
| `hooks/` — 보호 브랜치·서명 차단 | `todos/`, `projects/`, 세션 파일 |
| `rules/` — 프로젝트 코딩 규칙 | `.mcp.json`, `CLAUDE.md` |
| `settings.json` — 공용 설정 | |

`rules/` 는 Spring Legacy 의 `javax` vs `jakarta`, MyBatis `${}` 금지, 금액 `BigDecimal`, LLM 이 금액을 직접 계산하지 않기 같은 내용이라 **사람이 읽어도 유용합니다.** 한 번 훑어보세요.

개인 설정은 `.claude/settings.local.json` 에 두면 다른 팀원에게 영향을 주지 않습니다.

### 라벨 만들기 (저장소 관리자가 한 번만)

PR 라벨 자동 부착이 동작하려면 라벨이 먼저 있어야 합니다. 없는 라벨은 조용히 무시됩니다.

```bash
gh label create BE     --color 1D76DB --description "백엔드 변경"
gh label create FE     --color 0E8A16 --description "프론트엔드 변경"
gh label create db     --color D93F0B --description "DB 스키마 · SQL 변경"
gh label create infra  --color 5319E7 --description "Docker · CI · 설정 변경"
gh label create docs   --color 0075CA --description "문서 변경"
gh label create task   --color FBCA04 --description "논의가 필요한 작업"
```

### 브랜치 보호 설정 (저장소 관리자가 한 번만)

`develop` 과 `main` 에 아래를 걸어두면 컨벤션이 강제됩니다.

- Require a pull request before merging — 승인 1명
- Require status checks to pass — `backend`, `frontend`, `convention`
- Require branches to be up to date before merging

> 잡 이름(`backend` / `frontend` / `convention`)을 바꾸면 여기 등록한 필수 체크 이름도 함께 바꿔야 합니다. 안 바꾸면 PR 이 영원히 pending 으로 멈춥니다.
