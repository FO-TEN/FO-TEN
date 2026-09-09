-- FO:TEN "구간 전환 대기 + 지난달 부족액"(NEW_SEGMENT, hasShortfall) 대화 흐름 검증용 계정 — natty05
--
-- natty04(db/init/08-seed-natty-newsegment.sql)와 같은 시점(1구간 12개월이 어제 끝나고 2구간은
-- 아직 시작되지 않은 NEW_SEGMENT)을 재현하되, 마지막 회차(12)만 300,000원을 덜 낸다. 대화형
-- 로드맵 UI 흐름 v5 §3 "새 운용구간 시작 월"의 시연 흐름 —
--   구간 전환 안내 → 지난 구간 목돈 안내 → 지난달 저축 결과(부족) → 당월 저축 방식 선택 →
--   우대조건 체크 → 추천 조합 → 전체 로드맵 → 이번 구간 상세 —
-- 을 처음부터 끝까지 밟으려면 새 구간 월에 부족액이 함께 있어야 하는데, natty04 는 완납이라
-- 방식 선택 단계가 나오지 않고 natty03 은 일반 월이라 구간 전환이 나오지 않는다.
--
-- natty04 와 다른 점은 딱 두 곳이다.
--   asset_snapshot.monthly_payment   12회차 500,000 → 200,000
--   transaction_history(SAVINGS_PAYMENT) 12회차 500,000 → 200,000
-- 그래서 누적 저축실적 = 5,700,000(적금) + 7,107,444(현금성) = 12,807,444원이고, 12개월 목표
-- 13,107,444원에 300,000원이 모자라 hasShortfall=true, shortfallAmount=300,000 이 된다.
-- 지난 구간 목돈(rolloverAmount)은 적금 원금 5,700,000 + 세후 이자 + 현금성 7,107,444 이다.
--
-- 그 밖의 재무 조건·소비내역·급여/송금은 natty04 와 완전히 같다. 자연키가 없는 로드맵 계열
-- 테이블은 이 계정(natty05) 것만 지우고 다시 넣는다.

SET NAMES utf8mb4;

-- ============================================================
-- member / stay_info / financial_info / goal
-- ============================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('natty05', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'QA New Segment Shortfall', 'VIETNAM', 'vi');

-- 구간1 만기일 = 어제. 시작일은 거기서 12개월을 뺀 날 — lan01과 동일한 산식.
SET @natty05_start := DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 12 MONTH);

INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', @natty05_start, DATE_ADD(@natty05_start, INTERVAL 58 MONTH)
FROM member WHERE login_id = 'natty05';

-- natty02/natty03 과 동일한 재무 조건.
INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2500000, 300000, 626604, 400000
FROM member WHERE login_id = 'natty05';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 1200000000, 'VND', 1092287, 1092287
FROM member WHERE login_id = 'natty05';

-- natty02 실제 응답과 동일: 카드결제·해외송금만 TRUE.
INSERT IGNORE INTO member_rate_condition_response (member_id, condition_code, will_meet)
SELECT member_id, c.condition_code, c.will_meet
FROM member m
JOIN (SELECT 'SALARY_TRANSFER' AS condition_code, FALSE AS will_meet UNION ALL
      SELECT 'CARD_PAYMENT', TRUE UNION ALL
      SELECT 'OVERSEAS_REMITTANCE', TRUE UNION ALL
      SELECT 'AUTO_TRANSFER', FALSE UNION ALL
      SELECT 'STARBANKING_TRANSFER', FALSE UNION ALL
      SELECT 'SPECIAL_DAY', FALSE) c ON TRUE
WHERE m.login_id = 'natty05';

-- ============================================================
-- 로드맵 계열 (자연키 없음) — natty05 것만 지우고 다시 넣는다.
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'natty05';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'natty05';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

-- savings_roadmap — natty02 실제 로드맵과 같은 총 57개월(12+12+12+21 구간 구조).
INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, @natty05_start, DATE_ADD(@natty05_start, INTERVAL 57 MONTH), 57
FROM member WHERE login_id = 'natty05';

-- 구간1: 12개월, end_date = 어제(이미 만료) → 아직 status='ACTIVE'(전환 미처리 상태 재현).
INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date,
       DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'ACTIVE'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05';

-- product_subscription — 1개만(KB Global Star 적금, natty02 응답 기준 4.00%, 한도 500,000).
-- 아직 만기 처리 전이라 status='ACTIVE', maturity_amount=NULL.
INSERT INTO product_subscription
    (member_id, product_id, segment_id, subscription_role, term_months, start_date, maturity_date,
     expected_applied_rate, monthly_payment_limit_snapshot, status)
SELECT m.member_id, 3, rs.segment_id, 'NEW_SAVINGS', 12, rs.start_date, rs.end_date, 4.00, 500000, 'ACTIVE'
FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty05' AND rs.segment_no = 1;

-- monthly_saving_plan — cycle 1~12 계획은 전부 같다(실제 납입은 12회차만 미달). 목표기준액(1,092,287)이 상품 한도
-- (500,000)를 넘는 592,287원은 매달 recommended_cash_saving 으로 잡는다.
-- current_accumulated_fund/cumulative_saving_performance 는 "그 달 납입 전" 스냅샷 —
-- 현금성 저축까지 포함한 총 누적액이라 monthly_saving_amount(1,092,287) 기준으로 쌓는다.
INSERT INTO monthly_saving_plan
    (savings_roadmap_id, segment_id, plan_month, cycle_no, deficit_choice, monthly_saving_amount,
     recommended_cash_saving, current_accumulated_fund, cumulative_saving_performance,
     baseline_snapshot, required_snapshot)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       c.cycle_no, 'NONE', 1092287, 592287,
       1092287 * (c.cycle_no - 1), 1092287 * (c.cycle_no - 1), 1092287, 1092287
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty05';

-- monthly_saving_allocation — 매달 상품 한도 전액(500,000)만 배분(현금성 저축은 별도
-- 필드로만 관리되고 allocation 행이 없다 — product_subscription 을 요구하는 컬럼이라).
INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id, 500000, 1
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id
WHERE m.login_id = 'natty05';

-- asset_snapshot — monthly_payment 는 그 달 실제 상품 납입액. 12회차만 200,000(300,000 미납).
-- cash_saving_balance 는 현금성 저축 누적잔액(매달 592,287원씩 쌓임) — "여태 모은 돈"의
-- 핵심 근거 숫자다.
INSERT INTO asset_snapshot (savings_roadmap_id, segment_id, snapshot_month, monthly_payment, cash_saving_balance)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       CASE WHEN c.cycle_no = 12 THEN 200000 ELSE 500000 END, 592287 * c.cycle_no
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty05';

-- transaction_history(SAVINGS_PAYMENT) — 상품이 1개뿐이라 총 12건. 12회차만 200,000원.
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT', CASE WHEN c.cycle_no = 12 THEN 200000 ELSE 500000 END, 300000,
       ps.product_subscription_id, 'KB Global Star 적금 납입'
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty05';

-- ================================================================
-- 소비내역(EXPENSE) — 구간1 12개월 전체(1~12개월 전). 07-seed-natty-regular.sql 과 같은
-- 이유·같은 항목 구성(카테고리 6개 × 달마다 5건 = 30건/월)으로 채운다. 이 계정은 "오늘"에
-- 걸친 미완료 달이 없어(구간이 어제 끝났다) natty03의 (c)블록(부분월) 없이 (a)+(b)만 쓴다.
--   (a) 1~11개월 전(cycle2~12, 이미 끝난 달): 그 달 1일부터 날짜를 센다.
--   (b) 12개월 전(cycle1, 입국월 = partial month): sr.start_date 이후 며칠로 날짜를 잡는다
--       (같은 이유로 sr.start_date 일자가 큰 경우의 월 경계는 다루지 않는다 — lan01/natty03과 동일).
--
-- 쇼핑 편차를 가장 크게 잡아(04-seed-transaction.sql 의 nguyen01과 같은 이유) 절감 여력
-- 1위 후보가 되게 했다 — 이 계정은 부족액 시나리오가 아니라서 특정 달을 최고액으로
-- 몰아줄 필요는 없어, 12개월에 걸쳐 고르게 편차를 뒀다.
-- ================================================================

-- (a) 변동비 — cycle2~12 (1~11개월 전)
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL t.months_ago MONTH), '%Y-%m-01'), INTERVAL (d.day_of_month - 1) DAY),
         INTERVAL (8 + o.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', ROUND(t.total * o.weight, -2), 0, t.category, 'VARIABLE', o.item_name
FROM member m
JOIN (SELECT '쇼핑' AS category, 1  AS months_ago, 230000 AS total UNION ALL
      SELECT '쇼핑', 2,  60000 UNION ALL
      SELECT '쇼핑', 3, 210000 UNION ALL
      SELECT '쇼핑', 4,  50000 UNION ALL
      SELECT '쇼핑', 5, 190000 UNION ALL
      SELECT '쇼핑', 6,  70000 UNION ALL
      SELECT '쇼핑', 7, 180000 UNION ALL
      SELECT '쇼핑', 8,  45000 UNION ALL
      SELECT '쇼핑', 9, 160000 UNION ALL
      SELECT '쇼핑', 10, 55000 UNION ALL
      SELECT '쇼핑', 11, 200000 UNION ALL
      SELECT '식비', 1, 205000 UNION ALL
      SELECT '식비', 2, 190000 UNION ALL
      SELECT '식비', 3, 195000 UNION ALL
      SELECT '식비', 4, 185000 UNION ALL
      SELECT '식비', 5, 200000 UNION ALL
      SELECT '식비', 6, 190000 UNION ALL
      SELECT '식비', 7, 195000 UNION ALL
      SELECT '식비', 8, 180000 UNION ALL
      SELECT '식비', 9, 190000 UNION ALL
      SELECT '식비', 10, 185000 UNION ALL
      SELECT '식비', 11, 195000 UNION ALL
      SELECT '교통', 1,  92000 UNION ALL
      SELECT '교통', 2,  85000 UNION ALL
      SELECT '교통', 3,  88000 UNION ALL
      SELECT '교통', 4,  82000 UNION ALL
      SELECT '교통', 5,  90000 UNION ALL
      SELECT '교통', 6,  86000 UNION ALL
      SELECT '교통', 7,  89000 UNION ALL
      SELECT '교통', 8,  83000 UNION ALL
      SELECT '교통', 9,  87000 UNION ALL
      SELECT '교통', 10, 84000 UNION ALL
      SELECT '교통', 11, 91000) t ON TRUE
JOIN (SELECT '쇼핑' AS category, 1 AS item_order, 0.30 AS weight, '의류' AS item_name UNION ALL
      SELECT '쇼핑', 2, 0.25, '온라인쇼핑' UNION ALL
      SELECT '쇼핑', 3, 0.20, '생활용품' UNION ALL
      SELECT '쇼핑', 4, 0.15, '잡화' UNION ALL
      SELECT '쇼핑', 5, 0.10, '선물' UNION ALL
      SELECT '식비', 1, 0.30, '마트 장보기' UNION ALL
      SELECT '식비', 2, 0.25, '외식' UNION ALL
      SELECT '식비', 3, 0.20, '배달음식' UNION ALL
      SELECT '식비', 4, 0.15, '카페' UNION ALL
      SELECT '식비', 5, 0.10, '간식' UNION ALL
      SELECT '교통', 1, 0.30, '지하철' UNION ALL
      SELECT '교통', 2, 0.25, '버스' UNION ALL
      SELECT '교통', 3, 0.20, '택시' UNION ALL
      SELECT '교통', 4, 0.15, '시외버스' UNION ALL
      SELECT '교통', 5, 0.10, '공유자전거') o ON o.category = t.category
JOIN (SELECT 1 AS item_order, 3 AS day_of_month UNION ALL
      SELECT 2, 9 UNION ALL SELECT 3, 14 UNION ALL SELECT 4, 19 UNION ALL SELECT 5, 24) d ON d.item_order = o.item_order
WHERE m.login_id = 'natty05';

-- (a) 고정비 — cycle2~12 (1~11개월 전), 금액은 매달 동일(합계 30만원 = financial_info.monthly_living_cost).
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL mo.months_ago MONTH), '%Y-%m-01'), INTERVAL (d.day_of_month - 1) DAY),
         INTERVAL (8 + f.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11) mo
JOIN (SELECT '주거' AS category, 1 AS item_order, 100000 AS amount, '월세' AS item_name UNION ALL
      SELECT '주거', 2,  30000, '관리비' UNION ALL
      SELECT '주거', 3,  20000, '전기세' UNION ALL
      SELECT '주거', 4,  15000, '가스비' UNION ALL
      SELECT '주거', 5,  15000, '수도세' UNION ALL
      SELECT '통신', 1,  30000, '휴대폰 요금' UNION ALL
      SELECT '통신', 2,  15000, '인터넷 요금' UNION ALL
      SELECT '통신', 3,   5000, 'OTT 구독' UNION ALL
      SELECT '통신', 4,   5000, '데이터 충전' UNION ALL
      SELECT '통신', 5,   5000, '국제전화' UNION ALL
      SELECT '기타', 1,  15000, 'TV 수신료' UNION ALL
      SELECT '기타', 2,  15000, '보험료' UNION ALL
      SELECT '기타', 3,  10000, '정수기 렌탈료' UNION ALL
      SELECT '기타', 4,  10000, '회비' UNION ALL
      SELECT '기타', 5,  10000, '잡비') f ON TRUE
JOIN (SELECT 1 AS item_order, 2 AS day_of_month UNION ALL
      SELECT 2, 7 UNION ALL SELECT 3, 12 UNION ALL SELECT 4, 17 UNION ALL SELECT 5, 22) d ON d.item_order = f.item_order
WHERE m.login_id = 'natty05';

-- (b) 변동비 — cycle1 (12개월 전, 입국월 = partial month)
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL d.day_offset DAY), INTERVAL (8 + o.item_order * 2) HOUR),
       'EXPENSE', 'OUT', ROUND(t.total * o.weight, -2), 0, t.category, 'VARIABLE', o.item_name
FROM member m
JOIN savings_roadmap sr ON sr.member_id = m.member_id
JOIN (SELECT '쇼핑' AS category, 50000 AS total UNION ALL
      SELECT '식비', 145000 UNION ALL
      SELECT '교통',  62000) t ON TRUE
JOIN (SELECT '쇼핑' AS category, 1 AS item_order, 0.30 AS weight, '의류' AS item_name UNION ALL
      SELECT '쇼핑', 2, 0.25, '온라인쇼핑' UNION ALL
      SELECT '쇼핑', 3, 0.20, '생활용품' UNION ALL
      SELECT '쇼핑', 4, 0.15, '잡화' UNION ALL
      SELECT '쇼핑', 5, 0.10, '선물' UNION ALL
      SELECT '식비', 1, 0.30, '마트 장보기' UNION ALL
      SELECT '식비', 2, 0.25, '외식' UNION ALL
      SELECT '식비', 3, 0.20, '배달음식' UNION ALL
      SELECT '식비', 4, 0.15, '카페' UNION ALL
      SELECT '식비', 5, 0.10, '간식' UNION ALL
      SELECT '교통', 1, 0.30, '지하철' UNION ALL
      SELECT '교통', 2, 0.25, '버스' UNION ALL
      SELECT '교통', 3, 0.20, '택시' UNION ALL
      SELECT '교통', 4, 0.15, '시외버스' UNION ALL
      SELECT '교통', 5, 0.10, '공유자전거') o ON o.category = t.category
JOIN (SELECT 1 AS item_order, 1 AS day_offset UNION ALL
      SELECT 2, 3 UNION ALL SELECT 3, 5 UNION ALL SELECT 4, 7 UNION ALL SELECT 5, 9) d ON d.item_order = o.item_order
WHERE m.login_id = 'natty05';

-- (b) 고정비 — cycle1 (12개월 전, 입국월 = partial month)
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL d.day_offset DAY), INTERVAL (8 + f.item_order * 2) HOUR),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN savings_roadmap sr ON sr.member_id = m.member_id
JOIN (SELECT '주거' AS category, 1 AS item_order, 100000 AS amount, '월세' AS item_name UNION ALL
      SELECT '주거', 2,  30000, '관리비' UNION ALL
      SELECT '주거', 3,  20000, '전기세' UNION ALL
      SELECT '주거', 4,  15000, '가스비' UNION ALL
      SELECT '주거', 5,  15000, '수도세' UNION ALL
      SELECT '통신', 1,  30000, '휴대폰 요금' UNION ALL
      SELECT '통신', 2,  15000, '인터넷 요금' UNION ALL
      SELECT '통신', 3,   5000, 'OTT 구독' UNION ALL
      SELECT '통신', 4,   5000, '데이터 충전' UNION ALL
      SELECT '통신', 5,   5000, '국제전화' UNION ALL
      SELECT '기타', 1,  15000, 'TV 수신료' UNION ALL
      SELECT '기타', 2,  15000, '보험료' UNION ALL
      SELECT '기타', 3,  10000, '정수기 렌탈료' UNION ALL
      SELECT '기타', 4,  10000, '회비' UNION ALL
      SELECT '기타', 5,  10000, '잡비') f ON TRUE
JOIN (SELECT 1 AS item_order, 2 AS day_offset UNION ALL
      SELECT 2, 4 UNION ALL SELECT 3, 6 UNION ALL SELECT 4, 8 UNION ALL SELECT 5, 10) d ON d.item_order = f.item_order
WHERE m.login_id = 'natty05';

-- ------------------------------------------------------------
-- 급여(SALARY)/송금(REMITTANCE) — cycle1~12 전부(구간1이 어제 끝나 12개월 모두 이미
-- 지난 달이다). 매달 25일 급여·26일 송금, 온보딩 때 넣은 재무조건(월급여 2,500,000/
-- 월송금액 626,604) 그대로 한 달도 빠짐없이 들어왔다고 가정한다. balance_after 는
-- 09-fix-transaction-balance.sql 이 마지막에 다시 계산하므로 0으로 둔다.
-- ------------------------------------------------------------
INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-25'), INTERVAL mo.months_ago MONTH),
       'SALARY', 'IN', 2500000, 0, '급여'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE m.login_id = 'natty05';

INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-26'), INTERVAL mo.months_ago MONTH),
       'REMITTANCE', 'OUT', 626604, 0, '본국 송금'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE m.login_id = 'natty05';
