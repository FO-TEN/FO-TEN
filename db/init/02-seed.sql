-- FO:TEN 시드 데이터 (목데이터)
--
-- 규칙
--   1. 여러 번 실행돼도 깨지지 않아야 한다. INSERT IGNORE 또는 자연키 기준 upsert 를 쓴다.
--   2. AUTO_INCREMENT 값에 의존하는 시드를 만들지 않는다. 재적용하면 어긋난다.
--   3. 기획 범위상 수입/지출 데이터는 목데이터를 쓴다 (마이데이터 연동 없음).

SET NAMES utf8mb4;

-- 예)
-- INSERT IGNORE INTO member (login_id, ...) VALUES ('demo', ...);
