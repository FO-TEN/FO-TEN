-- FO:TEN "일반 월"(REGULAR_MONTH) 대화 흐름 검증용 계정 — natty03
--
-- 참고: FOTEN_대화형_로드맵_UI_흐름_정리_v5.pdf (10-11페이지, 일반 월 시나리오).
-- 05-seed-roadmap.sql 의 lan03(REGULAR_MONTH, 부족액 있음)과 구조는 같지만, 조건은
-- 라이브 테스트 계정 natty02(로그인 natty02, member_id는 환경마다 다름)가 실제로 온보딩
-- 절차를 거쳐 만든 값을 그대로 복제했다 — natty02 는 db/init/ 시드에 없는 수동 생성
-- 계정이라 docker compose down -v 로 볼륨을 초기화하면 사라진다. "같은 조건에서 이미
-- 6개월치 이력이 쌓인 상태"를 재현하려면 별도의 커밋된 시드가 필요해서 이 파일을 만든다.
--
-- natty03 시나리오 — "일반 월, 지난달 부족액 있음" (flowType=REGULAR_MONTH)
--   로드맵 시작을 오늘 기준 6개월 전으로 잡아 calculateCycleNo() 가 오늘 cycle_no=7을
--   반환하게 한다 (YearMonth 차이 6 → 경과월 6 + 1 = 7). 구간1은 12개월이라 end_date가
--   아직 6개월 뒤(미래) — pendingSegmentTransition=false, REGULAR_MONTH 유지.
--   (lan03과 동일한 이유로 "몇 달치가 밀렸는지"의 경계 계산은 여기서 다루지 않는다 —
--   매달 빠짐없이 대화한 정상 케이스만 재현한다.)
--
-- financial_info/goal/rate_condition_response 는 natty02 계정의 실제 값을 그대로 옮겼다:
--   월급여 2,500,000 / 월생활비 300,000 / 월송금액 626,604 / 현재저축액 400,000
--   목표금액 1,200,000,000 VND, 목표기준액=필요저축액 1,092,287
--   우대조건 응답: 카드결제·해외송금만 충족(TRUE), 나머지 4개는 미충족(FALSE)
--     → product_id=3(KB Global Star 적금, 12개월) 예상적용금리 2.00+1.00+1.00=4.00%
--       (급여이체 우대는 못 받음), product_id=5(KB내맘대로적금, 12개월) 예상적용금리
--       2.50+0.10(카드결제)=2.60% — 04-seed-product.sql 의 product_preferential_rate 로
--       직접 검산한 값이며, 실제 라이브 계정 natty02 의 product_subscription 값과도 일치한다.
--   월 배분: 목표기준액 1,092,287원을 금리 내림차순으로 채우면 product3(한도 500,000)
--     전액 + product5(한도 3,000,000, 여유 충분)에 나머지 592,287원 → 현금성 저축 0원.
--
-- 손계산 (오늘 CURDATE() 기준):
--   cycle 1~5는 계획대로 완납(1,092,287원). cycle 6(지난달)만 792,287원만 실제 납입
--   (product3는 한도까지 500,000 그대로, product5만 300,000 덜 냄: 592,287→292,287) —
--   부족액(shortfallAmount) = 1,092,287 - 792,287 = 300,000원.
--   cycle 7(이번 달) monthly_saving_plan 은 아직 없다 — 이번 달 저축 방식 선택
--   (POST /api/roadmap/monthly-plan/deficit-choice) 이 실제로 만들어야 할 행이다.
--
-- 날짜는 전부 CURDATE() 기준 상대값이라 언제 재적용해도 "오늘이 cycle 7" 상태 그대로
-- 재현된다. 자연키가 없는 로드맵 계열 테이블은 이 계정(natty03) 것만 지우고 다시 넣는다
-- (다른 계정에 영향 없음), 나머지는 02-seed.sql 관례대로 INSERT IGNORE.

SET NAMES utf8mb4;

-- ============================================================
-- member / stay_info / financial_info / goal
-- ============================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('natty03', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'QA Regular Month', 'VIETNAM', 'vi');

-- 오늘 기준 6개월 전 시작 → YearMonth 차이가 6이라 cycleNo=7(경과월+1). 구간1은 12개월이라
-- end_date가 아직 6개월 뒤(미래) — pendingSegmentTransition=false, flowType=REGULAR_MONTH.
SET @natty03_start := DATE_SUB(CURDATE(), INTERVAL 6 MONTH);

-- entry_date = 로드맵 start_date 와 동일(온보딩일 = 저축 운용 시작일).
-- expected_return_date = 로드맵 end_date + 1개월(스키마 주석 규칙 그대로).
INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', @natty03_start, DATE_ADD(@natty03_start, INTERVAL 58 MONTH)
FROM member WHERE login_id = 'natty03';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2500000, 300000, 626604, 400000
FROM member WHERE login_id = 'natty03';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 1200000000, 'VND', 1092287, 1092287
FROM member WHERE login_id = 'natty03';

-- natty02 실제 응답 그대로: 카드결제·해외송금만 TRUE.
INSERT IGNORE INTO member_rate_condition_response (member_id, condition_code, will_meet)
SELECT member_id, c.condition_code, c.will_meet
FROM member m
JOIN (SELECT 'SALARY_TRANSFER' AS condition_code, FALSE AS will_meet UNION ALL
      SELECT 'CARD_PAYMENT', TRUE UNION ALL
      SELECT 'OVERSEAS_REMITTANCE', TRUE UNION ALL
      SELECT 'AUTO_TRANSFER', FALSE UNION ALL
      SELECT 'STARBANKING_TRANSFER', FALSE UNION ALL
      SELECT 'SPECIAL_DAY', FALSE) c ON TRUE
WHERE m.login_id = 'natty03';

-- ============================================================
-- 로드맵 계열 (자연키 없음) — natty03 것만 지우고 다시 넣는다.
-- FK 자식부터: transaction_history → monthly_saving_allocation → monthly_saving_plan
--             → product_subscription → asset_snapshot → roadmap_segment → savings_roadmap
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'natty03';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'natty03';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

-- ------------------------------------------------------------
-- savings_roadmap — start_date = @natty03_start, total_months=57(natty02 실제 로드맵과 동일)
-- ------------------------------------------------------------
INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, @natty03_start, DATE_ADD(@natty03_start, INTERVAL 57 MONTH), 57
FROM member WHERE login_id = 'natty03';

-- 구간1: 12개월, 아직 안 끝났다(end_date가 6개월 뒤 미래) — status='ACTIVE'.
INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date,
       DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'ACTIVE'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

-- product_subscription — natty02 실제 온보딩 결과와 동일한 2개 상품.
-- product3(한도 500,000, 4.00%) + product5(한도 3,000,000, 2.60%).
INSERT INTO product_subscription
    (member_id, product_id, segment_id, subscription_role, term_months, start_date, maturity_date,
     expected_applied_rate, monthly_payment_limit_snapshot, status)
SELECT m.member_id, 3, rs.segment_id, 'NEW_SAVINGS', 12, rs.start_date, rs.end_date, 4.00, 500000, 'ACTIVE'
FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03' AND rs.segment_no = 1;

INSERT INTO product_subscription
    (member_id, product_id, segment_id, subscription_role, term_months, start_date, maturity_date,
     expected_applied_rate, monthly_payment_limit_snapshot, status)
SELECT m.member_id, 5, rs.segment_id, 'NEW_SAVINGS', 12, rs.start_date, rs.end_date, 2.60, 3000000, 'ACTIVE'
FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03' AND rs.segment_no = 1;

-- monthly_saving_plan — cycle 1~6만 커밋(계획대로 1,092,287). cycle 7(이번 달)은 아직 없다 —
-- 이게 POST /api/roadmap/monthly-plan/deficit-choice가 실제로 만들어야 할 행이다.
INSERT INTO monthly_saving_plan
    (savings_roadmap_id, segment_id, plan_month, cycle_no, deficit_choice, monthly_saving_amount,
     recommended_cash_saving, current_accumulated_fund, cumulative_saving_performance,
     baseline_snapshot, required_snapshot)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       c.cycle_no, 'NONE', 1092287, 0,
       1092287 * (c.cycle_no - 1), 1092287 * (c.cycle_no - 1), 1092287, 1092287
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'natty03';

-- monthly_saving_allocation — "계획"이라 매달 500,000(product3) + 592,287(product5)로 동일.
-- 실제로 덜 낸 건 transaction_history(아래)만 반영한다.
INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id, 500000, 1
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id AND ps.product_id = 3
WHERE m.login_id = 'natty03';

INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id, 592287, 2
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id AND ps.product_id = 5
WHERE m.login_id = 'natty03';

-- asset_snapshot — 실제 마감 스냅샷. 1~5회차는 1,092,287 완납, 6회차(지난달)만 792,287
-- (300,000 부족)만 반영.
INSERT INTO asset_snapshot (savings_roadmap_id, segment_id, snapshot_month, monthly_payment, cash_saving_balance)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       CASE WHEN c.cycle_no = 6 THEN 792287 ELSE 1092287 END, 0
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'natty03';

-- transaction_history — 1~5회차는 product3 500,000 + product5 592,287 완납.
-- 6회차(지난달)는 product3 500,000(한도까지 그대로) + product5 292,287(300,000 덜 냄).
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT', 500000, 300000, ps.product_subscription_id, 'KB Global Star 적금 납입'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id AND ps.product_id = 3
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT',
       CASE WHEN c.cycle_no = 6 THEN 292287 ELSE 592287 END,
       300000, ps.product_subscription_id, 'KB내맘대로적금(자유적립식) 납입'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id AND ps.product_id = 5
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'natty03';
