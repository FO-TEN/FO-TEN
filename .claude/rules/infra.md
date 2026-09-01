---
paths:
  - ".github/workflows/*.yml"
  - ".github/workflows/*.yaml"
  - "**/Dockerfile"
  - "docker-compose.yml"
  - "FE/nginx/*.conf"
  - "lefthook.yml"
  - "scripts/*.sh"
---

# CI / 인프라 규칙

## 대전제

**팀원 중 한 명은 Docker를 쓰지 못합니다.** Docker는 선택 경로이지 필수 경로가 아닙니다.

- CI 워크플로가 로컬 Docker를 전제하게 만들지 않는다. GitHub Actions에서 Gradle + Node만으로 빌드가 끝나야 한다.
- 로컬에서 이미지를 빌드해야만 검증되는 구조로 만들지 않는다.
- `docker-compose.yml` 을 바꿀 때는 로컬 MySQL 사용자에게 동등한 대안이 있는지 확인한다. **포트·DB명·계정·시간대를 컨테이너와 동일하게 유지**한다.
- Docker 전용 기능(컨테이너 이름으로 DNS 해석 등)에 애플리케이션이 의존하면 안 된다. 호스트명은 `.env` 의 `DB_URL` 한 줄로 갈아끼울 수 있어야 한다.

## Tomcat 버전 — 고정

`tomcat:9.0-*` 이미지만 씁니다. Tomcat 10 이상은 `javax.*` → `jakarta.*` 전환 때문에 Spring 5 앱이 뜨지 않습니다. **이미지 태그를 올리지 마세요.**

## 시간대

컨테이너·앱·DB의 시간대를 전부 `Asia/Seoul` 로 맞춥니다. 기본값(UTC)이면 귀국 D-day 계산과 환율 배치 시각이 하루씩 어긋납니다.

- `Dockerfile`: `ENV TZ=Asia/Seoul`
- `docker-compose.yml`: mysql `--default-time-zone=+09:00`, app `TZ: Asia/Seoul`
- JDBC URL의 `serverTimezone` 은 드라이버 해석만 바꿀 뿐 서버의 `NOW()` 는 바꾸지 않는다. 둘 다 필요하다.

## GitHub Actions

- 액션은 메이저 버전을 고정한다 (`actions/checkout@v4`). `@main` 이나 `@master` 를 쓰지 않는다.
- `BE/gradlew`, `BE/gradlew.bat`, `BE/gradle/wrapper/` 는 반드시 커밋되어 있어야 한다. 없으면 CI가 `gradlew: not found` 로 죽는다.
- 실행 권한이 빠졌으면: `git update-index --chmod=+x BE/gradlew`
- `concurrency` 로 이전 실행을 취소해 Actions 분을 아낀다.
- 잡 이름(`backend` / `frontend` / `convention`)을 바꾸면 **브랜치 보호의 필수 체크 이름도 함께 바꿔야 한다.** 안 바꾸면 PR이 영원히 pending으로 멈춘다. 잡 이름 변경을 제안할 때는 이 점을 함께 알린다.
- "변경된 폴더만 빌드"(paths-filter)를 도입하면 스킵된 잡 때문에 필수 체크가 걸린다. 3인 팀에서는 모든 잡을 무조건 실행한다.

## 시크릿

- 워크플로 파일에 비밀번호·API 키·토큰을 직접 쓰지 않는다. **항상 `${{ secrets.NAME }}`**.
- `docker-compose.yml` 의 값은 `${MYSQL_ROOT_PASSWORD}` 형태로 `.env` 에서 읽는다. `.env` 는 커밋하지 않는다.
- LLM·환율 API 키 같은 파일은 이미지에 `COPY` 하지 않고 `secrets/` 디렉터리를 읽기 전용으로 마운트한다. 이미지 레이어에 한 번 들어가면 영구히 남는다.
- 파일이 아니라 **디렉터리를 마운트**한다. 파일 경로를 지정했는데 호스트에 그 파일이 없으면 Docker가 같은 이름의 빈 디렉터리를 만들어 원인을 찾기 어려워진다.
- 새 시크릿이 필요하면 워크플로에 쓰기 전에 **어떤 이름으로 등록해야 하는지 먼저 알려준다.**

## Dockerfile

- 멀티스테이지 빌드를 유지한다 (빌드 스테이지 / 실행 스테이지). 최종 이미지에 소스코드와 빌드 도구가 남지 않게 한다.
- 의존성 설치 레이어를 소스 복사보다 먼저 둬서 캐시가 살아 있게 한다.
- 베이스 이미지 태그를 고정한다. `latest` 를 쓰지 않는다.

## nginx

- SPA 라우팅을 위해 `try_files $uri $uri/ /index.html;` 이 있어야 한다. 지우면 Vue 페이지 새로고침 시 404가 난다.
- `/api/` 프록시 경로를 바꾸면 프론트의 axios baseURL과 Vite 프록시 설정도 함께 확인한다.
- LLM 응답 대기가 길어질 수 있어 `proxy_read_timeout` 을 기본값(60초)보다 넉넉히 둔다.

## Git 훅 (lefthook)

- `scripts/validate-*.sh` 의 정규식을 바꾸면 `.github/workflows/ci.yml` 의 `convention` 잡, `.claude/commands/`, `README.md` 의 컨벤션 표를 **함께** 고친다. 네 곳이 같은 규칙을 따로 들고 있다.
- 훅 스크립트는 POSIX `sh` 로 작성한다. 팀원 환경이 Git Bash / WSL / macOS 로 갈린다.
- `--no-verify` 나 `LEFTHOOK=0` 를 상시로 쓰라고 안내하지 않는다.

## 변경 시

CI나 인프라 파일을 수정하면 PR 본문에 **무엇이 바뀌었고 팀원이 뭘 해야 하는지**를 적는다. 이 파일들은 세 명 모두의 환경에 영향을 준다.
