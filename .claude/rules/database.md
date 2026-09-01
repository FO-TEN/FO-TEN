---
paths:
  - "BE/**/*.xml"
  - "db/**/*.sql"
---

# DB / MyBatis 규칙

## 스키마 변경 — 가장 중요

팀원마다 DB 띄우는 방식이 다릅니다 (Docker 컨테이너 / 로컬 MySQL / 원격 공용 DB). **`db/init/01-schema.sql` 이 유일한 스키마 출처입니다.**

- 스키마를 바꾸면 **반드시 `db/init/01-schema.sql` 을 함께 수정**한다.
- Workbench나 콘솔에서 손으로 `ALTER TABLE` 하고 끝내지 않는다. 그 순간 팀원 환경과 어긋난다.
- 스키마 변경이 포함된 작업이면 PR 본문의 "변경된 DB 스키마" 항목에 재적용 방법을 적는다.
  - Docker: `docker compose down -v && docker compose up -d`
  - 로컬 MySQL: `mysql -u root -p foten < db/init/01-schema.sql`
- Docker 의 `docker-entrypoint-initdb.d` 마운트는 **데이터 디렉터리가 빈 최초 기동에만** 실행된다. 그냥 재시작하면 반영되지 않으므로 볼륨을 지워야 한다.

## 금액 컬럼 — `DECIMAL`

```sql
target_amount   DECIMAL(15, 2)  NOT NULL,   -- O
monthly_income  DOUBLE          NOT NULL,   -- X — 누적 계산에서 오차가 쌓인다
```

목표 달성률은 월 순저축을 남은 개월 수만큼 누적하므로 부동소수점 오차가 그대로 결과에 남습니다. 환율은 소수 자릿수가 더 필요하므로 `DECIMAL(15, 4)` 정도를 씁니다.

통화 코드는 금액과 **항상 같이** 저장합니다 (`amount` + `currency_code CHAR(3)`).

## SQL 인젝션 — `${}` 금지

```xml
<!-- O -->
WHERE goal_id = #{goalId}

<!-- X — 문자열이 그대로 SQL에 박힘 -->
WHERE goal_id = ${goalId}
```

`${}` 는 컬럼명·테이블명·정렬 방향처럼 바인딩이 불가능한 자리에만 쓰고, 그 경우에도 **화이트리스트로 검증한 값만** 넘긴다.

## 네이밍

- 테이블·컬럼: `snake_case` (`created_at`, `target_amount`)
- Java 필드: `camelCase` (`createdAt`, `targetAmount`)
- 매핑은 `resultMap` 또는 `map-underscore-to-camel-case` 설정으로. XML에서 `AS` 별칭을 남발하지 않는다.
- **MySQL 예약어를 컬럼명으로 쓰지 않는다.** `order` → `sort_order`, `rank` → `ranking`, `desc` → `description`, `interval` → `period_months`.

## Mapper XML

- `namespace` 는 Mapper 인터페이스의 FQN과 정확히 일치해야 한다.
- 파라미터가 2개 이상이면 인터페이스에 `@Param` 을 붙인다.
- SQL 키워드는 대문자, 식별자는 소문자.
- 1:N 조인은 `resultMap` + `<collection>` 으로 매핑한다. 애플리케이션에서 루프 돌며 조합하지 않는다.
- XML이므로 `>` `<` 는 `&gt;` `&lt;` 로 쓰거나 `<![CDATA[ ]]>` 로 감싼다.

## 동적 SQL

`<where>`, `<set>`, `<if>`, `<foreach>` 를 쓴다. 문자열 이어붙이기로 조건을 만들지 않는다.

## 시드 데이터

- `db/init/02-seed.sql` 은 여러 번 실행돼도 깨지지 않아야 한다. `INSERT IGNORE` 또는 자연키 기준 upsert를 쓴다.
- AUTO_INCREMENT 값에 의존하는 시드를 만들지 않는다. 재적용하면 어긋난다.
- 수입/지출은 기획 범위상 목데이터다. 실제 마이데이터 연동을 전제한 컬럼을 미리 만들지 않는다.

## 시간대

- 컨테이너 MySQL은 `--default-time-zone=+09:00` 으로 띄운다. 로컬 MySQL 사용자도 서버 시간대를 `Asia/Seoul` 로 맞춘다.
- JDBC URL의 `serverTimezone` 은 드라이버 해석만 바꿀 뿐 서버의 `NOW()` 는 바꾸지 않는다. 둘 다 맞춰야 한다.
- 어긋나면 귀국 D-day 계산과 환율 배치 시각이 하루씩 밀린다.

## 인덱스

- 자주 조회하는 컬럼과 조인 키에 인덱스를 만든다.
- 인덱스도 `db/init/01-schema.sql` 에 함께 정의한다.
