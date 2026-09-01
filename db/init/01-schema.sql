-- FO:TEN 스키마 — 팀 전체의 유일한 스키마 출처
--
-- 규칙
--   1. 테이블·컬럼을 바꾸면 반드시 이 파일을 함께 수정한다.
--      Workbench 나 콘솔에서 손으로 ALTER TABLE 하고 끝내면 그 순간 팀원 환경과 어긋난다.
--   2. 스키마 변경이 포함된 PR 은 본문의 "변경된 DB 스키마" 항목에 재적용 방법을 적는다.
--        Docker      : docker compose down -v && docker compose up -d
--        로컬 MySQL  : mysql -u root -p foten < db/init/01-schema.sql
--   3. 컬럼명은 snake_case, MySQL 예약어를 피한다 (order → sort_order, rank → ranking).
--   4. 금액은 DECIMAL 을 쓴다. DOUBLE 은 오차가 누적돼 목표 달성률 계산이 틀어진다.
--   5. 환율은 예측하지 않고 고시값을 그대로 저장한다.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- 여기부터 테이블을 정의한다.
-- 예)
-- CREATE TABLE member (
--     member_id     BIGINT       NOT NULL AUTO_INCREMENT,
--     login_id      VARCHAR(50)  NOT NULL,
--     ...
--     created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     PRIMARY KEY (member_id),
--     UNIQUE KEY uk_member_login_id (login_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
