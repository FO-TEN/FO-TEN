# db

`db/init/*.sql` 이 FO:TEN 스키마의 **유일한 출처**입니다. 팀원마다 DB 를 띄우는 방식이 다르기 때문에(Docker 컨테이너 / 로컬 MySQL / 원격 공용 DB), 이 파일이 어긋나면 "내 PC 에서만 되는" 문제가 바로 생깁니다.

| 파일 | 내용 |
| --- | --- |
| `init/01-schema.sql` | 테이블·인덱스 정의 |
| `init/02-seed.sql` | 목데이터. 여러 번 실행해도 안전해야 함 |

## 적용 방법

**Docker 경로** — MySQL 컨테이너가 최초 기동할 때 `docker-entrypoint-initdb.d` 로 자동 실행됩니다.

이 마운트는 **데이터 디렉터리가 완전히 빈 최초 기동에만** 동작합니다. 스키마를 바꾼 뒤 그냥 재시작하면 반영되지 않으므로 볼륨을 지워야 합니다.

```bash
docker compose down -v && docker compose up -d
```

**로컬 MySQL 경로** — 직접 실행합니다.

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS foten DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql -u root -p foten < db/init/01-schema.sql
mysql -u root -p foten < db/init/02-seed.sql
```

## 스키마를 바꿀 때

1. `init/01-schema.sql` 을 수정합니다. **콘솔에서 손으로 `ALTER TABLE` 만 치고 끝내지 않습니다.**
2. 같은 PR 에 SQL 변경을 포함시킵니다.
3. PR 본문의 "변경된 DB 스키마" 항목에 팀원이 재적용할 명령을 적습니다.
4. 머지 후 팀 채널에 알립니다 — 다른 사람이 pull 해도 DB 는 자동으로 안 바뀝니다.
