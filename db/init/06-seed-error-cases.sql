-- FO:TEN 에러 케이스 전용 시드 — be/fix/roadmap-exception-mapping (FO-TEN#95) 검증용
--
-- 조회 API들이 IllegalStateException 을 그대로 던져 500(톰캣 HTML)으로 새던 문제를 고치면서,
-- 두 가지 케이스를 실제 계정으로 재현해 확인해야 했다:
--   err01 = "로드맵이 없습니다" (404 ResourceNotFoundException 로 바뀌어야 하는 정상 미확정 상태)
--   err02 = "진행 중인 구간이 없습니다" (진짜 불변조건 위반 — ExceptionAdvice 의 catch-all 로
--           JSON 500 이 나가는지 확인하는 용도. 실제 서비스 흐름으로는 절대 나올 수 없는 상태라
--           일부러 만든다)
-- 기존 seed 계정(nguyen01/rai01/sok01/lan01/lan02/lan03)은 전부 이미 정상적으로 로드맵이
-- 있거나 없거나 한 상태라 이 두 케이스를 온디맨드로 재현할 수 없어서 전용 계정을 새로 판다.

SET NAMES utf8mb4;

-- ================================================================
-- err01 — savings_roadmap 자체가 없는 상태.
-- GET/POST 로드맵 조회 API(4-5/4-7/4-8/4-9, RoadmapCommandServiceImpl 의 4-4/4-6)를
-- 이 계정으로 호출하면 전부 "로드맵이 없습니다." → 404 여야 한다.
-- (GET /api/roadmap/status 는 예외로, roadmapExists=false 를 정상 200 으로 반환한다 — 그게
-- 원래 설계다. 이 계정은 그 "정상 200"과 "나머지 API들의 404"가 서로 다르다는 걸 보여준다.)
-- ================================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('err01', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'QA No Roadmap', 'VIETNAM', 'vi');

INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', DATE_SUB(CURDATE(), INTERVAL 1 MONTH), DATE_ADD(CURDATE(), INTERVAL 23 MONTH)
FROM member WHERE login_id = 'err01';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2400000, 850000, 350000, 1200000
FROM member WHERE login_id = 'err01';

-- 로드맵이 아예 없는 "막 온보딩한" 상태라 created_at=오늘(entry_date는 입국일일 뿐 목표를
-- 세운 시점과는 무관). remainingMonths=23-1=22 → target_amount_krw=500,000×22+1,200,000=12,200,000.
INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, target_amount_krw, monthly_required_saving, created_at)
SELECT member_id, 300000000, 'VND', 500000, 12200000, 500000, CURDATE()
FROM member WHERE login_id = 'err01';

-- savings_roadmap 은 절대 만들지 않는다 — 이게 이 계정의 핵심이다.

-- ================================================================
-- err02 — savings_roadmap 은 있지만 ACTIVE 구간이 하나도 없는 상태.
-- "로드맵이 있으면 ACTIVE 구간이 항상 정확히 1개 있어야 한다"는 불변조건(구간이 끝나면
-- 그 자리에서 COMPLETED 로 바꾸고 다음 구간을 바로 INSERT 하므로 갭이 생길 수 없다)을
-- 정상 흐름으로는 절대 못 만든다 — 그래서 구간을 아예 COMPLETED 로만 심어 직접 깬다.
-- GET /api/roadmap/status 부터 이 예외를 던지므로, 이 계정으로 아무 로드맵 API나 호출하면
-- 재현된다. 목적은 "진짜 서버 오류가 JSON 으로 나가는지" 확인이지, 정상 케이스 검증이 아니다.
-- ================================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('err02', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'QA No Active Segment', 'VIETNAM', 'vi');

INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', DATE_SUB(CURDATE(), INTERVAL 13 MONTH), DATE_ADD(CURDATE(), INTERVAL 11 MONTH)
FROM member WHERE login_id = 'err02';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2400000, 850000, 350000, 1200000
FROM member WHERE login_id = 'err02';

-- created_at = 로드맵 start_date(=오늘-13개월)와 동일 — 온보딩일 = 목표를 세운 시점.
-- remainingMonths=Period(오늘-13개월, 오늘+11개월)=24-1=23 → target_amount_krw=
-- 500,000×23+1,200,000=12,700,000.
INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, target_amount_krw, monthly_required_saving, created_at)
SELECT member_id, 300000000, 'VND', 500000, 12700000, 500000, DATE_SUB(CURDATE(), INTERVAL 13 MONTH)
FROM member WHERE login_id = 'err02';

-- 로드맵 계열은 자연키가 없어 err02 것만 지우고 다시 넣는다. 이 계정엔 product_subscription/
-- monthly_saving_plan/asset_snapshot/transaction_history 는 아예 안 만드므로 지울 것도 없다.
DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'err02';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'err02';

INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, DATE_SUB(CURDATE(), INTERVAL 13 MONTH), DATE_ADD(CURDATE(), INTERVAL 11 MONTH), 24
FROM member WHERE login_id = 'err02';

-- 구간을 status='COMPLETED' 로만 심는다 — 다음 구간이 없어 ACTIVE 가 하나도 없는 상태.
INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date, DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'COMPLETED'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'err02';
