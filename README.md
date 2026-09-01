# FO:TEN (포텐)

> E-9 체류기간 4년(**FO**ur) 10개월(**TEN**)을 더 큰 포텐셜로 바꾼다

E-9 이주노동자의 금융 목표와 소비패턴을 분석해, 체류기간 내 목표 달성을 위한 맞춤형 저축 전략을 대화형으로 제안하는 AI 자산관리 서비스입니다.

2026 KB IT's Your Life 해커톤 · 세잎클로버

## 주요 기능

- **귀국 목표 달성 가능성 진단** — 목표 금액과 예상 귀국 시점으로 월별 필요 저축액을 산정하고 달성 여부를 판정
- **소비패턴 기반 추가 저축 여력 분석** — 고정비를 제외하고 조정 가능한 지출 항목 제시
- **지출 의사결정 지원** — 비정기 지출이 목표 달성률에 미치는 영향을 시나리오별로 시뮬레이션
- **체류기간 기반 예·적금 추천** — 예상 귀국일 이전 만기 상품만 선별해 중도해지 불이익 방지
- **두 통화 동시 표시** — 원화와 본국 통화를 함께 보여줘 환율 변동에 따른 실질 가치를 즉시 전달

## 프로젝트 구조

```text
FO-TEN/
├─ BE/                      # Spring Framework 5 (Legacy) · Gradle · WAR
│  ├─ src/main/java/        # controller · service · mapper · domain · dto
│  ├─ src/main/resources/
│  │  └─ mappers/           # MyBatis XML 매퍼
│  └─ Dockerfile            # 멀티스테이지 (JDK 17 빌드 → Tomcat 9 실행)
├─ FE/                      # Vue 3 · Vite
│  ├─ src/
│  │  ├─ api/               # axios 인스턴스와 도메인별 호출 함수
│  │  ├─ stores/            # Pinia 스토어
│  │  ├─ components/common/ # 공용 컴포넌트
│  │  ├─ features/          # 도메인 전용 컴포넌트 (goal · chat · product · exchange)
│  │  └─ pages/             # 라우트에 1:1 대응하는 화면
│  ├─ nginx/default.conf    # 정적 파일 서빙 + /api 프록시
│  └─ Dockerfile            # 멀티스테이지 (Node 20 빌드 → nginx 실행)
├─ db/init/                 # 스키마·시드 SQL — 팀 전체의 유일한 스키마 출처
├─ scripts/                 # 커밋 메시지·브랜치명 검증 스크립트
├─ docker-compose.yml       # MySQL + 백엔드 + 프론트
└─ lefthook.yml             # Git 훅 (컨벤션 자동 검증)
```

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Framework 5 (Legacy), MyBatis, Gradle, WAR, Tomcat 9 |
| Frontend | Vue 3, Vite, Pinia, Vue Router, axios |
| Database | MySQL 8.4 |
| 외부 연동 | 생성형 AI API (Function Calling), 한국수출입은행 환율 Open API |
| 인프라 | Docker Compose, nginx, GitHub Actions |

계산 엔진과 LLM 을 분리한 구조입니다. **LLM 은 금액·금리·상품조건을 직접 계산하거나 생성하지 않고**, Java 시뮬레이션 엔진의 반환값만 문장으로 옮깁니다.

## 시작하기

```bash
git clone https://github.com/FO-TEN/FO-TEN.git
cd FO-TEN
cp .env.example .env        # LLM_API_KEY, EXIM_API_KEY 를 채웁니다

npm install && npx lefthook install    # Git 훅 설치 (한 번만)

docker compose up -d        # http://localhost:5173
```

### Docker 를 쓰지 않는다면

MySQL 8.4 · JDK 17 · Node 20 · **Tomcat 9** 를 직접 설치합니다. (Tomcat 10 이상은 `javax` → `jakarta` 전환 때문에 Spring 5 앱이 뜨지 않습니다)

DB 는 컨테이너와 **같은 값**으로 맞춥니다. 값이 갈리면 "내 PC 에서만 되는" 문제가 생깁니다. 정확한 값은 [`.env.example`](.env.example) 에 있습니다.

```sql
CREATE DATABASE foten DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'foten'@'localhost' IDENTIFIED BY 'foten';
GRANT ALL PRIVILEGES ON foten.* TO 'foten'@'localhost';
FLUSH PRIVILEGES;
SET GLOBAL time_zone = '+09:00';   -- 안 맞추면 D-day 계산이 하루씩 밀립니다
```

```bash
mysql -u root -p foten < db/init/01-schema.sql
cd FE && npm install && npm run dev      # http://localhost:5173
```

백엔드는 IDE 에서 Tomcat 9 를 8080 포트로 띄우고, VM 옵션에 `-Duser.timezone=Asia/Seoul` 을 넣습니다.

> 세팅 상세와 문제 해결은 팀 채널에 공유된 세팅 문서를 보세요.

## Git Convention

`develop` / `main` 2-브랜치 전략을 씁니다. 기능 브랜치는 `develop` 에서 만듭니다.

### 이슈는 필요할 때만

**모든 작업에 이슈를 만들지 않습니다.** 바로 할 수 있는 일은 브랜치를 파고 PR 로 기록합니다. 아래에 해당할 때만 이슈를 만듭니다.

- **버그** — 재현 절차와 원인을 남겨야 하는 것
- **논의가 필요한 것** — 방식을 정해야 해서 다른 팀원 의견이 필요할 때
- **순서를 맞춰야 하는 것** — BE·FE 가 함께 움직여야 할 때
- **DB 스키마 변경** — 다른 두 명이 각자 재적용해야 하므로 미리 알려야 함
- **미뤄둘 것** — 지금 안 하지만 잊으면 안 되는 일

이슈가 없으므로 **PR 이 그 작업의 유일한 기록**입니다. PR 본문의 "무엇을 왜" 를 파일 변경 요약으로 때우지 마세요.

### 브랜치

```text
[개발 파트]/접두어/기능명(케밥 케이스)
```

```text
be/feat/goal-simulation         # 기본. 이슈 없이 바로 작업
fe/design/goal-card-spacing
chore/docker-compose            # 파트 구분이 없는 공통 작업 (설정 · CI · 빌드)
docs/setup-guide                # 저장소 전체 문서

fe/fix/34-exchange-rate-display # 이슈가 있을 때만 앞에 번호를 붙임
```

### 커밋 메시지 & PR 제목

커밋 메시지와 PR 제목은 같은 형식을 씁니다. 이슈가 있을 때만 커밋 본문에 `Refs: #34`, PR 본문에 `Closes #34` 를 적습니다.

PR 은 squash merge 하므로 **PR 제목이 그대로 `develop` 의 커밋 메시지가 됩니다.** CI 가 제목을 검증합니다.

```text
[개발 파트]접두어: 기능명
```

```text
[be]feat: 목표 달성률 시뮬레이션 API 추가
[fe]fix: 환율 표시 소수점 오류 수정
chore: Docker Compose 구성 추가
```

### 접두어

| 접두어 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 (코드 변경 없음) |
| `style` | 코드 포맷팅, 세미콜론 등 스타일 변경 (논리 변경 없음) |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `test` | 테스트 관련 코드 추가/수정 |
| `chore` | 빌드, 패키지 매니저 설정 등 기타 작업 |
| `design` | css 등 사용자 UI 디자인 변경 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 혹은 폴더명을 수정하거나 옮기는 작업만인 경우 |
| `remove` | 파일을 삭제하는 작업만 수행한 경우 |
| `hotfix` | 급하게 치명적인 버그를 고쳐야 하는 경우 |
| `revert` | 이전 변경을 되돌리는 경우 |

`style` 은 포맷팅·세미콜론처럼 **코드의 겉모양**, `design` 은 CSS 처럼 **사용자에게 보이는 화면**을 바꾼 경우입니다. 헷갈리면 "사용자가 눈으로 알아챌 수 있는가"로 가릅니다.

`git revert` 가 자동으로 만드는 `Revert "..."` 커밋은 형식 검증에서 제외됩니다. 되돌린 이유를 남기고 싶을 때 `[be]revert: ...` 형태로 직접 작성하세요.

### 작업 흐름

```text
1. git switch develop && git pull origin develop
2. git switch -c be/feat/goal-simulation        # 이슈 있으면 be/fix/34-...
3. 작업 → git commit                             # [be]feat: 목표 시뮬레이션 API 추가
4. git fetch origin develop && git merge origin/develop    # PR 전에 한 번 맞추기
5. git push -u origin be/feat/goal-simulation
6. PR 생성 (base: develop) → 리뷰 1명 승인 → squash merge
```

### 규칙

- 개발 파트는 `fe` 또는 `be` 를 씁니다. 파트를 생략할 수 있는 접두어는 **`chore` 와 `docs` 둘뿐**입니다 — 설정·CI·빌드는 `chore`, 저장소 전체 문서는 `docs`.
- 기능 PR 은 기능 브랜치에서 `develop` 으로 생성하고 **squash merge** 합니다.
- 최소 1명의 리뷰어 승인 후 병합합니다.
- `develop` 에서 `main` 으로 릴리스할 때는 **rebase merge** 합니다.
- 이미 push 한 브랜치는 **rebase 하지 않습니다.** `develop` 을 따라잡을 때는 merge 를 씁니다 (리뷰 코멘트 위치가 어긋나는 것을 막기 위함).
- **DB 스키마를 바꾸면 `db/init/01-schema.sql` 을 함께 수정**하고, PR 본문에 팀원 재적용 방법을 적습니다.

### 자동 검증

`lefthook` 이 커밋·푸시 시점에 검사하고, GitHub Actions 가 PR 에서 한 번 더 확인합니다.

| 시점 | 검사 |
| --- | --- |
| `commit-msg` (로컬) | 커밋 메시지 형식 · 자동 생성 서명 포함 여부 |
| `pre-push` (로컬) | 브랜치명 형식 |
| `pre-commit` (로컬) | 프론트엔드 prettier · eslint 자동 수정 |
| `convention` (CI) | 브랜치명 · PR 제목 · 브랜치의 모든 커밋 메시지 |
| `backend` `frontend` (CI) | Gradle · npm 빌드 |
| `schema-guard` (CI) | `db/init/*.sql` 이 바뀌면 PR 에 재적용 안내 자동 코멘트 |
| `labeler` (CI) | 변경 경로로 `BE` `FE` `db` `infra` `docs` 라벨 자동 부착 |

커밋 작성자 검사는 로컬 `pre-push` 와 CI 양쪽에서 돕니다. GitHub 은 커밋의 author · committer · `Co-authored-by:` 트레일러를 모두 읽어 기여자를 집계하므로 세 지점을 함께 봅니다. 확인은 `git shortlog -sne --all` 로 합니다.

훅이 막으면 `--no-verify` 로 넘기지 말고 이름을 고칩니다.

```bash
git branch -m be/feat/12-goal-simulation    # 브랜치명 수정
git commit --amend                          # 직전 커밋 메시지 수정
```

## 팀

| 팀원 | 담당 영역 |
| --- | --- |
| 이수민 | 시뮬레이션 엔진 · 기획 |
| 강민주 | LLM 연동 · 환율 |
| 강현지 | 상품 추천 · 화면 |
