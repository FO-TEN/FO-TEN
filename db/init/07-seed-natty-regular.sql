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

-- ================================================================
-- 소비내역(EXPENSE) — "부족액을 채우기 위해 소비를 얼마나 줄일 수 있는지" 분석 흐름 검증용.
--
-- 로드맵 1개월차(cycle1, 3월)부터 오늘 기준 어제까지 7개월치(3~9월)를 채운다 — 3~8월은
-- 이미 끝난 달(각각 cycle1~6에 대응), 9월은 아직 진행 중인 달(cycle7, 어제까지만).
-- 카테고리는 6개(식비/교통/통신/쇼핑/주거/기타) 전부, 달마다 카테고리당 최소 5건씩
-- (=달 최소 30건) 넣는다 — 목표진단(GoalDiagnosisServiceImpl류)이 카테고리별 절감 여력을
-- 계산하는 화면에서 "거래가 너무 적어 표가 휑해 보이는" 문제를 피하기 위함이다.
--
-- 금액 설계: 식비/교통/쇼핑은 변동비(VARIABLE)라 달마다 총액이 다르고, 그중 쇼핑을
-- 편차가 가장 크게 잡았다(4.5만~25만) — 03-seed-transaction.sql의 nguyen01과 같은 이유로,
-- 쇼핑이 "절감 여력 1위"로 뽑히게 하기 위함이다. 특히 지난달(cycle6, 8월 — 실제로 30만원
-- 부족액이 난 달)의 쇼핑을 전체 중 최고액(25만원)으로 잡아, "왜 지난달에 부족했는지"와
-- "어디를 줄이면 되는지"가 한 이야기로 이어지게 했다. 주거/통신/기타는 고정비(FIXED)라
-- 매달 금액이 같고(합계 30만원 = financial_info.monthly_living_cost 와 일치시켰다),
-- 항목 하나하나는 "월세/관리비/전기세…"처럼 실제 있을 법한 소분류로 나눴다 — 이 스키마엔
-- 소분류 컬럼이 없어서 편법이지만, 달마다 카테고리당 5건을 채워야 하는 이 시드의 목적상
-- 고정비 하나를 5줄로 쪼개는 것 말고는 자연스러운 방법이 없었다.
--
-- 각 항목의 금액은 카테고리 월 총액 × 가중치(30/25/20/15/10%)로 계산한다(반올림은 -2,
-- 즉 100원 단위) — 21개(카테고리 3 × 월 5)의 총액만 손으로 정하면 나머지 75건의 금액은
-- 자동으로 나온다. 고정비는 총액이 항상 같아서 품목별 금액을 직접 고정값으로 뒀다(15건).
--
-- 날짜 처리는 구간이 셋으로 갈린다 — 하나의 CURDATE() 기준 산식으로 셋 다 커버할 수 없다:
--   (a) 1~5개월 전(cycle2~6, 이미 끝난 달): DATE_SUB(CURDATE(), INTERVAL mo MONTH)로 해당
--       월을 구하고, 그 달 1일부터 날짜를 센다(월말 근처로 밀리는 걸 막으려 항상 1일부터
--       계산 — 이 파일 위쪽 monthly_saving_plan 날짜 계산과 같은 이유).
--   (b) 6개월 전(cycle1, 첫 달 — 입국일부터 시작하는 partial month): sr.start_date 이후
--       며칠(1~9일)로 날짜를 잡는다. sr.start_date 의 일자가 크면(20일 이후) 드물게 다음 달로
--       넘어갈 수 있는데, 이 시드는 "정상적으로 매달 대화한 케이스"만 재현하는 게 목적이라
--       lan01/03과 같은 이유로 이 경계는 다루지 않는다.
--   (c) 이번 달(cycle7, 어제까지): "어제"가 그 달의 며칠째인지(@natty03_elapsed)를 구해
--       5건을 그 안에 비례 배분한다(CEIL(elapsed × i/5)) — 재적용 시점의 날짜가 몇일이든
--       마지막 건(5번째)은 항상 "어제"에 정확히 걸리게 한다.
--
-- natty03 은 SALARY/REMITTANCE 거래가 없어(적금 납입만 재현하는 게 이 계정의 핵심이라
-- 뺐다) 월말 잔액(findBalanceAsOf) 기반 계산의 입력값으로는 애초에 못 쓴다 — 이 소비내역은
-- 카테고리별 지출 분석(목표진단)에만 쓰는 걸 전제로 한다.
-- ================================================================
SET @natty03_yesterday := DATE_SUB(CURDATE(), INTERVAL 1 DAY);
SET @natty03_elapsed := GREATEST(1, DAY(@natty03_yesterday));

-- ------------------------------------------------------------
-- (a) 변동비 — cycle2~6 (1~5개월 전, 이미 끝난 달)
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL t.months_ago MONTH), '%Y-%m-01'), INTERVAL (d.day_of_month - 1) DAY),
         INTERVAL (8 + o.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', ROUND(t.total * o.weight, -2), 0, t.category, 'VARIABLE', o.item_name
FROM member m
JOIN (SELECT '쇼핑' AS category, 1 AS months_ago, 250000 AS total UNION ALL
      SELECT '쇼핑', 2,  55000 UNION ALL
      SELECT '쇼핑', 3, 170000 UNION ALL
      SELECT '쇼핑', 4,  60000 UNION ALL
      SELECT '쇼핑', 5, 190000 UNION ALL
      SELECT '식비', 1, 210000 UNION ALL
      SELECT '식비', 2, 190000 UNION ALL
      SELECT '식비', 3, 195000 UNION ALL
      SELECT '식비', 4, 185000 UNION ALL
      SELECT '식비', 5, 190000 UNION ALL
      SELECT '교통', 1,  95000 UNION ALL
      SELECT '교통', 2,  84000 UNION ALL
      SELECT '교통', 3,  88000 UNION ALL
      SELECT '교통', 4,  82000 UNION ALL
      SELECT '교통', 5,  85000) t ON TRUE
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
WHERE m.login_id = 'natty03';

-- ------------------------------------------------------------
-- (a) 고정비 — cycle2~6 (1~5개월 전), 금액은 매달 동일
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL mo.months_ago MONTH), '%Y-%m-01'), INTERVAL (d.day_of_month - 1) DAY),
         INTERVAL (8 + f.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) mo
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
WHERE m.login_id = 'natty03';

-- ------------------------------------------------------------
-- (b) 변동비/고정비 — cycle1 (6개월 전, 입국월 = partial month)
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(sr.start_date, INTERVAL d.day_offset DAY), INTERVAL (8 + o.item_order * 2) HOUR),
       'EXPENSE', 'OUT', ROUND(t.total * o.weight, -2), 0, t.category, 'VARIABLE', o.item_name
FROM member m
JOIN savings_roadmap sr ON sr.member_id = m.member_id
JOIN (SELECT '쇼핑' AS category, 45000 AS total UNION ALL
      SELECT '식비', 140000 UNION ALL
      SELECT '교통',  60000) t ON TRUE
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
WHERE m.login_id = 'natty03';

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
WHERE m.login_id = 'natty03';

-- ------------------------------------------------------------
-- (c) 변동비/고정비 — cycle7 (이번 달, 오늘 어제까지만)
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL (CEIL(@natty03_elapsed * o.item_order / 5) - 1) DAY),
         INTERVAL (8 + o.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', ROUND(t.total * o.weight, -2), 0, t.category, 'VARIABLE', o.item_name
FROM member m
JOIN (SELECT '쇼핑' AS category, 120000 AS total UNION ALL
      SELECT '식비', 60000 UNION ALL
      SELECT '교통', 25000) t ON TRUE
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
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(
         DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL (CEIL(@natty03_elapsed * f.item_order / 5) - 1) DAY),
         INTERVAL (8 + f.item_order * 2) HOUR
       ),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
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
WHERE m.login_id = 'natty03';

-- ------------------------------------------------------------
-- 급여(SALARY)/송금(REMITTANCE) — cycle1~6(이미 끝난 달)만. 매달 25일 급여·26일 송금,
-- 온보딩 때 넣은 재무조건(financial_info: 월급여 2,500,000 / 월송금액 626,604) 그대로
-- 한 달도 빠짐없이 들어왔다고 가정한다. 이번 달(cycle7)은 아직 25일이 지나지 않아서
-- (오늘이 9일) 넣지 않는다 — 급여가 미래 시점에 들어온 것처럼 보이면 안 되니까.
-- balance_after 는 09-fix-transaction-balance.sql 이 마지막에 다시 계산하므로 0으로 둔다.
-- ------------------------------------------------------------
INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-25'), INTERVAL mo.months_ago MONTH),
       'SALARY', 'IN', 2500000, 0, '급여'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) mo
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-26'), INTERVAL mo.months_ago MONTH),
       'REMITTANCE', 'OUT', 626604, 0, '본국 송금'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) mo
WHERE m.login_id = 'natty03';
